package project.ast;

import project.parser.RustParser.IdentifierPatternContext;
import project.parser.RustParser.PatternNoTopAltContext;
import project.parser.RustParser.PatternWithoutRangeContext;

/**
 * Reads the bound {@link Identifier} from a simple binding pattern. Shared by the item builder
 * (function parameters) and the statement builder ({@code let} bindings), both of which accept only
 * a plain identifier in binding position.
 */
final class BindingReader {

  private BindingReader() {}

  /**
   * Reads the {@link Identifier} bound by a pattern context. Only simple identifier patterns are
   * supported; {@code ref} bindings and sub-patterns are rejected.
   *
   * @param ctx the pattern context
   * @return the {@link Identifier} bound by the pattern
   * @throws UnsupportedConstructException if the pattern is not a simple identifier binding
   */
  static Identifier boundIdentifier(PatternNoTopAltContext ctx) {
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
}
