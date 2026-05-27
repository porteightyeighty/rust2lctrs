package project.ast;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Maps AST node instances to their source {@link Span}. Identity-keyed so structurally equal nodes
 * at different source locations are tracked independently.
 */
public final class SpanTable {

  private final Map<Node, Span> spans = new IdentityHashMap<>();

  /**
   * Records the source span for the given AST node.
   *
   * @param node the AST node
   * @param span its location in the source
   */
  public void put(Node node, Span span) {
    spans.put(node, span);
  }

  /**
   * Returns the source span for the given AST node.
   *
   * @param node the AST node to look up
   * @return the recorded {@link Span}
   * @throws NoSuchElementException if no span was recorded for {@code node}
   */
  public Span get(Node node) {
    Span s = spans.get(node);
    if (s == null) throw new NoSuchElementException("No span recorded for " + node);
    return s;
  }
}
