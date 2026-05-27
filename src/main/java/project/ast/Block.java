package project.ast;

import java.util.List;
import java.util.Objects;

/**
 * A braced block — a sequence of statements. The final statement must be a {@link Return}.
 *
 * @param statements the list of statements (last must be a {@link Return})
 */
public record Block(List<Stmt> statements) implements Node {
  public Block {
    Objects.requireNonNull(statements);
  }
}
