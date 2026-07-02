package project.translator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import project.ast.Assignment;
import project.ast.BinaryOp;
import project.ast.BinaryOp.Op;
import project.ast.Block;
import project.ast.Break;
import project.ast.Continue;
import project.ast.Crate;
import project.ast.Expression;
import project.ast.FunctionCall;
import project.ast.FunctionDeclaration;
import project.ast.Identifier;
import project.ast.If;
import project.ast.Item;
import project.ast.Let;
import project.ast.Loop;
import project.ast.Parameter;
import project.ast.Return;
import project.ast.SpanTable;
import project.ast.Statement;
import project.ast.Type;
import project.ast.UnaryNot;
import project.ast.UnaryOp;
import project.ast.UnsupportedConstructException;
import project.ast.Variable;
import project.ast.While;
import project.lctrs.Constraint;
import project.lctrs.FnApp;
import project.lctrs.Lctrs;
import project.lctrs.Rule;
import project.lctrs.ScopedVar;
import project.lctrs.Serialiser;
import project.lctrs.Sort;
import project.lctrs.Symbol;
import project.lctrs.Term;
import project.lctrs.TermSymbol;
import project.lctrs.TheorySymbol;
import project.translator.ExpressionLowering.Corrected;
import project.translator.ExpressionLowering.DivisionHoist;

/**
 * Lowers a {@link Crate} to an {@link Lctrs} by walking the AST and emitting constrained rewrite
 * rules over program-point function symbols, following the encoding of Fuhs, Kop &amp; Nishida
 * (2017).
 *
 * <p>Each statement is translated against an <em>incoming</em> configuration — a term rooted at the
 * current program-point function symbol applied to the live scope — and yields the configuration
 * that the next statement flows into. Per-function state (scope, signature, rules) lives in {@link
 * Context}.
 */
public class Translator {

  private static final Logger LOG = LoggerFactory.getLogger(Translator.class);

  final Crate crate;
  private final SpanTable spans;

  /**
   * Creates a translator for a single crate with no span information, so provenance trace lines
   * report locations as {@code "<unknown>"}. Suitable for hand-built ASTs in tests.
   *
   * @param crate the AST to translate
   */
  public Translator(Crate crate) {
    this(crate, new SpanTable());
  }

  /**
   * Creates a translator for a single crate, reading source locations from the given span table for
   * provenance trace output.
   *
   * @param crate the AST to translate
   * @param spans the span table populated during the parse-to-AST walk
   */
  public Translator(Crate crate, SpanTable spans) {
    this.crate = crate;
    this.spans = spans;
  }

  /**
   * Translates the whole crate into an LCTRS by lowering each top-level item in turn.
   *
   * @return the resulting LCTRS
   */
  public Lctrs translate() {
    Lctrs lctrs = new Lctrs();
    CrateScope scope = new CrateScope(new AtomicInteger(), buildRegistry());
    for (Item item : crate.items()) {
      processItem(lctrs, item, scope);
    }
    return lctrs;
  }

  /**
   * Crate-wide state shared by every function's {@link Context}.
   *
   * @param counter the program-point counter, shared so {@code u}-symbols stay globally unique
   * @param registry the function name to {@link Signature} map for resolving calls
   */
  record CrateScope(AtomicInteger counter, Map<String, Signature> registry) {}

  /**
   * The callee-facing facts about a function needed to encode a call to it: the entry program-point
   * symbol that heads its redex, and the return type the captured result takes.
   *
   * @param entry the function's entry program-point symbol
   * @param returnType the function's declared return type
   */
  record Signature(Symbol entry, Optional<Type> returnType) {}

  /**
   * Builds the crate-wide signature registry by minting each function's entry symbol from its
   * declaration alone. Runs before any body is lowered, so calls resolve regardless of source
   * order.
   *
   * @return a map from function name to its {@link Signature}
   */
  private Map<String, Signature> buildRegistry() {
    Map<String, Signature> registry = new HashMap<>();
    for (Item item : crate.items()) {
      if (item instanceof FunctionDeclaration fn) {
        List<Sort> argSorts = fn.parameters().stream().map(p -> Sort.of(p.type())).toList();
        // The entry symbol heads the program-point family, so its codomain is the shared result
        // sort, not the function's value return sort.
        Symbol entry = new TermSymbol(fn.identifier().name(), argSorts, Sort.RESULT);
        registry.put(fn.identifier().name(), new Signature(entry, fn.returnType()));
      }
    }
    return registry;
  }

