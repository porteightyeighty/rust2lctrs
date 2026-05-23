package project.ast;

import org.antlr.v4.runtime.ParserRuleContext;

public class UnsupportedConstructException extends RuntimeException {

  Span span;

  public UnsupportedConstructException(ParserRuleContext ctx) {
    this(ctx, "Unsupported construct " + ctx.getClass().getSimpleName());
  }

  public UnsupportedConstructException(ParserRuleContext ctx, String message) {
    Span s = Span.of(ctx);
    this.span = s;
    super(message + " at " + s.toString());
  }
}
