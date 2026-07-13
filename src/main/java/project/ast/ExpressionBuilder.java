package project.ast;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.antlr.v4.runtime.tree.TerminalNode;
import project.parser.RustParser.ArithmeticOrLogicalExpressionContext;
import project.parser.RustParser.CallExpressionContext;
import project.parser.RustParser.ComparisonExpressionContext;
import project.parser.RustParser.ComparisonOperatorContext;
import project.parser.RustParser.ExpressionContext;
import project.parser.RustParser.GroupedExpressionContext;
import project.parser.RustParser.IdentifierContext;
import project.parser.RustParser.LazyBooleanExpressionContext;
import project.parser.RustParser.LiteralExpressionContext;
import project.parser.RustParser.LiteralExpression_Context;
import project.parser.RustParser.NegationExpressionContext;
import project.parser.RustParser.PathExprSegmentContext;
import project.parser.RustParser.PathExpression_Context;
import project.parser.RustParser.PathInExpressionContext;

/**
 * Builds {@link Expression} nodes from expression parse-tree contexts. Expressions are leaves of
 * the supported fragment: none of them contain blocks or statements, so this builder never calls
 * back into the statement or item builders.
 */
final class ExpressionBuilder {

  private final SpanRecorder spans;

  private final Set<Identifier> declaredFunctions = new HashSet<>();

  /**
   * Creates an expression builder that records node spans through the given recorder.
   *
   * @param spans the recorder shared across the whole parse-tree walk
   */
  ExpressionBuilder(SpanRecorder spans) {
    this.spans = Objects.requireNonNull(spans);
  }

  /**
   * Records a function declared in the crate, so a call to it resolves while calls to undeclared
   * names are rejected as out of scope. Populated by a pre-pass over all items before any body is
   * built, so calls to functions defined later in the crate still resolve.
   *
   * @param name the declared function's name
   */
  void addFunctionIdentifier(Identifier name) {
    this.declaredFunctions.add(Objects.requireNonNull(name));
  }

  /**
   * Dispatches an expression parse-tree context to the appropriate builder method.
   *
   * @param ctx the expression context
   * @return the corresponding {@link Expression} node
   * @throws UnsupportedConstructException if the expression kind is not supported
   */
  Expression buildExpression(ExpressionContext ctx) {
    return switch (ctx) {
      case LiteralExpression_Context c -> buildLiteral(c);
      case ArithmeticOrLogicalExpressionContext c -> buildArithmeticExpression(c);
      case ComparisonExpressionContext c -> buildComparisonExpression(c);
      case LazyBooleanExpressionContext c -> buildLazyBooleanExpression(c);
      case PathExpression_Context c -> buildVarExpression(c);
      case NegationExpressionContext c -> buildNegation(c);
      case GroupedExpressionContext c -> buildGrouped(c);
      case CallExpressionContext c -> buildCallExpression(c);
      default -> throw new UnsupportedConstructException(ctx, "Unsupported expression");
    };
  }

  /**
   * Builds a {@link Literal} from a literal-expression parse-tree context. Only integer and boolean
   * literals are supported.
   *
   * @param ctx the literal expression context
   * @return the corresponding {@link Literal} node
   * @throws UnsupportedConstructException if the literal is not an integer or boolean literal
   */
  Literal buildLiteral(LiteralExpression_Context ctx) {
    LiteralExpressionContext litExprCtx = ctx.literalExpression();
    TerminalNode intLitNode = litExprCtx.INTEGER_LITERAL();
    if (intLitNode != null) {
      String intLitText = intLitNode.getText();
      String s = intLitText.replaceFirst("(?:[iu](?:8|16|32|64|128|size))$", "").replace("_", "");
      int radix = 10;
      if (s.startsWith("0x")) {
        radix = 16;
        s = s.substring(2);
      } else if (s.startsWith("0o")) {
        radix = 8;
        s = s.substring(2);
      } else if (s.startsWith("0b")) {
        radix = 2;
        s = s.substring(2);
      }
      return spans.track(new IntegerLiteral(new BigInteger(s, radix)), ctx);
    }
    if (litExprCtx.KW_TRUE() != null) {
      return spans.track(new BooleanLiteral(true), ctx);
    }
    if (litExprCtx.KW_FALSE() != null) {
      return spans.track(new BooleanLiteral(false), ctx);
    }
    throw new UnsupportedConstructException(
        ctx, "Unsupported literal, only integer and boolean literals are supported");
  }