  /**
   * Lowers a single top-level item, appending its term symbols and rewrite rules to the LCTRS.
   *
   * @param lctrs the LCTRS being assembled
   * @param item the item to translate
   * @param scope the crate-wide state shared across all functions of this translation
   */
  private void processItem(Lctrs lctrs, Item item, CrateScope scope) {
    switch (item) {
      case FunctionDeclaration fn -> {
        // A function's return sort is the shared output sort of its whole program-point family, so
        // the Context is constructed per function once that sort is known.
        Context ctx = new Context(Sort.RESULT, fn.returnType(), scope);
        processFunctionDeclaration(ctx, fn);
        lctrs.appendSymbols(ctx.sigma());
        lctrs.appendRules(ctx.rules());
      }
    }
  }

  /**
   * Lowers a function: seeds the scope with its parameters, hand-rolls the entry program-point
   * symbol (named after the function so the family starts at the actual function name), and
   * translates the body against that initial configuration.
   *
   * @param ctx the per-function translation state
   * @param functionDeclaration the function to translate
   */
  private void processFunctionDeclaration(Context ctx, FunctionDeclaration functionDeclaration) {
    for (Parameter parameter : functionDeclaration.parameters()) {
      ctx.addToScope(parameter.identifier(), parameter.type());
    }
    // The entry symbol is minted once in the registry pre-pass; pull this function's from there so
    // a
    // self-call and a forward call resolve to the identical symbol. advance() never mints it, so
    // register it explicitly or the signature would omit the function's own program-point symbol.
    Symbol entry = ctx.calleeEntry(functionDeclaration.identifier());
    ctx.register(entry);
    ctx.setEntry(entry);
    // ret wraps the value into the result sort, err is the nullary error sink (FKN §8.1). Held on
    // the Context so return lowering shares one definition instead of rebuilding it.
    Symbol ret = retSymbol(functionDeclaration.returnType());
    Symbol err = new TermSymbol("err", List.of(), Sort.RESULT);
    ctx.setResultSymbols(ret, err);
    Term incoming = new FnApp(entry, ctx.argsFromScope());
    Optional<Term> tail = processBlock(ctx, functionDeclaration.block(), incoming);
    if (ctx.returnType().isEmpty() && tail.isPresent()) {
      ctx.addRule(new Rule(tail.get(), new FnApp(ctx.ret(), List.of()), Optional.empty()));
    }
  }

  /**
   * Mints the {@code ret} program-point symbol for a function's return type (FKN §8.1). Cora forbids
   * overloading one name across sorts, so the symbol is qualified by value sort ({@code ret_Int},
   * {@code ret_Bool}) — or {@code ret_unit}, a nullary sink, for a {@code ()}-returning function.
   *
   * @param returnType the declared return type, empty if unit
   * @return the qualified, well-sorted return symbol
   */
  private static Symbol retSymbol(Optional<Type> returnType) {
    return returnType.isEmpty()
        ? new TermSymbol("ret_unit", List.of(), Sort.RESULT)
        : retSymbol(returnType.get());
  }

  /**
   * Mints the sorted {@code ret_<sort>} symbol for a function that returns a value (FKN §8.1).
   *
   * @param valueType the declared, non-unit return type
   * @return the qualified, well-sorted return symbol
   */
  private static Symbol retSymbol(Type valueType) {
    Sort sort = Sort.of(valueType);
    return new TermSymbol("ret_" + sort.notation(), List.of(sort), Sort.RESULT);
  }

