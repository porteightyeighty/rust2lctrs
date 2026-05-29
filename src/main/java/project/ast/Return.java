package project.ast;

import java.util.Objects;

/**
 * A {@code return} statement.
 *
 * @param value the expression whose value is returned
 */
public record Return(Expression value) implements Statement {
  public Return {
    Objects.requireNonNull(value);
  }
}
