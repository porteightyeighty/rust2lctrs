package project.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.antlr.v4.runtime.ParserRuleContext;
import project.parser.RustParser;
import project.parser.RustParser.BlockExpressionContext;

/**
 * Converts an ANTLR parse tree into the typed AST. Each {@code build*} method handles one grammar
 * rule and throws {@link UnsupportedConstructException} on the first construct outside the
 * supported Rust fragment.
 */
public final class AstBuilder {

  private final SpanTable spans;

  public AstBuilder(SpanTable spanTable) {
    this.spans = Objects.requireNonNull(spanTable);
  }

  /**
   * Builds the root {@link Crate} from a crate parse-tree context.
   *
   * @param ctx the top-level crate context produced by the parser
   * @return the corresponding {@link Crate} node
   */
  public Crate buildCrate(RustParser.CrateContext ctx) {
    List<Item> items = new ArrayList<>();
    for (var itemCtx : ctx.item()) {
      items.add(buildItem(itemCtx));
    }
    return track(new Crate(items), ctx);
  }

  /**
   * Builds an {@link Item} from an item parse-tree context. Only visible function items are
   * supported.
   *
   * @param ctx the item context
   * @return the corresponding {@link Item} node
   * @throws UnsupportedConstructException if the item is not a plain function definition
   */
  public Item buildItem(RustParser.ItemContext ctx) {
    RustParser.VisItemContext visItemContext = ctx.visItem();
    if (visItemContext == null) {
      throw new UnsupportedConstructException(ctx, "Unsupported Item");
    }
    RustParser.Function_Context functionContext = visItemContext.function_();
    if (functionContext == null) {
      throw new UnsupportedConstructException(ctx, "Only function definitions are supported");
    }
    return buildFunctionDeclaration(functionContext);
  }

  /**
   * Builds a {@link FunctionDeclaration} from a function parse-tree context. Qualifiers (async,
   * unsafe, const, extern), generics, and a missing return type are all rejected.
   *
   * @param ctx the function context
   * @return the corresponding {@link FunctionDeclaration} node
   * @throws UnsupportedConstructException if any unsupported feature is present
   */
  public FunctionDeclaration buildFunctionDeclaration(RustParser.Function_Context ctx) {
    RustParser.FunctionQualifiersContext functionQualifiersContext = ctx.functionQualifiers();
    if (functionQualifiersContext.KW_ASYNC() != null
        || functionQualifiersContext.KW_UNSAFE() != null
        || functionQualifiersContext.KW_CONST() != null
        || functionQualifiersContext.abi() != null
        || functionQualifiersContext.KW_EXTERN() != null) {
      throw new UnsupportedConstructException(ctx, "Function qualifiers not supported");
    }
    if (ctx.genericParams() != null) {
      throw new UnsupportedConstructException(ctx, "Generics are not supported");
    }
    RustParser.FunctionReturnTypeContext functionReturnTypeContext = ctx.functionReturnType();
    if (functionReturnTypeContext == null) {
      throw new UnsupportedConstructException(
          ctx, "Return type must be provided in a function definition");
    }
    RustParser.Type_Context typeContext = functionReturnTypeContext.type_();
    Type returnType = extractType(typeContext);
    RustParser.IdentifierContext identifierContext = ctx.identifier();
    Identifier id = new Identifier(identifierContext.getText());
    List<Parameter> functionParams = extractParameters(ctx.functionParameters());
    BodyBlock block = buildBodyBlock(ctx.blockExpression());
    return track(new FunctionDeclaration(id, functionParams, block, returnType), ctx);
  }

  /**
   * Builds a {@link BodyBlock} from a block-expression parse-tree context. The block must end with
   * an explicit {@link Return} statement; trailing expressions are rejected.
   *
   * @param ctx the block expression context
   * @return the corresponding {@link BodyBlock} node
   * @throws UnsupportedConstructException if the block has a trailing expression or does not end
   *     with a {@code return} statement
   */
  public BodyBlock buildBodyBlock(RustParser.BlockExpressionContext ctx) {
    List<Statement> built = extractStatementsFromBlock(ctx);
    if (!(built.getLast() instanceof Return tail)) {
      throw new UnsupportedConstructException(ctx, "Block must end with a return statement");
    }
    return track(new BodyBlock(built.subList(0, built.size() - 1), tail), ctx);
  }

  public Block buildBlock(BlockExpressionContext ctx) {
    List<Statement> built = extractStatementsFromBlock(ctx);
    return track(new Block(built), ctx);
  }

