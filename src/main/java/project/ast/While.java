package project.ast;

import java.util.Objects;

public record While(Expression condition, Block block) implements Statement {
  public While {
    Objects.requireNonNull(condition);
    Objects.requireNonNull(block);
  }
}
