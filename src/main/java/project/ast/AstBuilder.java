package project.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.antlr.v4.runtime.ParserRuleContext;
import project.parser.RustParser.ArithmeticOrLogicalExpressionContext;
import project.parser.RustParser.AssignmentExpressionContext;
import project.parser.RustParser.BlockExpressionContext;
import project.parser.RustParser.BreakExpressionContext;
import project.parser.RustParser.ComparisonExpressionContext;
import project.parser.RustParser.ComparisonOperatorContext;
import project.parser.RustParser.ContinueExpressionContext;
import project.parser.RustParser.CrateContext;
import project.parser.RustParser.ExpressionContext;
import project.parser.RustParser.ExpressionStatementContext;
import project.parser.RustParser.ExpressionWithBlockContext;
import project.parser.RustParser.ExpressionWithBlock_Context;
import project.parser.RustParser.FunctionParamContext;
import project.parser.RustParser.FunctionParamPatternContext;
import project.parser.RustParser.FunctionParametersContext;
import project.parser.RustParser.FunctionQualifiersContext;
import project.parser.RustParser.FunctionReturnTypeContext;
import project.parser.RustParser.Function_Context;
import project.parser.RustParser.IdentifierContext;
import project.parser.RustParser.IdentifierPatternContext;
import project.parser.RustParser.IfExpressionContext;
import project.parser.RustParser.InfiniteLoopExpressionContext;
import project.parser.RustParser.ItemContext;
import project.parser.RustParser.LetStatementContext;
import project.parser.RustParser.LiteralExpressionContext;
import project.parser.RustParser.LiteralExpression_Context;
import project.parser.RustParser.LoopExpressionContext;
import project.parser.RustParser.PathExprSegmentContext;
import project.parser.RustParser.PathExpression_Context;
import project.parser.RustParser.PathInExpressionContext;
import project.parser.RustParser.PatternNoTopAltContext;
import project.parser.RustParser.PatternWithoutRangeContext;
import project.parser.RustParser.PredicateLoopExpressionContext;
import project.parser.RustParser.ReturnExpressionContext;
import project.parser.RustParser.StatementContext;
import project.parser.RustParser.StatementsContext;
import project.parser.RustParser.Type_Context;
import project.parser.RustParser.VisItemContext;

/**
 * Converts an ANTLR parse tree into the typed AST. Each {@code build*} method handles one grammar
 * rule and throws {@link UnsupportedConstructException} on the first construct outside the
 * supported Rust fragment.
 */
public final class AstBuilder {

  private final SpanTable spans;

  /**
   * Creates a builder that records each node's source position in the given span table.
   *
   * @param spanTable the table that source spans are written to as nodes are built
   */
  public AstBuilder(SpanTable spanTable) {
    this.spans = Objects.requireNonNull(spanTable);
  }

