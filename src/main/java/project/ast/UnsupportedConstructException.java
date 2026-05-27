package project.ast;

import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Thrown by {@link AstBuilder} when it encounters a parse-tree node that is not part of the
 * supported Rust fragment. Carries the {@link Span} of the offending node for error reporting.
 */
public class UnsupportedConstructException extends RuntimeException {

  /** The source location of the unsupported construct. */
  final Span span;

  /**
   * Constructs an exception with a default message derived from the context's class name.
   *
   * @param ctx the unsupported parse-tree node
   */
  public UnsupportedConstructException(ParserRuleContext ctx) {
    this(ctx, "Unsupported construct " + ctx.getClass().getSimpleName());
  }

  /**
   * Constructs an exception with an explicit message, appending the source span.
   *
   * @param ctx the unsupported parse-tree node
   * @param message a description of what is unsupported
   */
  public UnsupportedConstructException(ParserRuleContext ctx, String message) {
    Span s = Span.of(ctx);
    this.span = s;
    super(message + " at " + s.toString());
  }
}
