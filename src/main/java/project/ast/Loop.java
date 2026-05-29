package project.ast;

import java.util.Objects;

public record Loop(Block block) implements Statement {
  public Loop {
    Objects.requireNonNull(block);
  }
}