  /**
   * Lowers the statements of a block in sequence, threading each statement's outgoing configuration
   * into the next as its incoming configuration.
   *
   * @param ctx the per-function translation state
   * @param block the block to translate
   * @param incoming the configuration flowing into the first statement
   */
  private Optional<Term> processBlock(Context ctx, Block block, Term incoming) {
    ctx.enterScope();
    try {
      for (Statement statement : block.statements()) {
        Optional<Term> outgoing = processStatement(ctx, statement, incoming);
        if (outgoing.isEmpty()) {
          // Control diverges at this statement (a return, or an if both of whose branches diverge):
          // the tail and any dead trailing statements are unreachable, so stop lowering the block.
          return Optional.empty();
        }
        incoming = outgoing.get();
      }
      return Optional.of(incoming);
    } finally {
      // Bindings made inside the block fall out of scope on exit, including on the divergent path.
      ctx.leaveScope();
    }
  }

  /**
   * Dispatches a statement to its translation rule.
   *
   * @param ctx the per-function translation state
   * @param statement the statement to translate
   * @param incoming the configuration flowing into the statement
   * @return the configuration flowing out to the next statement, or empty if control diverges here
   */
  @SuppressWarnings("unused")
  private Optional<Term> processStatement(Context ctx, Statement statement, Term incoming) {
    if (LOG.isTraceEnabled()) {
      LOG.trace(
          "{} at {} → incoming {}",
          statement.getClass().getSimpleName(),
          spans.describe(statement),
          Serialiser.serialise(incoming));
    }
    return switch (statement) {
      case Let stmt -> Optional.of(processLetStatement(ctx, stmt, incoming));
      case If stmt -> processIfStatement(ctx, stmt, incoming);
      case While stmt -> Optional.of(processWhileStatement(ctx, stmt, incoming));
      case Loop stmt -> processLoopStatement(ctx, stmt, incoming);
      case Break stmt -> {
        ctx.addBreakPoint(incoming);
        yield Optional.empty();
      }
      case Continue stmt -> {
        ctx.addRule(new Rule(incoming, ctx.getCurrentContinueTarget(), Optional.empty()));
        yield Optional.empty();
      }
      case Return stmt -> {
        processReturnStatement(ctx, stmt, incoming);
        yield Optional.empty();
      }
      case Assignment stmt -> Optional.of(processAssignmentStatement(ctx, stmt, incoming));
    };
  }

  /**
   * Lowers a {@code let} binding: evaluates the bound expression over the pre-binding scope, brings
   * the new variable into scope, and rewrites the incoming configuration to a fresh program point
   * whose configuration extends the scope with the bound value.
   *
   * @param ctx the per-function translation state
   * @param let the let binding to translate
   * @param incoming the configuration flowing into the binding
   * @return the configuration at the fresh program point, over the extended scope
   */
  private Term processLetStatement(Context ctx, Let let, Term incoming) {
    // Lower the safety formula now, over the pre-binding scope, before addToScope can shadow a name
    // the value reads. Otherwise the value and its overflow guard would resolve the same name to
    // different variables.
    LoweredExpr low = lowerHoisted(ctx, let.value(), intWidth(let.type()), incoming);
    incoming = low.outgoing();
    List<Term> oldArgs = ctx.argsFromScope();
    ctx.addToScope(let.identifier(), let.type());
    Symbol to = ctx.advance();
    List<Term> rhsArgs = new ArrayList<>(oldArgs);
    rhsArgs.add(low.term());
    Term rhs = new FnApp(to, rhsArgs);
    emitDivSafe(ctx, low.safety(), incoming, rhs);
    return new FnApp(to, ctx.argsFromScope());
  }