  /**
   * Builds a {@link BinaryOp} from an arithmetic expression parse-tree context. Only the five basic
   * arithmetic operators (+, -, *, /, %) are supported.
   *
   * @param ctx the arithmetic/logical expression context
   * @return the corresponding {@link BinaryOp} node
   * @throws UnsupportedConstructException if the operator is not a basic arithmetic operator
   */
  BinaryOp buildArithmeticExpression(ArithmeticOrLogicalExpressionContext ctx) {
    BinaryOp.Op op;
    if (ctx.STAR() != null) {
      op = BinaryOp.Op.MUL;
    } else if (ctx.PLUS() != null) {
      op = BinaryOp.Op.ADD;
    } else if (ctx.SLASH() != null) {
      op = BinaryOp.Op.DIV;
    } else if (ctx.MINUS() != null) {
      op = BinaryOp.Op.SUB;
    } else if (ctx.PERCENT() != null) {
      op = BinaryOp.Op.MOD;
    } else {
      throw new UnsupportedConstructException(
          ctx, "Only basic (+, -, *, /, %) arithmetic expressions supported.");
    }
    Expression left = buildExpression(ctx.expression().get(0));
    Expression right = buildExpression(ctx.expression().get(1));
    return spans.track(new BinaryOp(op, left, right), ctx);
  }

  /**
   * Builds a {@link BinaryOp} from a comparison-expression context. Supports the six comparison
   * operators (==, !=, &gt;, &lt;, &gt;=, &lt;=).
   *
   * @param ctx the comparison-expression context
   * @return the corresponding {@link BinaryOp} node
   * @throws UnsupportedConstructException if the operator is not a recognised comparison operator
   */
  BinaryOp buildComparisonExpression(ComparisonExpressionContext ctx) {
    ComparisonOperatorContext operatorCtx = ctx.comparisonOperator();
    BinaryOp.Op op;
    if (operatorCtx.EQEQ() != null) {
      op = BinaryOp.Op.EQ;
    } else if (operatorCtx.NE() != null) {
      op = BinaryOp.Op.NE;
    } else if (operatorCtx.GT() != null) {
      op = BinaryOp.Op.GT;
    } else if (operatorCtx.LT() != null) {
      op = BinaryOp.Op.LT;
    } else if (operatorCtx.GE() != null) {
      op = BinaryOp.Op.GE;
    } else if (operatorCtx.LE() != null) {
      op = BinaryOp.Op.LE;
    } else {
      throw new UnsupportedConstructException(ctx, "Unsupported comparison operator");
    }
    Expression left = buildExpression(ctx.expression().get(0));
    Expression right = buildExpression(ctx.expression().get(1));
    return spans.track(new BinaryOp(op, left, right), ctx);
  }

  /**
   * Builds a {@link BinaryOp} from a lazy-boolean-expression context ({@code &&} or {@code ||}).
   *
   * <p>The translator encodes these eagerly, as {@code ∧}/{@code ∨} inside the rule constraint, so
   * Rust's short-circuit evaluation of the right operand is not preserved. That is only observable
   * when the right operand can panic or diverge: a division, a remainder, or a function call. Those
   * are rejected here so the translation never silently changes panic or termination behaviour. The
   * left operand is always evaluated in Rust, so it may contain them freely.
   *
   * @param ctx the lazy-boolean-expression context
   * @return the corresponding {@link BinaryOp} node
   * @throws UnsupportedConstructException if the right operand contains a division, remainder, or
   *     function call
   */
  BinaryOp buildLazyBooleanExpression(LazyBooleanExpressionContext ctx) {
    BinaryOp.Op op = ctx.ANDAND() != null ? BinaryOp.Op.AND : BinaryOp.Op.OR;
    Expression left = buildExpression(ctx.expression().get(0));
    Expression right = buildExpression(ctx.expression().get(1));
    if (containsDivisionOrCall(right)) {
      throw new UnsupportedConstructException(
          ctx,
          "Division, remainder, and function calls to the right of `&&`/`||` are not supported:"
              + " the eager encoding would lose Rust's short-circuit semantics");
    }
    return spans.track(new BinaryOp(op, left, right), ctx);
  }

  /** Whether the expression contains a {@code /}, {@code %}, or function call anywhere. */
  private static boolean containsDivisionOrCall(Expression e) {
    return switch (e) {
      case FunctionCall c -> true;
      case BinaryOp b ->
          b.operator() == BinaryOp.Op.DIV
              || b.operator() == BinaryOp.Op.MOD
              || containsDivisionOrCall(b.left())
              || containsDivisionOrCall(b.right());
      case UnaryOp u -> containsDivisionOrCall(u.operand());
      default -> false;
    };
  }

