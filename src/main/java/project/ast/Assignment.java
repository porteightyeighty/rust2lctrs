package project.ast;

import java.util.Objects;

public record Assignment(Identifier target, Expr value) implements Stmt {
  public Assignment {
    Objects.requireNonNull(target);
    Objects.requireNonNull(value);
  }
}
