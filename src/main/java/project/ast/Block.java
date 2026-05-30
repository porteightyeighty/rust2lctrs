package project.ast;

import java.util.List;
import java.util.Objects;

/**
 * A scoped block: a sequence of statements.
 *
 * @param statements the statements within the block (may be empty)
 */
public record Block(List<Statement> statements) implements Node {
  public Block {
    Objects.requireNonNull(statements);
    statements = List.copyOf(statements);
  }
}
