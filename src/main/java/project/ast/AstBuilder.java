package project.ast;

import java.util.Objects;
import project.parser.RustParser.CrateContext;

/**
 * Entry point for converting an ANTLR parse tree into the typed AST. Holds the {@link SpanRecorder}
 * and wires the per-level builders ({@link ItemBuilder}, {@link StatementBuilder}, {@link
 * ExpressionBuilder}) that do the work, so that the parse tree is walked exactly once.
 */
public final class AstBuilder {
  // TODO: Do a pre-pass to de-dupe variable names?
  private final ItemBuilder items;

  /**
   * Creates a builder that records each node's source position in the given span table.
   *
   * @param spanTable the table that source spans are written to as nodes are built
   */
  public AstBuilder(SpanTable spanTable) {
    SpanRecorder spans = new SpanRecorder(Objects.requireNonNull(spanTable));
    this.items = new ItemBuilder(spans);
  }

  /**
   * Builds the root {@link Crate} from a crate parse-tree context. Only a single top-level function
   * is supported.
   *
   * @param ctx the top-level crate context produced by the parser
   * @return the corresponding {@link Crate} node
   * @throws UnsupportedConstructException if the crate contains more than one item
   */
  public Crate buildCrate(CrateContext ctx) {
    return items.buildCrate(ctx);
  }
}
