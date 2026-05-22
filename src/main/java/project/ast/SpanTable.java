package project.ast;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * A mapping from AST {@link Node}s to source {@link Span}. Used to track the source location of
 * nodes in the AST.
 */
public final class SpanTable {

  private final Map<Node, Span> spans = new IdentityHashMap<>();

  public void put(Node n, Span s) {
    spans.put(n, s);
  }

  public Span of(Node n) {
    return spans.get(n);
  }
}
