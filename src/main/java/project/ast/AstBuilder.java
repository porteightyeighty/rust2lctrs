package project.ast;

import java.util.Objects;
import project.parser.RustParser.CrateContext;

/**
 * Entry point for converting an ANTLR parse tree into the typed AST. Holds the {@link SpanRecorder}
 * and wires the per-level builders ({@link ItemBuilder}, {@link StatementBuilder}, {@link
 * ExpressionBuilder}) that do the work, so that the parse tree is walked exactly once.
 */
public final class AstBuilder {

  private final ItemBuilder items;

  /**
   * Creates a builder that records each node's source position in the given span table and collects
   * out-of-scope-construct diagnostics into the given recorder.
   *
   * @param spanTable the table that source spans are written to as nodes are built
   * @param diagnostics the recorder that out-of-scope-construct diagnostics are collected into
   */
  public AstBuilder(SpanTable spanTable, DiagnosticRecorder diagnostics) {
    SpanRecorder spans = new SpanRecorder(Objects.requireNonNull(spanTable));
    this.items = new ItemBuilder(spans, Objects.requireNonNull(diagnostics));
  }

  /**
   * Builds the root {@link Crate} from a crate parse-tree context. Only a single top-level function
   * is supported.
   *
   * @param ctx the top-level crate context produced by the parser
   * @return the corresponding {@link Crate} node; an empty crate if more than one item is present.
   *     Out-of-scope constructs are recorded as diagnostics rather than thrown, so callers must
   *     inspect the {@link DiagnosticRecorder} before treating the result as complete.
   */
  public Crate buildCrate(CrateContext ctx) {
    return items.buildCrate(ctx);
  }
}
