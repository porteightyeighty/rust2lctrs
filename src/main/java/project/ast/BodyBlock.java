package project.ast;

import java.util.List;
import java.util.Objects;

/**
 * A function-body block: a sequence of leading statements followed by a guaranteed trailing
 * {@link Return}. The trailing return is held as a separate field so callers can rely on its
 * presence without re-inspecting the statement list.
 *
 * @param leading the statements preceding the trailing return (may be empty)
 * @param returnStatement the block's trailing {@code return} statement
 */
public record BodyBlock(List<Stmt> leading, Return returnStatement) implements Node {
  public BodyBlock {
    Objects.requireNonNull(leading);
    Objects.requireNonNull(returnStatement);
    leading = List.copyOf(leading);
  }
}
