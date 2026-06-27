package project.ast;

import java.util.Objects;

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
   * Creates a {@code Diagnostic} from an {@link UnsupportedConstructException}, preserving the
   * specific message authored at the throw site. The exception's raw {@link
   * UnsupportedConstructException#detail() detail} is used rather than {@link
   * UnsupportedConstructException#getMessage()}, so the source location is not duplicated between
   * the message and the diagnostic's own span.
   *
   * @param e the caught unsupported-construct exception
   * @return a diagnostic carrying the exception's message and span
   */
  static Diagnostic of(UnsupportedConstructException e) {
    return new Diagnostic(e.detail(), e.span());
  }

  /**
   * Renders the diagnostic as its message followed by the source location, keeping the {@link Span}
   * encapsulated so callers in other packages can report a diagnostic without naming {@code Span}.
   *
   * @return the message with its source location appended
   */
  @Override
  public String toString() {
    return message + " at " + span;
  }
}