  /**
   * Lowers an {@code if}/{@code else}. Mints a then program point reached when the condition holds
   * and, if an else block is present, an else program point reached when it fails; each branch's
   * body is lowered against its program point. Branches that fall through, plus the false
   * fall-through when there is no else, rewrite to a shared merge program point where control
   * resumes. When an else is present and both branches diverge nothing reaches the merge, so no
   * merge point is minted and the whole statement diverges.
   *
   * @param ctx the per-function translation state
   * @param stmt the {@code if} statement to translate
   * @param incoming the configuration flowing into the statement
   * @return the configuration at the merge point, or empty if control diverges here
   */
  private Optional<Term> processIfStatement(Context ctx, If stmt, Term incoming) {
    boolean elsePresent = stmt.elseBlock().isPresent();
    LoweredCond guards = conditionGuards(ctx, stmt.condition(), incoming);
    incoming = guards.outgoing();
    Constraint phi = guards.whenTrue();
    Constraint notPhi = guards.whenFalse();
    List<Term> preScope = ctx.argsFromScope();
    Symbol uThen = ctx.advance();
    ctx.addRule(new Rule(incoming, new FnApp(uThen, preScope), Optional.of(phi)));
    Optional<Term> thenBlockOut = processBlock(ctx, stmt.thenBlock(), new FnApp(uThen, preScope));
    Optional<Term> elseBlockOut = Optional.empty();
    if (elsePresent) {
      Symbol uElse = ctx.advance();
      ctx.addRule(new Rule(incoming, new FnApp(uElse, preScope), Optional.of(notPhi)));
      elseBlockOut = processBlock(ctx, stmt.elseBlock().get(), new FnApp(uElse, preScope));
    }
    // The merge point is reachable from the false fall-through (when there is no else) and from
    // whichever branches fall through. If an else is present and both branches diverge, nothing
    // reaches the merge: the whole if diverges, so don't mint an orphan uMerge.
    boolean mergeReachable = !elsePresent || thenBlockOut.isPresent() || elseBlockOut.isPresent();
    if (!mergeReachable) {
      return Optional.empty();
    }
    Symbol uMerge = ctx.advance();
    Term merge = new FnApp(uMerge, preScope);
    if (!elsePresent) {
      ctx.addRule(new Rule(incoming, merge, Optional.of(notPhi)));
    }
    if (thenBlockOut.isPresent()) {
      ctx.addRule(new Rule(thenBlockOut.get(), merge, Optional.empty()));
    }
    if (elseBlockOut.isPresent()) {
      ctx.addRule(new Rule(elseBlockOut.get(), merge, Optional.empty()));
    }
    return Optional.of(merge);
  }

  /**
   * Lowers a {@code while} loop. Mints a loop-head program point reached from the incoming
   * configuration when the condition holds, lowers the body against it, and feeds the body's
   * outgoing configuration back to the incoming configuration to close the loop. A merge program
   * point is reached when the condition fails, and is where control resumes after the loop.
   *
   * @param ctx the per-function translation state
   * @param stmt the {@code while} statement to translate
   * @param incoming the configuration flowing into the loop
   * @return the configuration at the merge point, over the scope live before the loop
   */
  private Term processWhileStatement(Context ctx, While stmt, Term incoming) {
    // The condition is re-evaluated on every iteration, so when it hoists a division the back-edge
    // and continue must return to the loop top (pre-condition), not to the post-condition point:
    // the next iteration has to re-run the hoist. incoming is that loop top; afterCond is where the
    // condition's hoist program points (if any) flow to, and is where the true/false guards apply.
    // With a division-free condition hoistAll is a no-op and afterCond == incoming, recovering the
    // plain encoding.
    Term loopTop = incoming;
    LoweredCond guards = conditionGuards(ctx, stmt.condition(), loopTop);
    Term afterCond = guards.outgoing();
    Constraint phi = guards.whenTrue();
    Constraint notPhi = guards.whenFalse();
    List<Term> preScope = ctx.argsFromScope();
    Symbol uWhile = ctx.advance();
    Term head = new FnApp(uWhile, preScope);
    ctx.enterLoop(loopTop);
    ctx.addRule(new Rule(afterCond, head, Optional.of(phi)));
    Optional<Term> whileBlockOut = processBlock(ctx, stmt.block(), head);
    LoopContext loop = ctx.leaveLoop();
    Symbol uMerge = ctx.advance();
    Term merge = new FnApp(uMerge, preScope);
    ctx.addRule(new Rule(afterCond, merge, Optional.of(notPhi)));
    if (whileBlockOut.isPresent()) {
      ctx.addRule(new Rule(whileBlockOut.get(), loopTop, Optional.empty()));
    }

    for (Term site : loop.breakPoints()) {
      ctx.addRule(new Rule(site, merge, Optional.empty()));
    }
    return merge;
  }

