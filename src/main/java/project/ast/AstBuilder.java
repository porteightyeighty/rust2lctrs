package project.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.antlr.v4.runtime.ParserRuleContext;
import project.parser.RustParser;

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
    return buildFunctionDefinition(functionContext);
  }

  /**
   * Builds a {@link FunctionDef} from a function parse-tree context. Qualifiers (async, unsafe,
   * const, extern), generics, and a missing return type are all rejected.
   *
   * @param ctx the function context
   * @return the corresponding {@link FunctionDef} node
   * @throws UnsupportedConstructException if any unsupported feature is present
   */
  public FunctionDef buildFunctionDefinition(RustParser.Function_Context ctx) {
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
    Block block = buildBlock(ctx.blockExpression());
    return track(new FunctionDef(id, functionParams, block, returnType), ctx);
  }

  /**
   * Builds a {@link Block} from a block-expression parse-tree context. The block must end with an
   * explicit {@link Return} statement; trailing expressions are rejected.
   *
   * @param ctx the block expression context
   * @return the corresponding {@link Block} node
   * @throws UnsupportedConstructException if the block has a trailing expression or does not end
   *     with a {@code return} statement
   */
  public Block buildBlock(RustParser.BlockExpressionContext ctx) {
    RustParser.StatementsContext statementsCtx = ctx.statements();
    List<Stmt> statements = new ArrayList<>();
    if (statementsCtx != null) {
      if (statementsCtx.expression() != null) {
        throw new UnsupportedConstructException(
            statementsCtx.expression(),
            "Trailing expressions are not supported; use an explicit return statement");
      }
      for (RustParser.StatementContext statementCtx : statementsCtx.statement()) {
        statements.add(buildStatement(statementCtx));
      }
    }
    if (statements.isEmpty() || !(statements.get(statements.size() - 1) instanceof Return)) {
      throw new UnsupportedConstructException(ctx, "Block must end with a return statement");
    }
    return track(new Block(statements), ctx);
  }

  /**
   * Builds a {@link Stmt} from a statement parse-tree context. Only {@code let} and {@code return}
   * statements are supported.
   *
   * @param ctx the statement context
   * @return the corresponding {@link Stmt} node
   * @throws UnsupportedConstructException if the statement is not a {@code let} or {@code return}
   */
  public Stmt buildStatement(RustParser.StatementContext ctx) {
    if (ctx.SEMI() != null || ctx.item() != null || ctx.macroInvocationSemi() != null) {
      throw new UnsupportedConstructException(ctx, "Only let and return statements are supported");
    }
    if (ctx.expressionStatement() != null) {
      return buildReturnStatement(ctx.expressionStatement());
    }
    return buildLetStatement(ctx.letStatement());
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
    return track(new Return(buildExpression(returnCtx.expression())), ctx);
  }

  /**
   * Builds an {@link IntLit} from a literal-expression parse-tree context. Only integer literals
   * are supported.
   *
   * @param ctx the literal expression context
   * @return the corresponding {@link IntLit} node
   * @throws UnsupportedConstructException if the literal is not an integer literal
   */
  public IntLit buildIntegerLiteralExpression(RustParser.LiteralExpression_Context ctx) {
    RustParser.LiteralExpressionContext litExprCtx = ctx.literalExpression();
    if (litExprCtx == null || litExprCtx.INTEGER_LITERAL() == null) {
      throw new UnsupportedConstructException(ctx, "Unsupported literal expression");
    }
    return track(new IntLit(Long.parseLong(litExprCtx.INTEGER_LITERAL().getText())), ctx);
  }

  /**
   * Dispatches an expression parse-tree context to the appropriate builder method.
   *
   * @param ctx the expression context
   * @return the corresponding {@link Expr} node
   * @throws UnsupportedConstructException if the expression kind is not supported
   */
  public Expr buildExpression(RustParser.ExpressionContext ctx) {
    return switch (ctx) {
      case RustParser.LiteralExpression_Context c -> buildIntegerLiteralExpression(c);
      case RustParser.ArithmeticOrLogicalExpressionContext c -> buildBinaryOperatorExpression(c);
      case RustParser.PathExpression_Context c -> buildVarExpression(c);
      default -> throw new UnsupportedConstructException(ctx, "Unsupported expression");
    };
  }

  /**
   * Builds a {@link Var} from a path expression context. Only single-segment paths with no generic
   * arguments are supported (i.e. a plain variable name).
   *
   * @param ctx the path expression context
   * @return the corresponding {@link Var} node
   * @throws UnsupportedConstructException if the path is not a simple identifier reference
   */
  public Var buildVarExpression(RustParser.PathExpression_Context ctx) {
    RustParser.PathInExpressionContext path = ctx.pathExpression().pathInExpression();
    if (path == null || path.pathExprSegment().size() != 1 || path.PATHSEP() != null) {
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
    return track(new Var(new Identifier(identifier.getText())), ctx);
  }

  /**
   * Builds a {@link BinOp} from an arithmetic-or-logical expression parse-tree context. Only the
   * four basic arithmetic operators (+, -, *, /) are supported.
   *
   * @param ctx the arithmetic/logical expression context
   * @return the corresponding {@link BinOp} node
   * @throws UnsupportedConstructException if the operator is not a basic arithmetic operator
   */
  public BinOp buildBinaryOperatorExpression(RustParser.ArithmeticOrLogicalExpressionContext ctx) {
    BinOp.Op op;
    if (ctx.STAR() != null) {
      op = BinOp.Op.MUL;
    } else if (ctx.PLUS() != null) {
      op = BinOp.Op.ADD;
    } else if (ctx.SLASH() != null) {
      op = BinOp.Op.DIV;
    } else if (ctx.MINUS() != null) {
      op = BinOp.Op.SUB;
    } else {
      throw new UnsupportedConstructException(
          ctx, "Only basic (+, -, *, /) arithmetic expressions supported.");
    }
    Expr left = buildExpression(ctx.expression().get(0));
    Expr right = buildExpression(ctx.expression().get(1));
    return track(new BinOp(op, left, right), ctx);
  }

  /**
   * Builds a {@link LetStmt} from a let-statement parse-tree context.
   *
   * @param ctx the let-statement context
   * @return the corresponding {@link LetStmt} node
   */
  public LetStmt buildLetStatement(RustParser.LetStatementContext ctx) {
    Identifier bindingTarget = extractLetBinding(ctx.patternNoTopAlt());
    Type type = extractType(ctx.type_());
    Expr expr = buildExpression(ctx.expression());
    return track(new LetStmt(bindingTarget, type, expr), ctx);
  }

  private Type extractType(RustParser.Type_Context ctx) {
    String typeText = ctx.getText();
    return switch (typeText) {
      case "i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64", "u128" ->
          Type.Int.valueOf(typeText);
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
            param, "Variadic type parameters are not supported");
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

  private <T extends Node> T track(T node, ParserRuleContext ctx) {
    Objects.requireNonNull(node, "build method returned null: bug in AstBuilder");
    spans.put(node, Span.of(ctx));
    return node;
  }
}
