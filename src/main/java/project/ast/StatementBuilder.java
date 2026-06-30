package project.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import project.parser.RustParser.AssignmentExpressionContext;
import project.parser.RustParser.BlockExpressionContext;
import project.parser.RustParser.BreakExpressionContext;
import project.parser.RustParser.ContinueExpressionContext;
import project.parser.RustParser.ExpressionContext;
import project.parser.RustParser.ExpressionStatementContext;
import project.parser.RustParser.ExpressionWithBlockContext;
import project.parser.RustParser.ExpressionWithBlock_Context;
import project.parser.RustParser.IfExpressionContext;
import project.parser.RustParser.InfiniteLoopExpressionContext;
import project.parser.RustParser.LetStatementContext;
import project.parser.RustParser.LoopExpressionContext;
import project.parser.RustParser.PathExpression_Context;
import project.parser.RustParser.PredicateLoopExpressionContext;
import project.parser.RustParser.ReturnExpressionContext;
import project.parser.RustParser.StatementContext;
import project.parser.RustParser.StatementsContext;

/**
 * Builds {@link Statement} nodes (and the {@link Block}/{@link BodyBlock} that hold them) from
 * statement parse-tree contexts. Delegates expression position to an {@link ExpressionBuilder} and
 * recurses into itself for nested blocks (loop and {@code if} bodies).
 */
final class StatementBuilder {

  private final SpanRecorder spans;
  private final ExpressionBuilder expressions;
  private final DiagnosticRecorder diagnostics;

  /**
   * Creates a statement builder, self-wiring an {@link ExpressionBuilder} over the same recorder.
   *
   * @param spans the recorder shared across the whole parse-tree walk
   * @param diagnostics the recorder that out-of-scope-construct diagnostics are collected into
   */
  StatementBuilder(SpanRecorder spans, DiagnosticRecorder diagnostics) {
    this.spans = Objects.requireNonNull(spans);
    this.diagnostics = Objects.requireNonNull(diagnostics);
    this.expressions = new ExpressionBuilder(spans);
  }

  /**
   * Records the enclosing function's name on the delegate expression builder, so self-recursive
   * calls can be distinguished from out-of-scope calls to other functions.
   *
   * @param name the enclosing function's name
   */
  void addFunctionIdentifier(Identifier name) {
    expressions.addFunctionIdentifier(name);
  }

  /**
   * Builds a {@link Block} from a block-expression parse-tree context.
   *
   * @param ctx the block expression context
   * @return the corresponding {@link Block} node
   */
  Block buildBlock(BlockExpressionContext ctx) {
    StatementsContext statementsCtx = ctx.statements();
    ExpressionContext tailExpr = statementsCtx == null ? null : statementsCtx.expression();
    List<Statement> built = extractStatementsFromBlock(ctx);
    if (tailExpr != null) {
      built.add(buildImplicitReturn(tailExpr));
    }
    return spans.track(new Block(built), ctx);
  }

  /**
   * Builds a {@link Statement} from a statement parse-tree context. Supported statement forms are
   * {@code let} bindings, assignments, {@code return}, {@code break}, {@code continue}, and {@code
   * if}/{@code while}/{@code loop} statements.
   *
   * @param ctx the statement context
   * @return the corresponding {@link Statement} node
   * @throws UnsupportedConstructException if the statement is not one of the supported forms
   */
  Statement buildStatement(StatementContext ctx) {
    if (ctx.SEMI() != null || ctx.item() != null || ctx.macroInvocationSemi() != null) {
      throw new UnsupportedConstructException(
          ctx,
          "Only let bindings, assignments, return, break, continue, and if/while/loop statements"
              + " are supported");
    }
    ExpressionStatementContext expressionStatementCtx = ctx.expressionStatement();
    if (expressionStatementCtx != null) {
      /* A block-form statement (if/loop/while) without a trailing `;` parses through the dedicated
       * `expressionWithBlock SEMI?` alternative; with a trailing `;` it parses through `expression`
       * as an ExpressionWithBlock_. */
      ExpressionWithBlockContext blockCtx = expressionStatementCtx.expressionWithBlock();
      if (blockCtx != null) {
        return extractBlockExpressionStatement(blockCtx);
      }
      ExpressionContext expressionCtx = expressionStatementCtx.expression();
      return switch (expressionCtx) {
        case ReturnExpressionContext c -> buildReturnStatement(c);
        case BreakExpressionContext c -> buildBreakStatement(c);
        case ContinueExpressionContext c -> buildContinueStatement(c);
        case AssignmentExpressionContext c -> buildAssignment(c);
        case ExpressionWithBlock_Context c ->
            extractBlockExpressionStatement(c.expressionWithBlock());
        default ->
            throw new UnsupportedConstructException(
                ctx,
                "Only let bindings, assignments, return, break, continue, and if/while/loop"
                    + " statements are supported");
      };
    }
    return buildLetStatement(ctx.letStatement());
  }

