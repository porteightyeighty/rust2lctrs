package project.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import project.parser.RustParser;

public final class AstBuilder {

  public Crate buildCrate(RustParser.CrateContext ctx) {
    List<Item> items = new ArrayList<>();
    for (var itemCtx : ctx.item()) {
      items.add(buildItem(itemCtx));
    }
    return new Crate(items);
  }

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
    return new FunctionDef(id, functionParams, block, returnType);
  }

  public Block buildBlock(RustParser.BlockExpressionContext ctx) {
    RustParser.StatementsContext statementsCtx = ctx.statements();
    if (statementsCtx == null) {
      return new Block(new ArrayList<Stmt>(), Optional.empty());
    }
    List<Stmt> statements = new ArrayList<>();
    Optional<Expr> trailingExpression = Optional.empty();
    if (statementsCtx.expression() != null) {
      trailingExpression = Optional.of(buildExpression(statementsCtx.expression()));
    }
    for (RustParser.StatementContext statementCtx : statementsCtx.statement()) {
      statements.add(buildStatement(statementCtx));
    }
    return new Block(statements, trailingExpression);
  }

  public Stmt buildStatement(RustParser.StatementContext ctx) {
    if (ctx.SEMI() != null
        | ctx.item() != null
        | ctx.expressionStatement() != null
        | ctx.macroInvocationSemi() != null) {
      throw new UnsupportedConstructException(ctx, "Only let statements are supported");
    }
    return buildLetStatement(ctx.letStatement());
  }

  public IntLit buildIntegerLiteralExpression(RustParser.LiteralExpression_Context ctx) {
    RustParser.LiteralExpressionContext litExprCtx = ctx.literalExpression();
    if (litExprCtx == null || litExprCtx.INTEGER_LITERAL() == null) {
      throw new UnsupportedConstructException(ctx, "Unsupported literal expression");
    }
    return new IntLit(Long.parseLong(litExprCtx.INTEGER_LITERAL().getText()));
  }

  public Expr buildExpression(RustParser.ExpressionContext ctx) {
    return switch (ctx) {
      case RustParser.LiteralExpression_Context c -> buildIntegerLiteralExpression(c);
      case RustParser.ArithmeticOrLogicalExpressionContext c -> buildBinaryOperatorExpression(c);
      case RustParser.PathExpression_Context c -> buildVarExpression(c);
      default -> throw new UnsupportedConstructException(ctx, "Unsupported expression");
    };
  }

  private Var buildVarExpression(RustParser.PathExpression_Context ctx) {
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
    return new Var(new Identifier(identifier.getText()));
  }

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
          ctx, "Only basic (+, -, *, /) arithmetic expressions support.");
    }
    Expr left = (Expr) buildExpression(ctx.expression().get(0));
    Expr right = (Expr) buildExpression(ctx.expression().get(1));
    return new BinOp(op, left, right);
  }

  public Let buildLetStatement(RustParser.LetStatementContext ctx) {
    Identifier bindingTarget = extractLetBinding(ctx.patternNoTopAlt());
    Type type = extractType(ctx.type_());
    Expr expr = (Expr) buildExpression(ctx.expression());
    return new Let(bindingTarget, type, expr);
  }

  private Type extractType(RustParser.Type_Context ctx) {
    String typeText = ctx.getText();
    return switch (typeText) {
      case "i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64", "u128" ->
          Type.Int.valueOf(typeText);
      default -> throw new UnsupportedConstructException(ctx, "Unsupport type: " + typeText);
    };
  }

  private List<Parameter> extractParameters(RustParser.FunctionParametersContext ctx) {

    if (ctx.selfParam() != null) {
      throw new UnsupportedConstructException(ctx, "Self parameters are not supported");
    }
    List<Parameter> builtParams = new ArrayList<>();
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
}
