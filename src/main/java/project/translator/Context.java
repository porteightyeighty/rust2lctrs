package project.translator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
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
 * <p>The program-point counter is shared across every {@code Context} of one translation (threaded
 * in at construction), so the symbols ({@code u1}, {@code u2}, …) are globally unique and never
 * clash between functions in the shared signature.
 *
 * @see Translator
 */
final class Context {

  private static final Logger LOG = LoggerFactory.getLogger(Context.class);

  private final Translator.CrateScope crateScope;
  private List<ScopedVar> scope = new ArrayList<>();
  private final List<Symbol> sigma = new ArrayList<>();
  private final List<Rule> rules = new ArrayList<>();
  private final Sort returnSort;
  private final Optional<Type> returnType;
  private Symbol ret;
  private Symbol err;
  private final Deque<Integer> scopeMarks = new ArrayDeque<>();
  private final Deque<LoopContext> loopContexts = new ArrayDeque<>();
  private Symbol entry;

  /**
   * Creates a context for a single function.
   *
   * @param returnSort the sort returned by every program-point function symbol of this function
   * @param returnType the function's declared return type
   * @param crateScope the crate-wide state (counter and registry) shared across all functions
   */
  Context(Sort returnSort, Optional<Type> returnType, Translator.CrateScope crateScope) {
    this.returnSort = returnSort;
    this.returnType = returnType;
    this.crateScope = crateScope;
  }

  // --- Function-level facts -------------------------------------------------

  Optional<Type> returnType() {
    return returnType;
  }

  void setEntry(Symbol entry) {
    this.entry = entry;
  }

  Symbol entry() {
    return this.entry;
  }

  /**
   * Returns the entry program-point symbol of the named function, for heading a call's redex.
   *
   * @param name the called function's name
   * @return the callee's entry symbol
   */
  Symbol calleeEntry(Identifier name) {
    return crateScope.registry().get(name.name()).entry();
  }

  /**
   * Returns the declared return type of the named function, for typing a call's captured result.
   *
   * @param name the called function's name
   * @return the callee's return type
   */
  Optional<Type> calleeReturnType(Identifier name) {
    return crateScope.registry().get(name.name()).returnType();
  }

  // --- Program-point symbols and the terms signature ------------------------

  /**
   * Mints the next program-point function symbol over the current scope, records it in the terms
   * signature, and returns it.
   *
   * @return the freshly created program-point function symbol
   */
  Symbol advance() {
    Symbol s = symbolFor(crateScope.counter().incrementAndGet());
    register(s);
    return s;
  }

  Symbol advanceContinuation(Sort resultSort) {
    List<Sort> argSorts = new ArrayList<>(scope.stream().map(v -> v.varDecl().sort()).toList());
    argSorts.add(resultSort);
    Symbol s =
        new TermSymbol("u" + crateScope.counter().incrementAndGet(), argSorts, this.returnSort);
    register(s);
    return s;
  }

  /**
   * Builds a program-point function symbol {@code u<counter>} whose argument sorts are the sorts of
   * the current scope variables and whose result sort is the function's shared return sort.
   *
   * @param counter the program-point number to name the symbol after
   * @return the program-point function symbol for the current scope
   */
  private Symbol symbolFor(int counter) {
    String notation = "u" + counter;
    List<Sort> argSorts = scope.stream().map((v) -> v.varDecl().sort()).toList();
    return new TermSymbol(notation, argSorts, returnSort);
  }

  /**
   * Records a symbol in the terms signature. Used for symbols not minted by {@link #advance()},
   * such as the function's hand-rolled entry program-point symbol, so the signature stays complete.
   *
   * @param s the term symbol to add to the signature
   */
  void register(Symbol s) {
    if (sigma.contains(s)) {
      return;
    }
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

  // --- Result symbols -------------------------------------------------------

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

  // --- Rewrite rules --------------------------------------------------------

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

  // --- Reading the scope ----------------------------------------------------

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

  // --- Binding into the scope -----------------------------------------------

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
   * Brings a synthetic hoist variable into scope. Pass a {@code $}-prefixed {@code prefix} (e.g.
   * {@code $div}, {@code $call}), legal in Cora but not in Rust, so it can't collide with a source
   * variable.
   *
   * @param prefix the base name to derive a fresh LCTRS name from
   * @param sourceType the source type, driving the variable's sort and inferred width
   * @return the newly bound hoist variable
   */
  ScopedVar addHoistVar(String prefix, Type sourceType) {
    String name = freshName(prefix);
    ScopedVar sv =
        new ScopedVar(new Identifier(name), new VarDecl(name, Sort.of(sourceType)), sourceType);
    scope.add(sv);
    return sv;
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
   * Reports whether any variable currently live in the scope already carries the given LCTRS name.
   *
   * @param name the candidate LCTRS variable name
   * @return {@code true} if a live scope variable already uses that name
   */
  private boolean nameInUse(String name) {
    return scope.stream().anyMatch(v -> v.varDecl().name().equals(name));
  }

  // --- Lexical scope nesting ------------------------------------------------

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
   * Truncates the scope back to the given size, dropping the variables bound since. Used when
   * leaving a block whose local bindings fall out of scope.
   *
   * @param newSize the number of variables to retain, counting from the outermost
   */
  private void shrinkScope(int newSize) {
    scope = new ArrayList<>(scope.subList(0, newSize));
  }

  // --- Loops ----------------------------------------------------------------

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
    if (loop == null) {
      throw new IllegalStateException("leaveLoop() called with no open loop");
    }
    return loop;
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
    if (loop == null) {
      throw new IllegalStateException("getCurrentContinueTarget() called with no open loop");
    }
    return loop.continueTarget();
  }
}
