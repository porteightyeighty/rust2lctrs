package project.ast;

import java.util.Objects;

/**
 * A {@code return} statement.
 *
 * @param value the expression whose value is returned
 */
public record Return(Expr value) implements Stmt {
  public Return {
    Objects.requireNonNull(value);
  }
}
