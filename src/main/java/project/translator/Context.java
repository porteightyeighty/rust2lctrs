package project.translator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import project.ast.Identifier;
import project.ast.Type;
import project.lctrs.Rule;
import project.lctrs.ScopedVar;
import project.lctrs.Serialiser;
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
  private List<ScopedVar> scope = new ArrayList<>();
  private final List<Symbol> sigma = new ArrayList<>();
  private final List<Rule> rules = new ArrayList<>();
  private final Sort returnSort;
  private final Type returnType;
  private Symbol ret;
  private Symbol err;
  private final Deque<Integer> scopeMarks = new ArrayDeque<>();
  private final Deque<LoopContext> loopContexts = new ArrayDeque<>();

  /**
   * Creates a context for a single function.
   *
   * @param returnSort the sort returned by every program-point function symbol of this function
   */
  Context(Sort returnSort, Type returnType) {
    this.returnSort = returnSort;
    this.returnType = returnType;
  }

  Type returnType() {
    return returnType;
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
   * Records the function's two primary result symbols — {@code ret}, which wraps a returned value
   * into the {@code result} sort, and the nullary {@code err} error sink — and registers both in
   * the signature. Following Fuhs, Kop &amp; Nishida (2017), §8.1, every accepted function has
   * exactly this pair, so they are per-function fixtures held alongside {@link #returnSort}.
   *
   * @param ret the {@code ret :: <returnSort> -> result} symbol
   * @param err the nullary {@code err :: result} symbol
   */
  void setResultSymbols(Symbol ret, Symbol err) {
    this.ret = ret;
    this.err = err;
    register(ret);
    register(err);
  }

  /**
   * Returns the function's {@code ret} symbol, which wraps a returned value into the {@code result}
   * sort.
   *
   * @return the {@code ret} symbol
   */
  Symbol ret() {
    return ret;
  }

  /**
   * Returns the function's nullary {@code err} error sink.
   *
   * @return the {@code err} symbol
   */
  Symbol err() {
    return err;
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
    return scope.stream().<Term>map(ScopedVar::varDecl).toList();
  }

  /**
   * Returns the current scope as configuration arguments, with the innermost slot whose source
   * identifier is {@code name} replaced by {@code value}. Used to inline a reassigned variable's
   * new value on a rule's right-hand side while leaving the other slots as their scope variables.
   * Searching innermost-first means a reassignment hits the nearest shadowing binding, matching
   * Rust.
   *
   * @param name the source identifier of the variable whose slot to replace
   * @param value the term to place in that slot
   * @return the scope arguments with the innermost matching slot replaced
   */
  List<Term> argsWithValue(Identifier name, Term value) {
    List<Term> args = new ArrayList<>(argsFromScope());
    for (int i = scope.size() - 1; i >= 0; i--) {
      if (scope.get(i).sourceName().equals(name)) {
        args.set(i, value);
        return args;
      }
    }
    throw new IllegalStateException("Unbound variable in scope: " + name.name());
  }

  /**
   * Brings a binding into scope at the innermost end. The binding keeps its source identifier for
   * resolution, but its LCTRS variable gets a name that is unique among the currently live scope,
   * so a shadowing binding ({@code let x ...; let x ...}, a {@code let} over a parameter) stays a
   * distinct variable in the configuration rather than collapsing onto the binding it shadows.
   *
   * @param sourceName the variable's identifier in the source program
   * @param sourceType the variable's source type
   */
  void addToScope(Identifier sourceName, Type sourceType) {
    VarDecl varDecl = new VarDecl(freshName(sourceName.name()), Sort.of(sourceType));
    scope.add(new ScopedVar(sourceName, varDecl, sourceType));
  }

  /**
   * Derives an LCTRS variable name from a source name that collides with no variable currently live
   * in the scope. The source name is used as-is when free, so the common, non-shadowing case keeps
   * readable names; otherwise a numeric suffix is appended until the name is unused. Checking
   * against the live names (not just appending blindly) means the minted name cannot clash with an
   * unrelated source identifier such as a real {@code x1}.
   *
   * @param base the source name to derive an LCTRS name from
   * @return a name not currently used by any live scope variable
   */
  private String freshName(String base) {
    String candidate = base;
    int suffix = 1;
    while (nameInUse(candidate)) {
      candidate = base + "_" + suffix++;
    }
    return candidate;
  }

  /**
   * Brings a synthetic division-correction variable into scope. Its source identifier is its own
   * fresh LCTRS name, so the hoist's replacement {@code Variable} resolves back to it. The {@code
   * $} is a legal Cora identifier character but not a legal Rust one, so the name can never collide
   * with a source variable and marks the variable as compiler-generated.
   *
   * @param width the integer width driving the fresh variable's sort
   * @return the newly bound hoist variable
   */
  ScopedVar addHoistVar(Type.Int width) {
    String name = freshName("$div");
    ScopedVar sv = new ScopedVar(new Identifier(name), new VarDecl(name, Sort.INT), width);
    scope.add(sv);
    return sv;
  }

  /**
   * Reports whether any variable currently live in the scope already carries the given LCTRS name.
   *
   * @param name the candidate LCTRS variable name
   * @return {@code true} if a live scope variable already uses that name
   */
  private boolean nameInUse(String name) {
    return scope.stream().anyMatch(v -> v.varDecl().name().equals(name));
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
    if (LOG.isDebugEnabled()) {
      LOG.debug("Adding rule {}", Serialiser.serialise(r));
    }
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
   * Resolves a source identifier to its binding, searching innermost-first so that the nearest
   * enclosing binding wins. This is what makes a shadowing binding take precedence over the one it
   * shadows.
   *
   * @param name the source identifier to look up
   * @return the matching scope binding
   * @throws IllegalStateException if no variable of that name is in scope
   */
  ScopedVar resolve(Identifier name) {
    for (int i = scope.size() - 1; i >= 0; i--) {
      ScopedVar scopedVar = scope.get(i);
      if (scopedVar.sourceName().equals(name)) {
        return scopedVar;
      }
    }
    throw new IllegalStateException("Unbound variable in scope: " + name.name());
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

  /**
   * Opens a loop, pushing a fresh {@link LoopContext} so nested {@code break}/{@code continue}
   * statements resolve to this loop until the matching {@link #leaveLoop()}.
   *
   * @param continueTarget the configuration a {@code continue} in this loop jumps back to
   */
  void enterLoop(Term continueTarget) {
    LoopContext loopContext = new LoopContext(continueTarget);
    this.loopContexts.addFirst(loopContext);
  }

  /**
   * Closes the innermost loop and returns its context, so the caller can wire up the recorded break
   * sites to the loop's merge point.
   *
   * @return the context of the loop just closed
   * @throws IllegalStateException if no loop is currently open
   */
  LoopContext leaveLoop() {
    LoopContext loop = loopContexts.pollFirst();
    if (loop != null) {
      return loop;
    }
    throw new IllegalStateException("leaveLoop() called with no open loop");
  }

  /**
   * Records a {@code break} site on the innermost open loop.
   *
   * @param breakTarget the configuration at the {@code break} site
   * @throws IllegalStateException if no loop is currently open
   */
  void addBreakPoint(Term breakTarget) {
    LoopContext current = loopContexts.peekFirst();
    if (current == null) {
      throw new IllegalStateException("addBreakPoint() called with no open loop");
    }
    current.addBreakPoint(breakTarget);
  }

  /**
   * Returns the configuration a {@code continue} jumps back to in the innermost open loop.
   *
   * @return the innermost loop's continue target
   * @throws IllegalStateException if no loop is currently open
   */
  Term getCurrentContinueTarget() {
    LoopContext loop = loopContexts.peekFirst();
    if (loop != null) {
      return loop.continueTarget();
    }
    throw new IllegalStateException("getCurrentContinueTarget() called with no open loop");
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
    List<Sort> argSorts = scope.stream().map((v) -> v.varDecl().sort()).toList();
    return new TermSymbol(notation, argSorts, returnSort);
  }
}
