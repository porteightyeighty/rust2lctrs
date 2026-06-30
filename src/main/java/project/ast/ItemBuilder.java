package project.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import project.parser.RustParser.CrateContext;
import project.parser.RustParser.FunctionParamContext;
import project.parser.RustParser.FunctionParamPatternContext;
import project.parser.RustParser.FunctionParametersContext;
import project.parser.RustParser.FunctionQualifiersContext;
import project.parser.RustParser.FunctionReturnTypeContext;
import project.parser.RustParser.Function_Context;
import project.parser.RustParser.IdentifierContext;
import project.parser.RustParser.ItemContext;
import project.parser.RustParser.Type_Context;
import project.parser.RustParser.VisItemContext;

/**
 * Builds top-level {@link Item} nodes (the crate and its single function) from item parse-tree
 * contexts. Delegates the function body to a {@link StatementBuilder}.
 */
final class ItemBuilder {

  private final SpanRecorder spans;
  private final StatementBuilder statements;
  private final DiagnosticRecorder diagnostics;

  /**
   * Creates an item builder, self-wiring a {@link StatementBuilder} over the same recorder.
   *
   * @param spans the recorder shared across the whole parse-tree walk
   * @param diagnostics the recorder that out-of-scope-construct diagnostics are collected into
   */
  ItemBuilder(SpanRecorder spans, DiagnosticRecorder diagnostics) {
    this.spans = Objects.requireNonNull(spans);
    this.diagnostics = Objects.requireNonNull(diagnostics);
    this.statements = new StatementBuilder(spans, diagnostics);
  }

  /**
   * Builds the root {@link Crate} from a crate parse-tree context. Only a single top-level function
   * is supported.
   *
   * @param ctx the top-level crate context produced by the parser
   * @return the corresponding {@link Crate} node; an empty crate if more than one item is present
   */
  Crate buildCrate(CrateContext ctx) {
    for (var itemCtx : ctx.item()) {
      Optional<Function_Context> fn = functionOf(itemCtx);
      if (fn.isPresent()) {
        statements.addFunctionIdentifier(new Identifier(fn.get().identifier().getText()));
      }
    }
    List<Item> items = new ArrayList<>();
    for (var itemCtx : ctx.item()) {
      try {
        items.add(buildItem(itemCtx));
      } catch (UnsupportedConstructException e) {
        diagnostics.add(Diagnostic.of(e));
      }
    }
    return spans.track(new Crate(items), ctx);
  }

  /**
   * Returns the function context of an item, or {@code null} if the item is not a plain function.
   * Shared by the name pre-pass and {@link #buildItem}.
   *
   * @param ctx the item context
   * @return the function context, or {@code null}
   */
  private static Optional<Function_Context> functionOf(ItemContext ctx) {
    VisItemContext visItem = ctx.visItem();
    return visItem == null ? Optional.empty() : Optional.ofNullable(visItem.function_());
  }

  /**
   * Builds an {@link Item} from an item parse-tree context. Only visible function items are
   * supported.
   *
   * @param ctx the item context
   * @return the corresponding {@link Item} node
   * @throws UnsupportedConstructException if the item is not a plain function definition
   */
  Item buildItem(ItemContext ctx) {
    if (ctx.visItem() == null) {
      throw new UnsupportedConstructException(ctx, "Unsupported Item");
    }
    Optional<Function_Context> functionContext = functionOf(ctx);
    if (functionContext.isEmpty()) {
      throw new UnsupportedConstructException(ctx, "Only function definitions are supported");
    }
    return buildFunctionDeclaration(functionContext.get());
  }

  /**
   * Builds a {@link FunctionDeclaration} from a function parse-tree context. Qualifiers (async,
   * unsafe, const, extern), generics, and a missing return type are all rejected.
   *
   * @param ctx the function context
   * @return the corresponding {@link FunctionDeclaration} node
   * @throws UnsupportedConstructException if any unsupported feature is present
   */
  FunctionDeclaration buildFunctionDeclaration(Function_Context ctx) {
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
    Type returnType = TypeReader.read(typeContext);
    IdentifierContext identifierContext = ctx.identifier();
    Identifier id = new Identifier(identifierContext.getText());
    List<Parameter> functionParams = extractParameters(ctx.functionParameters());
    Block block = statements.buildBlock(ctx.blockExpression());
    return spans.track(new FunctionDeclaration(id, functionParams, block, returnType), ctx);
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
      try {
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
        Type type = TypeReader.read(paramPattern.type_());
        Identifier identifier =
            BindingReader.boundIdentifier(paramPattern.pattern().patternNoTopAlt().get(0));
        builtParams.add(new Parameter(identifier, type));
      } catch (UnsupportedConstructException e) {
        diagnostics.add(Diagnostic.of(e));
      }
    }
    return builtParams;
  }
}
