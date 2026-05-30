package project.ast;

import java.util.Objects;
import org.antlr.v4.runtime.ParserRuleContext;

/**
 * Records the source span of each AST node as it is built, against a shared {@link SpanTable}.
 *
 * <p>A single recorder is threaded through every builder so that the parse tree is walked exactly
 * once and all nodes land in the same table, regardless of which builder created them.
 */
final class SpanRecorder {

  private final SpanTable spans;

  /**
   * Creates a recorder backed by the given span table.
   *
   * @param spans the table that source spans are written to as nodes are built
   */
  SpanRecorder(SpanTable spans) {
    this.spans = Objects.requireNonNull(spans);
  }

  /**
   * Records the node's source span in the span table and returns the node unchanged, so calls can
   * be inlined into {@code return} statements.
   *
   * @param node the freshly built AST node
   * @param ctx the parse-tree context the node was built from
   * @param <T> the node type
   * @return the same {@code node}, after its span has been recorded
   */
  <T extends Node> T track(T node, ParserRuleContext ctx) {
    Objects.requireNonNull(node, "build method returned null: bug in AstBuilder");
    spans.put(node, Span.of(ctx));
    return node;
  }
}
