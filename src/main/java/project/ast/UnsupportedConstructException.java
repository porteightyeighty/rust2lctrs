package project.ast;

import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Thrown by {@link AstBuilder} when it encounters a parse-tree node that is not part of the
 * supported Rust fragment. Carries the {@link Span} of the offending node for error reporting.
 */
public class UnsupportedConstructException extends RuntimeException {

  /** The source location of the unsupported construct. */
  final Span span;

  /** The description of what is unsupported, without the appended source span. */
  private final String detail;

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
    this.detail = message;
    super(message + " at " + s.toString());
  }

  /**
   * Returns the description of what is unsupported, without the source span that {@link
   * #getMessage()} appends. Suitable for building a {@link Diagnostic}, which carries its own span.
   *
   * @return the raw unsupported-construct description
   */
  public String detail() {
    return detail;
  }

  /**
   * Returns the source location of the unsupported construct.
   *
   * @return the span of the offending node
   */
  public Span span() {
    return span;
  }
}