  /**
   * Builds a {@link Variable} from a path expression context. Only single-segment paths with no
   * generic arguments are supported (i.e. a plain variable name).
   *
   * @param ctx the path expression context
   * @return the corresponding {@link Variable} node
   * @throws UnsupportedConstructException if the path is not a simple identifier reference
   */
  Variable buildVarExpression(PathExpression_Context ctx) {
    return spans.track(new Variable(extractPathIdentifier(ctx)), ctx);
  }

  /**
   * Builds an expression from a unary negation context: arithmetic negation ({@code -e}) or boolean
   * not ({@code !e}), which share this grammar rule.
   *
   * <p>A negated integer literal folds into a single negative {@link IntegerLiteral}; any other
   * operand becomes a {@link UnaryMinus} node. Boolean {@code not} likewise negates the boolean
   * literal and otherwise becomes a {@link UnaryNot} node.
   *
   * @param ctx the negation-expression context
   * @return the corresponding {@link Expression} node
   */
  Expression buildNegation(NegationExpressionContext ctx) {
    Expression operand = buildExpression(ctx.expression());
    if (ctx.MINUS() == null) {
      // Boolean not (!e). Input is rustc-valid (CLAUDE.md invariant 5), so the operand is bool.
      if (operand instanceof BooleanLiteral literal) {
        return spans.track(new BooleanLiteral(!literal.value()), ctx);
      }
      return spans.track(new UnaryNot(operand), ctx);
    }
    if (operand instanceof IntegerLiteral literal) {
      return spans.track(new IntegerLiteral(literal.value().negate()), ctx);
    }
    return spans.track(new UnaryMinus(operand), ctx);
  }

  /**
   * Builds an expression from a parenthesised group. Parentheses carry no semantics of their own —
   * operator precedence is already resolved in the parse tree — so the group is its inner
   * expression with no wrapping AST node. Inner attributes ({@code (#![...] e)}) are out of scope.
   *
   * @param ctx the grouped-expression context
   * @return the {@link Expression} node for the inner expression
   * @throws UnsupportedConstructException if the group carries inner attributes
   */
  Expression buildGrouped(GroupedExpressionContext ctx) {
    if (!ctx.innerAttribute().isEmpty()) {
      throw new UnsupportedConstructException(ctx, "Inner attributes are not supported");
    }
    return buildExpression(ctx.expression());
  }

  private FunctionCall buildCallExpression(CallExpressionContext c) {
    if (!(c.expression() instanceof PathExpression_Context pathCtx)) {
      throw new UnsupportedConstructException(
          c, "CallExpressionContext should be PathExpression_Context");
    }
    Identifier callee = extractPathIdentifier(pathCtx);
    if (!declaredFunctions.contains(callee)) {
      throw new UnsupportedConstructException(
          c, "Function call to non-local function " + callee + " is not supported");
    }
    List<Expression> params = new ArrayList<>();
    if (c.callParams() != null) {
      for (ExpressionContext exprCtx : c.callParams().expression()) {
        params.add(buildExpression(exprCtx));
      }
    }
    return new FunctionCall(callee, params);
  }

  /**
   * Extracts a simple variable name from a path expression context. Only single-segment paths with
   * no generic arguments are supported (i.e. a plain identifier). Exposed for the statement
   * builder, which reuses it to read an assignment target.
   *
   * @param ctx the path expression context
   * @return the {@link Identifier} named by the path
   * @throws UnsupportedConstructException if the path is not a simple identifier reference
   */
  Identifier extractPathIdentifier(PathExpression_Context ctx) {
    PathInExpressionContext path = ctx.pathExpression().pathInExpression();
    if (path == null || path.pathExprSegment().size() != 1 || !path.PATHSEP().isEmpty()) {
      throw new UnsupportedConstructException(ctx, "Only simple variable references are supported");
    }
    PathExprSegmentContext segment = path.pathExprSegment().get(0);
    if (segment.genericArgs() != null) {
      throw new UnsupportedConstructException(ctx, "Generic arguments in paths are not supported");
    }
    IdentifierContext identifier = segment.pathIdentSegment().identifier();
    if (identifier == null) {
      throw new UnsupportedConstructException(ctx, "Only simple variable references are supported");
    }
    return new Identifier(identifier.getText());
  }
}
