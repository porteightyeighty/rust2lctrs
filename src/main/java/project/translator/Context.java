package project.translator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import project.lctrs.Rule;
import project.lctrs.Sort;
import project.lctrs.Symbol;
import project.lctrs.Term;
import project.lctrs.TermSymbol;
import project.lctrs.VarDecl;

/**
 * Mutable per-function translation state. Holds the program-point counter, the live variable scope,
 * the accumulated terms signature and rewrite rules, and the return sort shared by the whole
 * program-point family of the function being translated.
 *
 * <p>One instance is constructed per {@link project.ast.FunctionDeclaration} and threaded through
 * the {@link Translator} as statements are lowered.
 *
 * <p>The program-point counter resets per instance, so the minted symbols ({@code u1}, {@code u2},
 * …) are only unique within one function. Translating multiple functions would clash these symbols
 * across the shared signature; a unique per-function prefix (or a counter shared across contexts)
 * would be needed first. Multiple functions are a stretch goal, so this is left until then.
 *
 * @see Translator
 */
final class Context {

  private static final Logger LOG = LoggerFactory.getLogger(Context.class);

  private int counter = 0;
  private List<VarDecl> scope = new ArrayList<>();
  private final List<Symbol> sigma = new ArrayList<>();
  private final List<Rule> rules = new ArrayList<>();
  private final Sort returnSort;
  private final Deque<Integer> scopeMarks = new ArrayDeque<>();
  private final Deque<LoopContext> loopContexts = new ArrayDeque<>();

  /**
   * Creates a context for a single function.
   *
   * @param returnSort the sort returned by every program-point function symbol of this function
   */
  Context(Sort returnSort) {
    this.returnSort = returnSort;
  }

  /**
   * Mints the next program-point function symbol over the current scope, records it in the terms
   * signature, and returns it.
   *
   * @return the freshly created program-point function symbol
   */
  Symbol advance() {
    counter++;
    Symbol s = symbolFor(counter);
    register(s);
    return s;
  }

  /**
   * Records a symbol in the terms signature. Used for symbols not minted by {@link #advance()},
   * such as the function's hand-rolled entry program-point symbol, so the signature stays complete.
   *
   * @param s the term symbol to add to the signature
   */
  void register(Symbol s) {
    sigma.add(s);
  }

  /**
   * Returns an immutable snapshot of the terms signature accumulated so far, in mint order (the
   * entry symbol first, then {@code u1}, {@code u2}, …).
   *
   * @return the accumulated term symbols
   */
  List<Symbol> sigma() {
    return List.copyOf(sigma);
  }

  /**
   * Returns the current scope as configuration arguments, one term per variable in binding order.
   *
   * @return the scope variables as configuration arguments
   */
  List<Term> argsFromScope() {
    return List.<Term>copyOf(scope);
  }

  /**
   * Returns the current scope as configuration arguments, with the innermost slot bound to {@code
   * name} replaced by {@code value}. Used to inline a reassigned variable's new value on a rule's
   * right-hand side while leaving the other slots as their scope variables.
   *
   * <p>TODO: shadowing is not yet handled. Two bindings of the same name and sort are equal {@link
   * VarDecl} records, i.e. one variable in the LCTRS, so {@code let x ...; let x ...} currently
   * collapses two distinct program variables. Rejecting shadowing at the gatekeeper (or minting
   * unique internal names per binding) is deferred — see {@code
   * StatementBuilder#buildLetStatement}.
   *
   * @param name the name of the variable whose slot to replace
   * @param value the term to place in that slot
   * @return the scope arguments with the innermost named slot replaced
   */
  List<Term> argsWithValue(String name, Term value) {
    List<Term> args = new ArrayList<>(scope);
    for (int i = scope.size() - 1; i >= 0; i--) {
      if (scope.get(i).name().equals(name)) {
        args.set(i, value);
        return args;
      }
    }
    throw new IllegalStateException("Unbound variable in scope: " + name);
  }

  /**
   * Pushes a variable declaration onto the innermost end of the scope.
   *
   * @param var the variable declaration to bring into scope
   */
  void addToScope(VarDecl var) {
    scope.add(var);
  }

  /**
   * Truncates the scope back to the given size, dropping the variables bound since. Used when
   * leaving a block whose local bindings fall out of scope.
   *
   * @param newSize the number of variables to retain, counting from the outermost
   */
  private void shrinkScope(int newSize) {
    scope = new ArrayList<>(scope.subList(0, newSize));
  }

  /**
   * Records a constrained rewrite rule produced during translation.
   *
   * @param r the rule to accumulate
   */
  void addRule(Rule r) {
    LOG.debug("Adding rule {}", r);
    rules.add(r);
  }

  /**
   * Returns an immutable snapshot of the rules accumulated so far.
   *
   * @return the accumulated rewrite rules
   */
  List<Rule> rules() {
    return List.copyOf(rules);
  }

  /**
   * Resolves a variable name to its declaration, searching innermost-first so that the nearest
   * enclosing binding wins.
   *
   * @param varName the variable name to look up
   * @return the matching variable declaration
   * @throws IllegalStateException if no variable of that name is in scope
   */
  VarDecl resolve(String varName) {
    for (int i = scope.size() - 1; i >= 0; i--) {
      VarDecl varDecl = scope.get(i);
      if (varDecl.name().equals(varName)) {
        return varDecl;
      }
    }
    throw new IllegalStateException("Unbound variable in scope: " + varName);
  }

  /**
   * Opens a lexical scope, recording the current scope size so the bindings made inside can be
   * dropped on the matching {@link #leaveScope()}.
   */
  void enterScope() {
    scopeMarks.addFirst(scope.size());
  }

  /**
   * Closes the innermost lexical scope, truncating the scope back to where it was on the matching
   * {@link #enterScope()} and dropping any bindings made inside.
   *
   * @throws IllegalStateException if no scope is currently open
   */
  void leaveScope() {
    Integer mark = scopeMarks.pollFirst();
    if (mark == null) {
      throw new IllegalStateException("leaveScope() called with no open scope");
    }
    shrinkScope(mark);
  }

  void enterLoop(Term continueTarget) {
    LoopContext loopContext = new LoopContext(continueTarget);
    this.loopContexts.addFirst(loopContext);
  }

  LoopContext leaveLoop() {
    LoopContext loop = loopContexts.pollFirst();
    if (loop != null) {
      return loop;
    }
    throw new IllegalStateException("leaveLoop() called with no open loop");
  }

  void addBreakPoint(Term breakTarget) {
    LoopContext current = loopContexts.peekFirst();
    if (current == null) {
      throw new IllegalStateException("addBreakPoint() called with no open loop");
    }
    current.addBreakPoint(breakTarget);
  }

  /**
   * Builds a program-point function symbol {@code u<counter>} whose argument sorts are the sorts of
   * the current scope variables and whose result sort is the function's shared return sort.
   *
   * @param counter the program-point number to name the symbol after
   * @return the program-point function symbol for the current scope
   */
  private Symbol symbolFor(int counter) {
    String notation = "u" + String.valueOf(counter);
    List<Sort> argSorts = scope.stream().map((v) -> v.sort()).toList();
    return new TermSymbol(notation, argSorts, returnSort);
  }
}
