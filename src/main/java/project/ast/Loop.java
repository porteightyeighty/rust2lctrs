package project.ast;

import java.util.Objects;

/**
 * An unconditional {@code loop}, exited only via {@link Break}.
 *
 * @param block the loop body
 */
public record Loop(Block block) implements Statement {
  public Loop {
    Objects.requireNonNull(block);
  }
}