  /**
   * Builds the root {@link Crate} from a crate parse-tree context.
   *
   * @param ctx the top-level crate context produced by the parser
   * @return the corresponding {@link Crate} node
   */
  public Crate buildCrate(CrateContext ctx) {
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
  public Item buildItem(ItemContext ctx) {
    VisItemContext visItemContext = ctx.visItem();
    if (visItemContext == null) {
      throw new UnsupportedConstructException(ctx, "Unsupported Item");
    }
    Function_Context functionContext = visItemContext.function_();
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
  public FunctionDeclaration buildFunctionDeclaration(Function_Context ctx) {
    FunctionQualifiersContext functionQualifiersContext = ctx.functionQualifiers();
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
    FunctionReturnTypeContext functionReturnTypeContext = ctx.functionReturnType();
    if (functionReturnTypeContext == null) {
      throw new UnsupportedConstructException(
          ctx, "Return type must be provided in a function definition");
    }
    Type_Context typeContext = functionReturnTypeContext.type_();
    Type returnType = extractType(typeContext);
    IdentifierContext identifierContext = ctx.identifier();
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
  public BodyBlock buildBodyBlock(BlockExpressionContext ctx) {
    List<Statement> built = extractStatementsFromBlock(ctx);
    if (built.isEmpty() || !(built.getLast() instanceof Return tail)) {
      throw new UnsupportedConstructException(ctx, "Block must end with a return statement");
    }
    return track(new BodyBlock(built.subList(0, built.size() - 1), tail), ctx);
  }

  /**
   * Builds a {@link Block} from a block-expression parse-tree context. Unlike {@link
   * #buildBodyBlock}, the block may be empty and need not end with a {@code return}; trailing
   * expressions are still rejected.
   *
   * @param ctx the block expression context
   * @return the corresponding {@link Block} node
   * @throws UnsupportedConstructException if the block has a trailing expression
   */
  public Block buildBlock(BlockExpressionContext ctx) {
    List<Statement> built = extractStatementsFromBlock(ctx);
    return track(new Block(built), ctx);
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
  public Statement buildStatement(StatementContext ctx) {
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
  public Break buildBreakStatement(BreakExpressionContext ctx) {
    if (ctx.LIFETIME_OR_LABEL() != null || ctx.expression() != null) {
      throw new UnsupportedConstructException(
          ctx, "Break statement with lifetimes, labels, or values are not supported");
    }
    return track(new Break(), ctx);
  }

  /**
   * Builds a {@link Continue} from a continue-expression context. Labelled continues and {@code
   * continue} with a value are rejected.
   *
   * @param ctx the continue-expression context
   * @return the corresponding {@link Continue} node
   * @throws UnsupportedConstructException if the continue carries a label or a value
   */
  public Continue buildContinueStatement(ContinueExpressionContext ctx) {
    if (ctx.LIFETIME_OR_LABEL() != null || ctx.expression() != null) {
      throw new UnsupportedConstructException(
          ctx, "Continue statement with lifetimes, labels, or values are not supported");
    }
    return track(new Continue(), ctx);
  }

  /**
   * Builds a {@link Let} from a let-statement parse-tree context.
   *
   * @param ctx the let-statement context
   * @return the corresponding {@link Let} node
   */
  public Let buildLetStatement(LetStatementContext ctx) {
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
  public Return buildReturnStatement(ReturnExpressionContext ctx) {
    if (ctx.expression() == null) {
      throw new UnsupportedConstructException(ctx, "Return statement must have an expression");
    }
    return track(new Return(buildExpression(ctx.expression())), ctx);
  }

  /**
   * Builds a {@link While} from a predicate-loop context.
   *
   * @param ctx the predicate-loop (`while`) context
   * @return the corresponding {@link While} node
   */
  public While buildWhileStatement(PredicateLoopExpressionContext ctx) {
    Expression condition = buildExpression(ctx.expression());
    Block body = buildBlock(ctx.blockExpression());
    return track(new While(condition, body), ctx);
  }

  /**
   * Builds a {@link Loop} from an infinite-loop context.
   *
   * @param ctx the infinite-loop (`loop`) context
   * @return the corresponding {@link Loop} node
   */
  public Loop buildLoopExpression(InfiniteLoopExpressionContext ctx) {
    Block body = buildBlock(ctx.blockExpression());
    return track(new Loop(body), ctx);
  }

  /**
   * Builds an {@link If} from an if-expression context. {@code if let} expressions are rejected; an
   * {@code else if} chain is built recursively as a nested {@link If} wrapped in the else block.
   *
   * @param ctx the if-expression context
   * @return the corresponding {@link If} node
   * @throws UnsupportedConstructException if the expression is an {@code if let}
   */
  public If buildIfStatement(IfExpressionContext ctx) {
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

  /**
   * Builds an {@link Assignment} from an assignment-expression context. The assignment target must
   * be a simple variable (a path expression).
   *
   * @param ctx the assignment-expression context
   * @return the corresponding {@link Assignment} node
   * @throws UnsupportedConstructException if the target is not a simple variable
   */
  public Assignment buildAssignment(AssignmentExpressionContext ctx) {
    if (!(ctx.expression(0) instanceof PathExpression_Context targetCtx)) {
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
  public Expression buildExpression(ExpressionContext ctx) {
    return switch (ctx) {
      case LiteralExpression_Context c -> buildLiteral(c);
      case ArithmeticOrLogicalExpressionContext c -> buildArithmeticExpression(c);
      case ComparisonExpressionContext c -> buildComparisonExpression(c);
      case PathExpression_Context c -> buildVarExpression(c);
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
  public Literal buildLiteral(LiteralExpression_Context ctx) {
    LiteralExpressionContext litExprCtx = ctx.literalExpression();
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
  public Variable buildVarExpression(PathExpression_Context ctx) {
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
  private Identifier extractPathIdentifier(PathExpression_Context ctx) {
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
  public BinaryOp buildArithmeticExpression(ArithmeticOrLogicalExpressionContext ctx) {
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

  /**
   * Builds a {@link BinaryOp} from a comparison-expression context. Supports the six comparison
   * operators (==, !=, &gt;, &lt;, &gt;=, &lt;=).
   *
   * @param ctx the comparison-expression context
   * @return the corresponding {@link BinaryOp} node
   * @throws UnsupportedConstructException if the operator is not a recognised comparison operator
   */
  public BinaryOp buildComparisonExpression(ComparisonExpressionContext ctx) {
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
    return track(new BinaryOp(op, left, right), ctx);
  }

  /**
   * Extracts a {@link Type} from a type context. Only the primitive integer sorts and {@code bool}
   * are supported.
   *
   * @param ctx the type context
   * @return the corresponding {@link Type}
   * @throws UnsupportedConstructException if the type is not a supported primitive
   */
  private Type extractType(Type_Context ctx) {
    String typeText = ctx.getText();
    return switch (typeText) {
      case "i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64", "u128" ->
          Type.Int.valueOf(typeText);
      case "bool" -> Type.BOOL;
      default -> throw new UnsupportedConstructException(ctx, "Unsupported type: " + typeText);
    };
  }

  /**
   * Extracts the {@link Parameter} list from a function-parameters context. {@code self}
   * parameters, varargs, parameter attributes, and unnamed or untyped parameters are all rejected.
   *
   * @param ctx the function-parameters context, or {@code null} for a parameterless function
   * @return the list of parameters, empty if {@code ctx} is {@code null}
   * @throws UnsupportedConstructException if any parameter is unsupported
   */
  private List<Parameter> extractParameters(FunctionParametersContext ctx) {
    List<Parameter> builtParams = new ArrayList<>();
    if (ctx == null) {
      return builtParams;
    }

    if (ctx.selfParam() != null) {
      throw new UnsupportedConstructException(ctx, "Self parameters are not supported");
    }
    for (FunctionParamContext param : ctx.functionParam()) {
      if (param.outerAttribute().size() > 0) {
        throw new UnsupportedConstructException(
            param, "Function parameter outer attributes are not supported");
      }
      if (param.DOTDOTDOT() != null) {
        throw new UnsupportedConstructException(param, "Varargs are not supported");
      }
      FunctionParamPatternContext paramPattern = param.functionParamPattern();
      if (paramPattern == null) {
        throw new UnsupportedConstructException(param, "Unnamed parameters are not supported");
      }
      if (paramPattern.type_() == null) {
        throw new UnsupportedConstructException(param, "Parameters must have an explicit type");
      }
      Type type = extractType(paramPattern.type_());
      Identifier identifier = extractLetBinding(paramPattern.pattern().patternNoTopAlt().get(0));
      builtParams.add(new Parameter(identifier, type));
    }
    return builtParams;
  }

  /**
   * Extracts the bound {@link Identifier} from a pattern context. Only simple identifier patterns
   * are supported; {@code ref} bindings and sub-patterns are rejected.
   *
   * @param ctx the pattern context
   * @return the {@link Identifier} bound by the pattern
   * @throws UnsupportedConstructException if the pattern is not a simple identifier binding
   */
  private Identifier extractLetBinding(PatternNoTopAltContext ctx) {
    PatternWithoutRangeContext pattern = ctx.patternWithoutRange();
    if (pattern == null) {
      throw new UnsupportedConstructException(ctx, "Only simple bindings are supported");
    }
    IdentifierPatternContext idPattern = pattern.identifierPattern();
    if (idPattern == null || idPattern.KW_REF() != null || idPattern.pattern() != null) {
      throw new UnsupportedConstructException(idPattern, "Only simple bindings are supported");
    }
    return new Identifier(idPattern.identifier().getText());
  }

  /**
   * Extracts and builds the {@link Statement} list from a block-expression context. An empty block
   * yields an empty list; a trailing expression (a block whose final element is an expression
   * rather than a statement) is rejected.
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
    if (statementsCtx.expression() != null) {
      throw new UnsupportedConstructException(
          statementsCtx.expression(),
          "Trailing expressions are not supported; use an explicit return statement");
    }
    List<Statement> built = new ArrayList<>();
    for (StatementContext statementCtx : statementsCtx.statement()) {
      built.add(buildStatement(statementCtx));
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

  /**
   * Records the node's source span in the span table and returns the node unchanged, so calls can
   * be inlined into {@code return} statements.
   *
   * @param node the freshly built AST node
   * @param ctx the parse-tree context the node was built from
   * @param <T> the node type
   * @return the same {@code node}, after its span has been recorded
   */
  private <T extends Node> T track(T node, ParserRuleContext ctx) {
    Objects.requireNonNull(node, "build method returned null: bug in AstBuilder");
    spans.put(node, Span.of(ctx));
    return node;
  }
}