  /**
   * Lowers an unconditional {@code loop}. Mints a loop-head program point that the incoming
   * configuration unconditionally enters, lowers the body against it, and feeds any fall-through
   * back to the head to close the loop. A {@code loop} is exited only via {@code break}: if the
   * body records no break sites the loop diverges and nothing flows out; otherwise a merge program
   * point is minted that every break site rewrites to, and control resumes there.
   *
   * @param ctx the per-function translation state
   * @param stmt the {@code loop} statement to translate
   * @param incoming the configuration flowing into the loop
   * @return the configuration at the merge point, or empty if the loop has no {@code break} and so
   *     diverges
   */
  private Optional<Term> processLoopStatement(Context ctx, Loop stmt, Term incoming) {
    List<Term> preScope = ctx.argsFromScope();
    Symbol uLoop = ctx.advance();
    Term continueTarget = new FnApp(uLoop, preScope);
    ctx.enterLoop(continueTarget);
    ctx.addRule(new Rule(incoming, continueTarget, Optional.empty()));
    Optional<Term> loopBlockOut = processBlock(ctx, stmt.block(), continueTarget);
    LoopContext loop = ctx.leaveLoop();
    if (loopBlockOut.isPresent()) {
      ctx.addRule(new Rule(loopBlockOut.get(), incoming, Optional.empty()));
    }
    if (loop.breakPoints().isEmpty()) {
      return Optional.empty();
    }
    Symbol uMerge = ctx.advance();
    Term merge = new FnApp(uMerge, preScope);
    for (Term site : loop.breakPoints()) {
      ctx.addRule(new Rule(site, merge, Optional.empty()));
    }
    return Optional.of(merge);
  }

  /**
   * Lowers a {@code return}: evaluates the returned expression and rewrites the incoming
   * configuration to {@code ret(value)}, the normal-completion term of the function's {@code
   * result} sort (FKN §8.1). Control diverges here, so the caller discards anything after it.
   *
   * @param ctx the per-function translation state
   * @param ret the return statement to translate
   * @param incoming the configuration flowing into the return
   */
  private void processReturnStatement(Context ctx, Return ret, Term incoming) {
    if (ret.value().isEmpty()) {
      ctx.addRule(new Rule(incoming, new FnApp(ctx.ret(), List.of()), Optional.empty()));
      return;
    }
    Optional<Type.Int> ctxWidth = ctx.returnType().flatMap(Translator::intWidth);
    LoweredExpr low = lowerHoisted(ctx, ret.value().get(), ctxWidth, incoming);
    Term wrapped = new FnApp(ctx.ret(), List.of(low.term()));
    emitDivSafe(ctx, low.safety(), low.outgoing(), wrapped);
  }

  /**
   * Lowers an assignment to a reassignment rule: evaluates the right-hand side, then rewrites the
   * incoming configuration to a fresh program point whose configuration replaces the target's slot
   * with the new value, leaving the other variables unchanged.
   *
   * @param ctx the per-function translation state
   * @param assignment the assignment to translate
   * @param incoming the configuration flowing into the assignment
   * @return the configuration at the fresh program point, over the unchanged scope
   */
  private Term processAssignmentStatement(Context ctx, Assignment assignment, Term incoming) {
    Identifier target = assignment.target();
    Optional<Type.Int> ctxWidth = intWidth(ctx.resolve(target).sourceType());
    LoweredExpr low = lowerHoisted(ctx, assignment.value(), ctxWidth, incoming);
    incoming = low.outgoing();

    // Fails if variable is not in scope
    Symbol to = ctx.advance();
    Term rhs = new FnApp(to, ctx.argsWithValue(target, low.term()));
    emitDivSafe(ctx, low.safety(), incoming, rhs);
    return new FnApp(to, ctx.argsFromScope());
  }

  /**
   * Emits the error rule {@code incoming -> err [¬safety]} when {@code e} can divide or take a
   * modulus by zero, and returns the safety formula so callers can guard their normal-path rules
   * with it. When {@code e} cannot fault, emits nothing and returns empty.
   *
   * <p>The safety formula is supplied already lowered rather than recomputed here: a {@code let}
   * binding mutates the scope between lowering its value and emitting its rules, so re-lowering
   * would resolve a shadowed name to the wrong (newly bound) variable. Callers lower the value and
   * its safety formula together, before any such scope change.
   *
   * @param ctx the per-function translation state
   * @param safety the expression's safety formula, lowered over the scope its value was lowered in,
   *     or empty if the expression cannot fault
   * @param incoming the configuration flowing into the statement
   * @return {@code safety} unchanged, for callers that conjoin it onto further guards
   */
  private Optional<Term> emitErrIfFaulty(Context ctx, Optional<Term> safety, Term incoming) {
    if (safety.isPresent()) {
      ctx.addRule(
          new Rule(
              incoming,
              new FnApp(ctx.err(), List.of()),
              Optional.of(new Constraint(new FnApp(TheorySymbol.NOT, List.of(safety.get()))))));
    }
    return safety;
  }

