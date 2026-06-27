package project.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Accumulates {@link Diagnostic}s during a single parse-tree walk. One recorder is threaded through
 * every builder so that out-of-scope constructs are collected rather than aborting on the first,
 * then drained by the caller after {@link AstBuilder#buildCrate} returns.
 */
public final class DiagnosticRecorder {

  private final List<Diagnostic> diagnostics;

  /** Creates an empty recorder. */
  public DiagnosticRecorder() {
    this.diagnostics = new ArrayList<>();
  }

  /**
   * Appends a diagnostic to the collection.
   *
   * @param diagnostic the diagnostic to record
   */
  public void add(Diagnostic diagnostic) {
    Objects.requireNonNull(diagnostic);
    this.diagnostics.add(diagnostic);
  }

  /**
   * Returns an immutable snapshot of the diagnostics collected so far, in insertion order.
   *
   * @return the recorded diagnostics
   */
  public List<Diagnostic> diagnostics() {
    return List.copyOf(diagnostics);
  }
}
