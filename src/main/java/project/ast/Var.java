package project.ast;

import java.util.Objects;

public record Var(Identifier name) implements Expr {
  public Var {
    Objects.requireNonNull(name);
  }
}
