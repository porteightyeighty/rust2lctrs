package project.ast;

import java.util.Objects;
import java.util.Optional;

/**
 * A {@code return} statement.
 *
 * @param value the expression whose value is returned
 */
public record Return(Optional<Expression> value) implements Statement {
  public Return {
    Objects.requireNonNull(value);
  }

  public Return(Expression value) {
    this(Optional.ofNullable(value));
  }
}
