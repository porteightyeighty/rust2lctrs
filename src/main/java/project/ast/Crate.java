package project.ast;

import java.util.List;
import java.util.Objects;

/**
 * The root node of the AST, corresponding to a Rust source file. Contains all top-level {@link
 * Item}s defined in the file.
 *
 * @param items the top-level items (e.g. function definitions)
 */
public record Crate(List<Item> items) implements Node {
  public Crate {
    Objects.requireNonNull(items);
    items = List.copyOf(items);
  }
}