  /**
   * Emits the rule(s) for a statement that rewrites {@code incoming} to {@code safeRhs} after
   * evaluating {@code e}. When {@code e} can divide or take a modulus by zero this is the FKN pair
   * the error rule plus the normal rewrite guarded by the divisors being non-zero; otherwise it is
   * the single unguarded rule.
   *
   * @param ctx the per-function translation state
   * @param safety the safety formula of the expression evaluated into {@code safeRhs}, lowered over
   *     the scope its value was lowered in, or empty if it cannot fault
   * @param incoming the configuration flowing into the statement
   * @param safeRhs the configuration the statement rewrites to on the normal path
   */
  private void emitDivSafe(Context ctx, Optional<Term> safety, Term incoming, Term safeRhs) {
    emitErrIfFaulty(ctx, safety, incoming);
    ctx.addRule(new Rule(incoming, safeRhs, safety.map(Constraint::new)));
  }

  private record HoistedExpr(Expression expr, Term outgoing) {}

  private record LoweredExpr(Term term, Optional<Term> safety, Term outgoing) {}

  /**
   * The two branch guards for a condition, each already conjoined with the condition's safety
   * formula so that the true, false and error guards partition the cases without overlap, plus the
   * configuration the condition's hoist program points (if any) flow to, where both guards apply.
   *
   * @param whenTrue the guard under which the condition's true branch is taken
   * @param whenFalse the guard under which the condition's false branch is taken
   * @param outgoing the configuration after the condition's hoisted divisions, where the guards
   *     hold
   */
  private record LoweredCond(Constraint whenTrue, Constraint whenFalse, Term outgoing) {}

  /**
   * Recursively walks {@code e}, emitting division-hoist rules for every {@code /} or {@code %}
   * sub-expression and returning the rewritten expression (with hoist variables substituted in) and
   * the outgoing configuration after all hoists.
   *
   * @param ctx the translation context
   * @param e the expression to hoist
   * @param ctxWidth the integer width inferred from the binding/return/assignment target
   * @param incoming the configuration term at the entry to {@code e}
   * @return the hoisted expression and its outgoing configuration
   */
  private HoistedExpr hoistAll(
      Context ctx, Expression e, Optional<Type.Int> ctxWidth, Term incoming) {
    return switch (e) {
      case BinaryOp op -> {
        HoistedExpr l = hoistAll(ctx, op.left(), ctxWidth, incoming);
        HoistedExpr r = hoistAll(ctx, op.right(), ctxWidth, l.outgoing());
        BinaryOp rewritten = new BinaryOp(op.operator(), l.expr(), r.expr());
        yield (op.operator() == Op.DIV || op.operator() == Op.MOD)
            ? emitDivisionHoist(ctx, rewritten, ctxWidth, r.outgoing())
            : new HoistedExpr(rewritten, r.outgoing());
      }
      case UnaryOp u -> {
        HoistedExpr inner = hoistAll(ctx, u.operand(), ctxWidth, incoming);
        // `!` on an integer is bitwise complement, which is out of scope. AstBuilder cannot tell it
        // apart from logical not without sorts, so reject it here, where the operand's sort is
        // known.
        if (u instanceof UnaryNot
            && ExpressionLowering.lower(ctx, inner.expr()).sort() != Sort.BOOL) {
          throw new UnsupportedConstructException(
              "`!` on an integer is bitwise complement, which is out of scope; only logical not on"
                  + " a bool is supported",
              spans.describe(u));
        }
        yield new HoistedExpr(u.withOperand(inner.expr()), inner.outgoing());
      }
      case FunctionCall c -> emitCallHoist(ctx, c, ctxWidth, incoming);
      default -> new HoistedExpr(e, incoming); // IntegerLiteral, BooleanLiteral, Variable
    };
  }

