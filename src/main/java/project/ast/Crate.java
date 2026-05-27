package project.ast;

import java.util.List;
import java.util.Objects;

/** Represents the root node of the crate. */
public record Crate(List<Item> items) {
  public Crate {
    Objects.requireNonNull(items);
  }
}