  /**
   * Builds a {@link Break} from a break-expression context. Labelled breaks and {@code break} with
   * a value are rejected.
   *
   * @param ctx the break-expression context
   * @return the corresponding {@link Break} node
   * @throws UnsupportedConstructException if the break carries a label or a value
   */
  Break buildBreakStatement(BreakExpressionContext ctx) {
    if (ctx.LIFETIME_OR_LABEL() != null || ctx.expression() != null) {
      throw new UnsupportedConstructException(
          ctx, "Break statement with lifetimes, labels, or values are not supported");
    }
    return spans.track(new Break(), ctx);
  }

  /**
   * Builds a {@link Continue} from a continue-expression context. Labelled continues and {@code
   * continue} with a value are rejected.
   *
   * @param ctx the continue-expression context
   * @return the corresponding {@link Continue} node
   * @throws UnsupportedConstructException if the continue carries a label or a value
   */
  Continue buildContinueStatement(ContinueExpressionContext ctx) {
    if (ctx.LIFETIME_OR_LABEL() != null || ctx.expression() != null) {
      throw new UnsupportedConstructException(
          ctx, "Continue statement with lifetimes, labels, or values are not supported");
    }
    return spans.track(new Continue(), ctx);
  }

  /**
   * Builds a {@link Let} from a let-statement parse-tree context.
   *
   * @param ctx the let-statement context
   * @return the corresponding {@link Let} node
   */
  Let buildLetStatement(LetStatementContext ctx) {
    Identifier bindingTarget = BindingReader.boundIdentifier(ctx.patternNoTopAlt());
    if (ctx.type_() == null) {
      throw new UnsupportedConstructException(ctx, "let bindings must have an explicit type");
    }
    Type type = TypeReader.read(ctx.type_());
    ExpressionContext exprCtx = ctx.expression();
    if (exprCtx == null) {
      throw new UnsupportedConstructException(
          ctx, "uninitialised let bindings are not supported; let requires an initialiser");
    }
    Expression expr = expressions.buildExpression(exprCtx);
    return spans.track(new Let(bindingTarget, type, expr), ctx);
  }

  /**
   * Builds a {@link Return} from an expression-statement parse-tree context. Only {@code return}
   * expressions are supported as expression statements.
   *
   * @param ctx the expression statement context
   * @return the corresponding {@link Return} node
   * @throws UnsupportedConstructException if the expression statement is not a {@code return}
   */
  Return buildReturnStatement(ReturnExpressionContext ctx) {
    if (ctx.expression() == null) {
      throw new UnsupportedConstructException(ctx, "Return statement must have an expression");
    }
    return spans.track(new Return(expressions.buildExpression(ctx.expression())), ctx);
  }

  /**
   * Builds a synthesised {@link Return} from a block's trailing value expression, normalising the
   * idiomatic implicit-return form ({@code a}) into the explicit form ({@code return a}). The
   * synthesised node's span is recorded against the expression itself, since there is no {@code
   * return} keyword in the source. Trailing block expressions are rejected: their value cannot flow
   * out of the supported {@code if}/{@code loop}/{@code while} statement forms.
   *
   * @param ctx the trailing expression context
   * @return the corresponding synthesised {@link Return} node
   * @throws UnsupportedConstructException if the trailing expression is a block expression
   */
  private Return buildImplicitReturn(ExpressionContext ctx) {
    if (ctx instanceof ExpressionWithBlock_Context) {
      throw new UnsupportedConstructException(
          ctx, "Trailing block expressions are not supported; use an explicit return statement");
    }
    return spans.track(new Return(expressions.buildExpression(ctx)), ctx);
  }

  /**
   * Builds a {@link While} from a predicate-loop context.
   *
   * @param ctx the predicate-loop (`while`) context
   * @return the corresponding {@link While} node
   */
  While buildWhileStatement(PredicateLoopExpressionContext ctx) {
    Expression condition = expressions.buildExpression(ctx.expression());
    Block body = buildBlock(ctx.blockExpression());
    return spans.track(new While(condition, body), ctx);
  }

  /**
   * Builds a {@link Loop} from an infinite-loop context.
   *
   * @param ctx the infinite-loop (`loop`) context
   * @return the corresponding {@link Loop} node
   */
  Loop buildLoopExpression(InfiniteLoopExpressionContext ctx) {
    Block body = buildBlock(ctx.blockExpression());
    return spans.track(new Loop(body), ctx);
  }

