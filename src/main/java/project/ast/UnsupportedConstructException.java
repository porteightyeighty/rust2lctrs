package project.ast;

import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Thrown for a Rust construct that is not part of the supported fragment. Usually raised by {@link
 * AstBuilder} at parse-to-AST time with the {@link Span} of the offending node; also raised by the
 * translator for constructs that can only be recognised as out of scope once sorts are known.
 */
public class UnsupportedConstructException extends RuntimeException {

  /**
   * The source location of the unsupported construct, or {@code null} when only a string is known.
   */
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
   * Constructs an exception for a construct detected past the parse boundary, where the source
   * {@link Span} is no longer reachable.
   *
   * @param message a description of what is unsupported
   * @param location the rendered source location of the offending construct
   */
  public UnsupportedConstructException(String message, String location) {
    super(message + " at " + location);
    this.span = null;
    this.detail = message;
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
   * @return the span of the offending node, or {@code null} when the exception was built past the
   *     parse boundary from a location string
   */
  public Span span() {
    return span;
  }
}
