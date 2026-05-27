package project.ast;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A braced block, a sequence of statements followed by an optional trailing expression whose value
 * becomes the block's value.
 *
 * @param statements the list of statements (may be empty)
 * @param trailingExpression the final expression whose value the block evaluates to, if present
 */
public record Block(List<Stmt> statements, Optional<Expr> trailingExpression) {
  public Block {
    Objects.requireNonNull(statements);
    Objects.requireNonNull(trailingExpression);
  }
}