  /**
   * Builds an {@link If} from an if-expression context. {@code if let} expressions are rejected; an
   * {@code else if} chain is built recursively as a nested {@link If} wrapped in the else block.
   *
   * @param ctx the if-expression context
   * @return the corresponding {@link If} node
   * @throws UnsupportedConstructException if the expression is an {@code if let}
   */
  If buildIfStatement(IfExpressionContext ctx) {
    if (ctx.ifLetExpression() != null) {
      throw new UnsupportedConstructException(ctx, "If-let expressions are not supported");
    }
    Expression condition = expressions.buildExpression(ctx.expression());
    Block thenBlock = buildBlock(ctx.blockExpression(0));
    BlockExpressionContext elseBlockContext = ctx.blockExpression(1);
    Optional<Block> elseBlock = Optional.empty();
    if (elseBlockContext != null) {
      elseBlock = Optional.of(buildBlock(elseBlockContext));
    } else if (ctx.ifExpression() != null) {
      elseBlock = Optional.of(new Block(List.of(buildIfStatement(ctx.ifExpression()))));
    }
    return spans.track(new If(condition, thenBlock, elseBlock), ctx);
  }

  /**
   * Builds an {@link Assignment} from an assignment-expression context. The assignment target must
   * be a simple variable (a path expression).
   *
   * @param ctx the assignment-expression context
   * @return the corresponding {@link Assignment} node
   * @throws UnsupportedConstructException if the target is not a simple variable
   */
  Assignment buildAssignment(AssignmentExpressionContext ctx) {
    if (!(ctx.expression(0) instanceof PathExpression_Context targetCtx)) {
      throw new UnsupportedConstructException(
          ctx.expression(0), "Assignment target must be a simple variable");
    }
    Identifier target = expressions.extractPathIdentifier(targetCtx);
    Expression value = expressions.buildExpression(ctx.expression(1));
    return spans.track(new Assignment(target, value), ctx);
  }

  /**
   * Extracts and builds the {@link Statement} list from a block-expression context. An empty block
   * yields an empty list;
   *
   * @param ctx the block-expression context
   * @return the statements in the block, in source order, possibly empty
   * @throws UnsupportedConstructException if the block has a trailing expression
   */
  private List<Statement> extractStatementsFromBlock(BlockExpressionContext ctx) {
    StatementsContext statementsCtx = ctx.statements();
    if (statementsCtx == null) {
      return List.of();
    }
    List<Statement> built = new ArrayList<>();
    for (StatementContext statementCtx : statementsCtx.statement()) {
      try {
        built.add(buildStatement(statementCtx));
      } catch (UnsupportedConstructException e) {
        diagnostics.add(Diagnostic.of(e));
      }
    }
    return built;
  }

  /**
   * Dispatches a block-expression used in statement position to the appropriate builder. Only
   * {@code loop}/{@code while} loops and {@code if} expressions are supported here.
   *
   * @param ctx the block-expression context
   * @return the corresponding {@link Statement} node
   * @throws UnsupportedConstructException if the block-expression kind is not supported
   */
  private Statement extractBlockExpressionStatement(ExpressionWithBlockContext ctx) {
    if (ctx.loopExpression() != null) {
      return extractLoopStatement(ctx.loopExpression());
    }
    if (ctx.ifExpression() != null) {
      return buildIfStatement(ctx.ifExpression());
    }
    throw new UnsupportedConstructException(
        ctx, "Only loop, while, and if block-expressions are supported");
  }

  /**
   * Dispatches a loop-expression to the appropriate builder. Only {@code while} (predicate) and
   * infinite {@code loop} loops are supported; {@code for}, {@code while let}, and labelled loops
   * are rejected.
   *
   * @param ctx the loop-expression context
   * @return the corresponding {@link While} or {@link Loop} node
   * @throws UnsupportedConstructException if the loop is labelled, a {@code for}, or a {@code while
   *     let}
   */
  private Statement extractLoopStatement(LoopExpressionContext ctx) {
    if (ctx.loopLabel() != null) {
      throw new UnsupportedConstructException(ctx, "Loop labels are not supported");
    }
    if (ctx.predicateLoopExpression() != null) {
      return buildWhileStatement(ctx.predicateLoopExpression());
    }
    if (ctx.infiniteLoopExpression() != null) {
      return buildLoopExpression(ctx.infiniteLoopExpression());
    }
    throw new UnsupportedConstructException(ctx, "Only while and loop loops are supported");
  }
}