  /**
   * Hoists a self-recursive call, emitting the rules that run it and capture its result into a
   * fresh variable that the call expression reduces to.
   *
   * @param ctx the translation context
   * @param c the self-recursive call to hoist
   * @param ctxWidth fallback integer width threaded into the argument hoists
   * @param incoming the configuration at the entry of the call
   * @return the hoisted expression (the captured-value variable) and its outgoing configuration
   */
  private HoistedExpr emitCallHoist(
      Context ctx, FunctionCall c, Optional<Type.Int> ctxWidth, Term incoming) {
    Term config = incoming;
    List<Expression> argExprs = new ArrayList<>();
    Optional<Term> argSafety = Optional.empty();
    for (Expression a : c.args()) {
      HoistedExpr h = hoistAll(ctx, a, ctxWidth, config);
      argExprs.add(h.expr());
      config = h.outgoing();
      argSafety = ExpressionLowering.conjoin(argSafety, ExpressionLowering.safety(ctx, h.expr()));
    }
    List<Term> argTerms =
        argExprs.stream().<Term>map(e -> ExpressionLowering.lower(ctx, e)).toList();

    // The jump: incoming -> uCont(x̄, callee(argTerms)) [argSafety], plus the arg-overflow err
    // rule.
    // The callee symbol comes from the registry, so this works for a self-call or a call to any
    // other function in the crate.
    List<Term> preScope = ctx.argsFromScope();
    Symbol uCont = ctx.advanceContinuation(Sort.RESULT);
    Term jumpRhs =
        new FnApp(
            uCont, withTrailing(preScope, new FnApp(ctx.calleeEntry(c.function()), argTerms)));
    emitDivSafe(ctx, argSafety, config, jumpRhs);

    // Landing pad and error propagation. The fresh variable carries the callee's return value,
    // typed
    // by the callee's return type, so downstream arithmetic recovers its width through inferWidth.
    Type calleeType =
        ctx.calleeReturnType(c.function())
            .orElseThrow(
                () ->
                    new UnsupportedConstructException(
                        "calling a ()-returning function is not supported", spans.describe(c)));
    ScopedVar r = ctx.addHoistVar("$call", calleeType);
    Symbol calleeRet = retSymbol(calleeType);

    Symbol uNext = ctx.advance();
    Term uNextTm = new FnApp(uNext, ctx.argsFromScope());
    Term contRet =
        new FnApp(uCont, withTrailing(preScope, new FnApp(calleeRet, List.of(r.varDecl()))));
    Term contErr = new FnApp(uCont, withTrailing(preScope, new FnApp(ctx.err(), List.of())));
    ctx.addRule(new Rule(contRet, uNextTm, Optional.empty()));
    ctx.addRule(new Rule(contErr, new FnApp(ctx.err(), List.of()), Optional.empty()));

    return new HoistedExpr(new Variable(r.sourceName()), uNextTm);
  }

  /**
   * Returns {@code scope} with {@code trailing} appended, for building a continuation configuration
   * over the live scope plus its trailing {@code result} slot.
   */
  private static List<Term> withTrailing(List<Term> scope, Term trailing) {
    List<Term> args = new ArrayList<>(scope);
    args.add(trailing);
    return args;
  }

