package project.translator;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import project.ast.Assignment;
import project.ast.BinaryOp;
import project.ast.Block;
import project.ast.BooleanLiteral;
import project.ast.Break;
import project.ast.Continue;
import project.ast.Crate;
import project.ast.Expression;
import project.ast.FunctionDeclaration;
import project.ast.If;
import project.ast.IntegerLiteral;
import project.ast.Item;
import project.ast.Let;
import project.ast.Loop;
import project.ast.Parameter;
import project.ast.Return;
import project.ast.Statement;
import project.ast.Variable;
import project.ast.While;
import project.lctrs.BoolValue;
import project.lctrs.Constraint;
import project.lctrs.FnApp;
import project.lctrs.IntValue;
import project.lctrs.Lctrs;
import project.lctrs.Rule;
import project.lctrs.Sort;
import project.lctrs.Symbol;
import project.lctrs.Term;
import project.lctrs.TermSymbol;
import project.lctrs.TheorySymbol;
import project.lctrs.VarDecl;

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

  /**
   * Creates a translator for a single crate.
   *
   * @param crate the AST to translate
   */
  public Translator(Crate crate) {
    this.crate = crate;
  }

  /**
   * Translates the whole crate into an LCTRS by lowering each top-level item in turn.
   *
   * @return the resulting LCTRS
   */
  public Lctrs translate() {
    Lctrs lctrs = new Lctrs();
    for (Item item : crate.items()) {
      processItem(lctrs, item);
    }
    return lctrs;
  }

  /**
   * Lowers a single top-level item, appending its term symbols and rewrite rules to the LCTRS.
   *
   * @param lctrs the LCTRS being assembled
   * @param item the item to translate
   */
  private void processItem(Lctrs lctrs, Item item) {
    switch (item) {
      case FunctionDeclaration fn -> {
        // A function's return sort is the shared output sort of its whole program-point family, so
        // the Context is constructed per function once that sort is known.
        Context ctx = new Context(Sort.RESULT);
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
      ctx.addToScope(VarDecl.of(parameter), parameter.type());
    }
    Sort functionReturnSort = Sort.of(functionDeclaration.returnType());
    // Want to hand roll the first symbol so that we start with the actual function name
    List<Sort> argSorts =
        functionDeclaration.parameters().stream().map(p -> Sort.of(p.type())).toList();
    // The entry symbol heads the program-point family, so its codomain is the shared result sort,
    // not the function's value return sort. The value sort (functionReturnSort) survives only as
    // ret's argument sort below.
    Symbol entry = new TermSymbol(functionDeclaration.identifier().name(), argSorts, Sort.RESULT);
    // advance() never mints the entry symbol, so register it explicitly or the signature would
    // omit the function's own program-point symbol.
    ctx.register(entry);
    // register return and error symbols for the function (FKN §8.1: ret wraps the value into the
    // result sort, err is the nullary error sink). Held on the Context so return lowering shares
    // one definition instead of rebuilding it.
    Symbol ret = new TermSymbol("ret", List.of(functionReturnSort), Sort.RESULT);
    Symbol err = new TermSymbol("err", List.of(), Sort.RESULT);
    ctx.setResultSymbols(ret, err);
    Term incoming = new FnApp(entry, ctx.argsFromScope());
    processBlock(ctx, functionDeclaration.block(), incoming);
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
    LOG.debug(
        "Lowering {} with incoming configuration {}",
        statement.getClass().getSimpleName(),
        incoming);
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
    BranchGuards guards = conditionGuards(ctx, stmt.condition(), incoming);
    Constraint phi = guards.whenTrue();
    Constraint notPhi = guards.whenFalse();
    List<Term> preScope = ctx.argsFromScope();
    Symbol uWhile = ctx.advance();
    ctx.enterLoop(incoming);
    ctx.addRule(new Rule(incoming, new FnApp(uWhile, preScope), Optional.of(phi)));
    Optional<Term> whileBlockOut = processBlock(ctx, stmt.block(), new FnApp(uWhile, preScope));
    LoopContext loop = ctx.leaveLoop();
    Symbol uMerge = ctx.advance();
    Term merge = new FnApp(uMerge, preScope);
    ctx.addRule(new Rule(incoming, merge, Optional.of(notPhi)));
    if (whileBlockOut.isPresent()) {
      ctx.addRule(new Rule(whileBlockOut.get(), incoming, Optional.empty()));
    }

    for (Term site : loop.breakPoints()) {
      ctx.addRule(new Rule(site, merge, Optional.empty()));
    }
    return merge;
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
    BranchGuards guards = conditionGuards(ctx, stmt.condition(), incoming);
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
   * Lowers a {@code return}: evaluates the returned expression and rewrites the incoming
   * configuration to {@code ret(value)}, the normal-completion term of the function's {@code
   * result} sort (FKN §8.1). Control diverges here, so the caller discards anything after it.
   *
   * @param ctx the per-function translation state
   * @param ret the return statement to translate
   * @param incoming the configuration flowing into the return
   * @return the term the configuration rewrites to ({@code ret(value)})
   */
  private Term processReturnStatement(Context ctx, Return ret, Term incoming) {
    Term value = processExpression(ctx, ret.value());
    Term wrapped = new FnApp(ctx.ret(), List.of(value));
    emitDivSafe(ctx, ret.value(), incoming, wrapped);
    return wrapped;
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
    String name = assignment.target().name();
    Term value = processExpression(ctx, assignment.value());

    // Fails if variable is not in scope
    ctx.resolve(name);

    Symbol to = ctx.advance();
    Term rhs = new FnApp(to, ctx.argsWithValue(name, value));
    emitDivSafe(ctx, assignment.value(), incoming, rhs);
    return new FnApp(to, ctx.argsFromScope());
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
    List<Term> oldArgs = ctx.argsFromScope();
    Term value = processExpression(ctx, let.value());
    VarDecl varDecl = new VarDecl(let.identifier().name(), Sort.of(let.type()));
    ctx.addToScope(varDecl, let.type());
    Symbol to = ctx.advance();
    List<Term> rhsArgs = new ArrayList<>(oldArgs);
    rhsArgs.add(value);
    Term rhs = new FnApp(to, rhsArgs);
    emitDivSafe(ctx, let.value(), incoming, rhs);
    return new FnApp(to, ctx.argsFromScope());
  }

  /**
   * Emits the error rule {@code incoming -> err [¬safety]} when {@code e} can divide or take a
   * modulus by zero, and returns the safety formula so callers can guard their normal-path rules
   * with it. When {@code e} cannot fault, emits nothing and returns empty.
   *
   * @param ctx the per-function translation state
   * @param e the expression about to be evaluated into a rule's right-hand side
   * @param incoming the configuration flowing into the statement
   * @return the safety formula to conjoin onto the normal-path guard(s), or empty if {@code e}
   *     cannot fault
   */
  private Optional<Term> emitErrIfFaulty(Context ctx, Expression e, Term incoming) {
    Optional<Term> safety = errorFree(ctx, e);
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
   * @param e the expression evaluated into {@code safeRhs}
   * @param incoming the configuration flowing into the statement
   * @param safeRhs the configuration the statement rewrites to on the normal path
   */
  private void emitDivSafe(Context ctx, Expression e, Term incoming, Term safeRhs) {
    Optional<Term> safety = emitErrIfFaulty(ctx, e, incoming);
    ctx.addRule(new Rule(incoming, safeRhs, safety.map(Constraint::new)));
  }

  /**
   * The two branch guards for a condition, each already conjoined with the condition's safety
   * formula so that the true, false and error guards partition the cases without overlap.
   *
   * @param whenTrue the guard under which the condition's true branch is taken
   * @param whenFalse the guard under which the condition's false branch is taken
   */
  private record BranchGuards(Constraint whenTrue, Constraint whenFalse) {}

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
  private BranchGuards conditionGuards(Context ctx, Expression condition, Term incoming) {
    Term cond = processExpression(ctx, condition);
    Optional<Term> safety = emitErrIfFaulty(ctx, condition, incoming);
    Term whenTrue = conjoin(safety, Optional.of(cond)).orElseThrow();
    Term whenFalse =
        conjoin(safety, Optional.of(new FnApp(TheorySymbol.NOT, List.of(cond)))).orElseThrow();
    return new BranchGuards(new Constraint(whenTrue), new Constraint(whenFalse));
  }

  /**
   * Translates an expression to a theory term over the current scope. Literals become theory
   * values, binary operations become applications of the corresponding theory symbol, and variables
   * resolve to their declaration in scope.
   *
   * @param ctx the per-function translation state
   * @param expression the expression to translate
   * @return the term denoting the expression
   */
  private Term processExpression(Context ctx, Expression expression) {
    return switch (expression) {
      case IntegerLiteral expr -> new IntValue(expr.value());
      case BooleanLiteral expr -> new BoolValue(expr.value());
      case BinaryOp expr -> {
        Term left = processExpression(ctx, expr.left());
        Term right = processExpression(ctx, expr.right());
        Symbol theorySymbol = theorySymbolFor(expr.operator(), left.sort());
        yield new FnApp(theorySymbol, List.of(left, right));
      }
      case Variable expr -> ctx.resolve(expr.name().name()).varDecl();
    };
  }

  @SuppressWarnings("unused")
  private Optional<Term> errorFree(Context ctx, Expression expression) {
    return switch (expression) {
      case IntegerLiteral e -> Optional.empty();
      case BooleanLiteral e -> Optional.empty();
      case Variable e -> Optional.empty();
      case BinaryOp expr -> {
        Optional<Term> leftFree = errorFree(ctx, expr.left());
        Optional<Term> rightFree = errorFree(ctx, expr.right());
        Optional<Term> combined = conjoin(leftFree, rightFree);
        if (expr.operator() == BinaryOp.Op.DIV || expr.operator() == BinaryOp.Op.MOD) {
          Term divisorNonZero =
              new FnApp(
                  TheorySymbol.NEQ_INT,
                  List.of(
                      processExpression(ctx, expr.right()), new IntValue(BigInteger.valueOf(0))));
          yield conjoin(combined, Optional.of(divisorNonZero));
        }
        yield combined;
      }
    };
  }

  private Optional<Term> conjoin(Optional<Term> a, Optional<Term> b) {
    if (a.isEmpty()) {
      return b;
    }
    if (b.isEmpty()) {
      return a;
    }
    return Optional.of(new FnApp(TheorySymbol.AND, List.of(a.get(), b.get())));
  }

  /**
   * Resolves a binary operator to its theory symbol.
   *
   * @param op the AST binary operator
   * @param operandSort the shared sort of both operands for equality operators
   * @return the corresponding theory symbol
   */
  static TheorySymbol theorySymbolFor(BinaryOp.Op op, Sort operandSort) {
    return switch (op) {
      case ADD -> TheorySymbol.ADD;
      case SUB -> TheorySymbol.SUB;
      case MUL -> TheorySymbol.MUL;
      case DIV -> TheorySymbol.DIV;
      case MOD -> TheorySymbol.MOD;
      case LT -> TheorySymbol.LT;
      case LE -> TheorySymbol.LE;
      case GT -> TheorySymbol.GT;
      case GE -> TheorySymbol.GE;
      case EQ -> operandSort == Sort.BOOL ? TheorySymbol.EQ_BOOL : TheorySymbol.EQ_INT;
      case NE -> operandSort == Sort.BOOL ? TheorySymbol.NEQ_BOOL : TheorySymbol.NEQ_INT;
    };
  }
}
