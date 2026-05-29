package project.ast;

import java.util.Objects;

public record Assignment(Identifier target, Expression value) implements Statement {
  public Assignment {
    Objects.requireNonNull(target);
    Objects.requireNonNull(value);
  }
}
