package project.ast;

import org.antlr.v4.runtime.ParserRuleContext;

public record Diagnostic(String message, Span span) {

  static Diagnostic unsupported(ParserRuleContext ctx) {
    String construct = ctx.getClass().getSimpleName();
    return new Diagnostic(construct + " is not supported", Span.of(ctx));
  }
}
