package project.ast;

import project.parser.RustParser;
import project.parser.RustParser.IdentifierPatternContext;
import project.parser.RustParser.PatternWithoutRangeContext;
import project.parser.RustParserBaseVisitor;

public final class AstBuilder extends RustParserBaseVisitor<Node> {

  @Override
  public Node visitLiteralExpression(RustParser.LiteralExpressionContext ctx) {
    if (ctx.INTEGER_LITERAL() != null) {
      return new IntLit(Long.parseLong(ctx.INTEGER_LITERAL().getText()));
    }
    throw new UnsupportedConstructException(ctx);
  }

  @Override
  public Node visitLetStatement(RustParser.LetStatementContext ctx) {
    Identifier bindingTarget = extractBinding(ctx.patternNoTopAlt());

    Type type = extractType(ctx.type_());

    throw new UnsupportedConstructException(ctx);
  }

  private Type extractType(RustParser.Type_Context ctx) {
    String typeText = ctx.getText();
    return switch (typeText) {
      case "i8", "i16", "i32", "i64", "i128", "isize", "u8", "u16", "u32", "u64", "u128" ->
          Type.Int.valueOf(typeText);
      default -> throw new UnsupportedConstructException(ctx, "Unsupport type: " + typeText);
    };
  }

  private Identifier extractBinding(RustParser.PatternNoTopAltContext ctx) {
    PatternWithoutRangeContext pattern = ctx.patternWithoutRange();
    if (pattern == null) {
      throw new UnsupportedConstructException(ctx, "Only simple bindings are supported");
    }
    IdentifierPatternContext idPattern = pattern.identifierPattern();
    if (idPattern == null || idPattern.KW_REF() != null | idPattern.pattern() != null) {
      throw new UnsupportedConstructException(idPattern, "Only simple bindings are supported");
    }
    return new Identifier(idPattern.identifier().getText());
  }
}