  /**
   * Builds a {@link Statement} from a statement parse-tree context. Only {@code let} and {@code
   * return} statements are supported.
   *
   * @param ctx the statement context
   * @return the corresponding {@link Statement} node
   * @throws UnsupportedConstructException if the statement is not a {@code let} or {@code return}
   */
  public Statement buildStatement(RustParser.StatementContext ctx) {
    if (ctx.SEMI() != null || ctx.item() != null || ctx.macroInvocationSemi() != null) {
      throw new UnsupportedConstructException(ctx, "Only let and return statements are supported");
    }
    if (ctx.expressionStatement() != null) {
      return buildReturnStatement(ctx.expressionStatement());
    }
    return buildLetStatement(ctx.letStatement());
  }

  /**
   * Builds a {@link Let} from a let-statement parse-tree context.
   *
   * @param ctx the let-statement context
   * @return the corresponding {@link Let} node
   */
  public Let buildLetStatement(RustParser.LetStatementContext ctx) {
    Identifier bindingTarget = extractLetBinding(ctx.patternNoTopAlt());
    if (ctx.type_() == null) {
      throw new UnsupportedConstructException(ctx, "let bindings must have an explicit type");
    }
    Type type = extractType(ctx.type_());
    Expression expr = buildExpression(ctx.expression());
    return track(new Let(bindingTarget, type, expr), ctx);
  }

  /**
   * Builds a {@link Return} from an expression-statement parse-tree context. Only {@code return}
   * expressions are supported as expression statements.
   *
   * @param ctx the expression statement context
   * @return the corresponding {@link Return} node
   * @throws UnsupportedConstructException if the expression statement is not a {@code return}
   */
  public Return buildReturnStatement(RustParser.ExpressionStatementContext ctx) {
    if (!(ctx.expression() instanceof RustParser.ReturnExpressionContext returnCtx)) {
      throw new UnsupportedConstructException(
          ctx, "Only return expressions are supported as expression statements");
    }
    if (returnCtx.expression() == null) {
      throw new UnsupportedConstructException(ctx, "Return statement must have an expression");
    }
    return track(new Return(buildExpression(returnCtx.expression())), ctx);
  }

  public If buildIfStatement(RustParser.IfExpressionContext ctx) {
    if (ctx.ifLetExpression() != null) {
      throw new UnsupportedConstructException(ctx, "If-let expressions are not supported");
    }
    Expression condition = buildExpression(ctx.expression());
    Block thenBlock = buildBlock(ctx.blockExpression(0));
    BlockExpressionContext elseBlockContext = ctx.blockExpression(1);
    Optional<Block> elseBlock = Optional.empty();
    if (elseBlockContext != null) {
      elseBlock = Optional.of(buildBlock(elseBlockContext));
    } else if (ctx.ifExpression() != null) {
      elseBlock = Optional.of(new Block(List.of(buildIfStatement(ctx.ifExpression()))));
    }
    return track(new If(condition, thenBlock, elseBlock), ctx);
  }

  public Assignment buildAssignment(RustParser.AssignmentExpressionContext ctx) {
    if (!(ctx.expression(0) instanceof RustParser.PathExpression_Context targetCtx)) {
      throw new UnsupportedConstructException(
          ctx.expression(0), "Assignment target must be a simple variable");
    }
    Identifier target = extractPathIdentifier(targetCtx);
    Expression value = buildExpression(ctx.expression(1));
    return track(new Assignment(target, value), ctx);
  }

