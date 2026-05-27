package project.ast;

import java.util.Objects;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * A structured diagnostic message tied to a source location. Intended for collecting multiple
 * errors in a single pass rather than failing fast.
 *
 * @param message a human-readable description of the problem
 * @param span the source location of the offending construct
 */
public record Diagnostic(String message, Span span) {
  public Diagnostic {
    Objects.requireNonNull(message);
    Objects.requireNonNull(span);
  }

  /**
   * Creates a {@code Diagnostic} reporting that the given parse-tree node's construct is not
   * supported.
   *
   * @param ctx the unsupported parse-tree node
   * @return a diagnostic at the node's source location
   */
  static Diagnostic unsupported(ParserRuleContext ctx) {
    String construct = ctx.getClass().getSimpleName();
    return new Diagnostic(construct + " is not supported", Span.of(ctx));
  }
}
