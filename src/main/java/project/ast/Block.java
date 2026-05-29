package project.ast;

import java.util.List;
import java.util.Objects;

/**
 * A scoped block: a sequence of statements.
 *
 * @param leading the statements preceding the trailing return (may be empty)
 */
public record Block(List<Statement> statements) implements Node {
  public Block {
    Objects.requireNonNull(statements);
    statements = List.copyOf(statements);
  }
}