  /**
   * Dispatches an expression parse-tree context to the appropriate builder method.
   *
   * @param ctx the expression context
   * @return the corresponding {@link Expression} node
   * @throws UnsupportedConstructException if the expression kind is not supported
   */
  public Expression buildExpression(RustParser.ExpressionContext ctx) {
    return switch (ctx) {
      case RustParser.LiteralExpression_Context c -> buildLiteral(c);
      case RustParser.ArithmeticOrLogicalExpressionContext c -> buildArithmeticExpression(c);
      case RustParser.ComparisonExpressionContext c -> buildComparisonExpression(c);
      case RustParser.PathExpression_Context c -> buildVarExpression(c);
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
  public Literal buildLiteral(RustParser.LiteralExpression_Context ctx) {
    RustParser.LiteralExpressionContext litExprCtx = ctx.literalExpression();
    if (litExprCtx.INTEGER_LITERAL() != null) {
      return track(new Integer(Long.parseLong(litExprCtx.INTEGER_LITERAL().getText())), ctx);
    }
    if (litExprCtx.KW_TRUE() != null) {
      return track(new Boolean(true), ctx);
    }
    if (litExprCtx.KW_FALSE() != null) {
      return track(new Boolean(false), ctx);
    }
    throw new UnsupportedConstructException(
        ctx, "Unsupported literal, only integer and boolean literals are supported");
  }

  /**
   * Builds a {@link Variable} from a path expression context. Only single-segment paths with no
   * generic arguments are supported (i.e. a plain variable name).
   *
   * @param ctx the path expression context
   * @return the corresponding {@link Variable} node
   * @throws UnsupportedConstructException if the path is not a simple identifier reference
   */
  public Variable buildVarExpression(RustParser.PathExpression_Context ctx) {
    return track(new Variable(extractPathIdentifier(ctx)), ctx);
  }

  /**
   * Extracts a simple variable name from a path expression context. Only single-segment paths with
   * no generic arguments are supported (i.e. a plain identifier).
   *
   * @param ctx the path expression context
   * @return the {@link Identifier} named by the path
   * @throws UnsupportedConstructException if the path is not a simple identifier reference
   */
  private Identifier extractPathIdentifier(RustParser.PathExpression_Context ctx) {
    RustParser.PathInExpressionContext path = ctx.pathExpression().pathInExpression();
    if (path == null || path.pathExprSegment().size() != 1 || !path.PATHSEP().isEmpty()) {
      throw new UnsupportedConstructException(ctx, "Only simple variable references are supported");
    }
    RustParser.PathExprSegmentContext segment = path.pathExprSegment().get(0);
    if (segment.genericArgs() != null) {
      throw new UnsupportedConstructException(ctx, "Generic arguments in paths are not supported");
    }
    RustParser.IdentifierContext identifier = segment.pathIdentSegment().identifier();
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
  public BinaryOp buildArithmeticExpression(RustParser.ArithmeticOrLogicalExpressionContext ctx) {
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
    return track(new BinaryOp(op, left, right), ctx);
  }

  public BinaryOp buildComparisonExpression(RustParser.ComparisonExpressionContext ctx) {
    RustParser.ComparisonOperatorContext operatorCtx = ctx.comparisonOperator();
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
    return track(new BinaryOp(op, left, right), ctx);
  }

  private Type extractType(RustParser.Type_Context ctx) {
    String typeText = ctx.getText();
    return switch (typeText) {
      case "i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64", "u128" ->
          Type.Int.valueOf(typeText);
      case "bool" -> Type.BOOL;
      default -> throw new UnsupportedConstructException(ctx, "Unsupported type: " + typeText);
    };
  }

  private List<Parameter> extractParameters(RustParser.FunctionParametersContext ctx) {
    List<Parameter> builtParams = new ArrayList<>();
    if (ctx == null) {
      return builtParams;
    }

    if (ctx.selfParam() != null) {
      throw new UnsupportedConstructException(ctx, "Self parameters are not supported");
    }
    for (RustParser.FunctionParamContext param : ctx.functionParam()) {
      if (param.outerAttribute().size() > 0) {
        throw new UnsupportedConstructException(
            param, "Function parameter outer attributes are not supported");
      }
      if (param.DOTDOTDOT() != null) {
        throw new UnsupportedConstructException(param, "Varargs are not supported");
      }
      RustParser.FunctionParamPatternContext paramPattern = param.functionParamPattern();
      if (paramPattern == null) {
        throw new UnsupportedConstructException(param, "Unnamed parameters are not supported");
      }
      if (paramPattern.type_() == null) {
        throw new UnsupportedConstructException(
            param, "Parameters must have an explicit type");
      }
      Type type = extractType(paramPattern.type_());
      Identifier identifier = extractLetBinding(paramPattern.pattern().patternNoTopAlt().get(0));
      builtParams.add(new Parameter(identifier, type));
    }
    return builtParams;
  }

  private Identifier extractLetBinding(RustParser.PatternNoTopAltContext ctx) {
    RustParser.PatternWithoutRangeContext pattern = ctx.patternWithoutRange();
    if (pattern == null) {
      throw new UnsupportedConstructException(ctx, "Only simple bindings are supported");
    }
    RustParser.IdentifierPatternContext idPattern = pattern.identifierPattern();
    if (idPattern == null || idPattern.KW_REF() != null || idPattern.pattern() != null) {
      throw new UnsupportedConstructException(idPattern, "Only simple bindings are supported");
    }
    return new Identifier(idPattern.identifier().getText());
  }

  private List<Statement> extractStatementsFromBlock(RustParser.BlockExpressionContext ctx) {
    RustParser.StatementsContext statementsCtx = ctx.statements();
    if (statementsCtx == null || statementsCtx.statement().isEmpty()) {
      throw new UnsupportedConstructException(ctx, "Block must end with a return statement");
    }
    if (statementsCtx.expression() != null) {
      throw new UnsupportedConstructException(
          statementsCtx.expression(),
          "Trailing expressions are not supported; use an explicit return statement");
    }
    List<Statement> built = new ArrayList<>();
    for (RustParser.StatementContext statementCtx : statementsCtx.statement()) {
      built.add(buildStatement(statementCtx));
    }
    return built;
  }

  private <T extends Node> T track(T node, ParserRuleContext ctx) {
    Objects.requireNonNull(node, "build method returned null: bug in AstBuilder");
    spans.put(node, Span.of(ctx));
    return node;
  }
}