  /**
   * Emits the hoist rules for a single {@code /} or {@code %} node: an {@code err} rule guarded by
   * {@code ¬S_D}, and one rule per corrected alternative guarded by {@code S_D ∧ guard ∧ fresh =
   * value}. Returns the hoisted expression ({@code fresh}) and the outgoing configuration that
   * carries it.
   *
   * @param ctx the translation context
   * @param division the already-hoisted {@code BinaryOp} whose operator is {@link Op#DIV} or {@link
   *     Op#MOD}
   * @param ctxWidth fallback integer width when neither operand is a variable
   * @param incoming the configuration at the entry of this division
   * @return the hoisted expression and its outgoing configuration
   */
  private HoistedExpr emitDivisionHoist(
      Context ctx, BinaryOp division, Optional<Type.Int> ctxWidth, Term incoming) {
    DivisionHoist info = ExpressionLowering.hoistInfo(ctx, division);
    Type.Int width = info.width().or(() -> ctxWidth).orElse(Type.Int.i32);
    // Width feeds the MIN/-1 guard and the fresh var's sort. Resolved in priority:
    //   1. inferWidth (a variable operand) — authoritative; both operands share its
    //      type in valid Rust, so this is exact whenever any variable is present.
    //   2. ctxWidth — the context type threaded from the binding/return/assignment
    //      target, for a *literal-only* division. This is what catches the widening
    //      case (let x: i64 = -2147483648 / -1): rustc types it i64, so the MIN/-1
    //      guard must use i64::MIN, not i32::MIN, or we'd wrongly route to err.
    //   3. i32 (Rust's literal default) — only when there is no integer context at
    //      all, e.g. a literal-only division inside a comparison, where rustc also
    //      defaults the operands to i32.
    ScopedVar fresh = ctx.addHoistVar("$div", width);
    Symbol point = ctx.advance();
    Term target = new FnApp(point, ctx.argsFromScope()); // carries `fresh`
    // err [¬S_D]
    emitErrIfFaulty(ctx, Optional.of(info.safety()), incoming);
    for (Corrected c : info.alternatives()) {
      Term bind = new FnApp(TheorySymbol.EQ_INT, List.of(fresh.varDecl(), c.value()));
      Term g =
          new FnApp(
              TheorySymbol.AND,
              List.of(new FnApp(TheorySymbol.AND, List.of(info.safety(), c.guard())), bind));
      ctx.addRule(new Rule(incoming, target, Optional.of(new Constraint(g))));
    }
    return new HoistedExpr(new Variable(fresh.sourceName()), target);
  }

  /**
   * Hoists all divisions in {@code e}, lowers the resulting hoist-variable expression to an LCTRS
   * {@link Term}, and returns the term together with its safety formula and outgoing configuration.
   * Hoist variables are scoped to this call: they are visible during lowering but dropped
   * afterwards.
   *
   * @param ctx the translation context
   * @param e the expression to hoist and lower
   * @param ctxWidth fallback integer width for literal-only divisions
   * @param incoming the configuration at the entry of {@code e}
   * @return the lowered term, its safety formula, and the outgoing configuration
   */
  private LoweredExpr lowerHoisted(
      Context ctx, Expression e, Optional<Type.Int> ctxWidth, Term incoming) {
    ctx.enterScope();
    HoistedExpr h = hoistAll(ctx, e, ctxWidth, incoming);
    Term term = ExpressionLowering.lower(ctx, h.expr());
    Optional<Term> safety = ExpressionLowering.safety(ctx, h.expr());
    ctx.leaveScope();
    return new LoweredExpr(term, safety, h.outgoing());
  }

  private static Optional<Type.Int> intWidth(Type t) {
    return t instanceof Type.Int i ? Optional.of(i) : Optional.empty();
  }

  /**
   * Builds the true/false branch guards for a condition and, when the condition can divide or take
   * a modulus by zero, emits the shared error rule {@code incoming -> err [¬safety]} (§8.1). The
   * returned guards are {@code safety ∧ cond} and {@code safety ∧ ¬cond}, so a faulting condition
   * routes to {@code err} rather than to either branch.
   *
   * @param ctx the per-function translation state
   * @param condition the branching condition
   * @param incoming the configuration flowing into the branch
   * @return the guards for the true and false branches
   */
  private LoweredCond conditionGuards(Context ctx, Expression condition, Term incoming) {
    LoweredExpr low = lowerHoisted(ctx, condition, Optional.empty(), incoming);
    Optional<Term> safety = emitErrIfFaulty(ctx, low.safety(), low.outgoing());
    Term whenTrue = ExpressionLowering.conjoin(safety, Optional.of(low.term())).orElseThrow();
    Term whenFalse =
        ExpressionLowering.conjoin(
                safety, Optional.of(new FnApp(TheorySymbol.NOT, List.of(low.term()))))
            .orElseThrow();
    return new LoweredCond(new Constraint(whenTrue), new Constraint(whenFalse), low.outgoing());
  }
}
