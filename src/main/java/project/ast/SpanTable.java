package project.ast;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Maps AST node instances to their source {@link Span}. Identity-keyed so structurally equal nodes
 * at different source locations are tracked independently.
 */
public final class SpanTable {

  private final Map<Object, Span> spans = new IdentityHashMap<>();

  /**
   * Records the source span for the given AST node.
   *
   * @param node the AST node
   * @param span its location in the source
   */
  public void put(Object node, Span span) {
    spans.put(node, span);
  }

  /**
   * Returns the source span for the given AST node, or {@code null} if none was recorded.
   *
   * @param node the AST node to look up
   * @return the recorded {@link Span}, or {@code null}
   */
  public Span of(Object node) {
    return spans.get(node);
  }
}
