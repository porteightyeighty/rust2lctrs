package project.ast;

import java.math.BigInteger;
import java.util.Objects;
import org.antlr.v4.runtime.tree.TerminalNode;
import project.parser.RustParser.ArithmeticOrLogicalExpressionContext;
import project.parser.RustParser.ComparisonExpressionContext;
import project.parser.RustParser.ComparisonOperatorContext;
import project.parser.RustParser.ExpressionContext;
import project.parser.RustParser.GroupedExpressionContext;
import project.parser.RustParser.IdentifierContext;
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

  /**
   * Creates an expression builder that records node spans through the given recorder.
   *
   * @param spans the recorder shared across the whole parse-tree walk
   */
  ExpressionBuilder(SpanRecorder spans) {
    this.spans = Objects.requireNonNull(spans);
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
      case PathExpression_Context c -> buildVarExpression(c);
      case NegationExpressionContext c -> buildNegation(c);
      case GroupedExpressionContext c -> buildGrouped(c);
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
   * Builds an expression from a unary negation context. Only arithmetic negation ({@code -e}) is
   * supported; boolean not ({@code !e}) shares this grammar rule but is not yet in the fragment.
   *
   * <p>There is no unary-minus theory symbol, so negation is desugared here rather than carried as
   * a dedicated AST node: a negated integer literal folds into a single negative {@link
   * IntegerLiteral}, and any other operand becomes {@code 0 - e} over the binary {@link
   * BinaryOp.Op#SUB}.
   *
   * @param ctx the negation-expression context
   * @return the corresponding {@link Expression} node
   * @throws UnsupportedConstructException if the operator is boolean not ({@code !})
   */
  Expression buildNegation(NegationExpressionContext ctx) {
    if (ctx.MINUS() == null) {
      throw new UnsupportedConstructException(ctx, "Boolean not (!) is not supported");
    }
    Expression operand = buildExpression(ctx.expression());
    if (operand instanceof IntegerLiteral literal) {
      return spans.track(new IntegerLiteral(literal.value().negate()), ctx);
    }
    IntegerLiteral zero = spans.track(new IntegerLiteral(BigInteger.ZERO), ctx);
    return spans.track(new BinaryOp(BinaryOp.Op.SUB, zero, operand), ctx);
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
}
