package project.ast;

import java.util.Objects;

/**
 * An assignment to an existing mutable local: {@code target = value}.
 *
 * @param target the variable being assigned to
 * @param value the expression whose result is assigned
 */
public record Assignment(Identifier target, Expression value) implements Statement {
  public Assignment {
    Objects.requireNonNull(target);
    Objects.requireNonNull(value);
  }
}
