package project.lctrs;

import java.util.List;
import java.util.Objects;

/**
 * A program-defined term symbol, such as a program-point function symbol introduced when encoding
 * a function's control flow. Unlike {@link TheorySymbol}, its meaning comes from the rewrite rules,
 * not the underlying theory.
 *
 * @param notation how the symbol is written in Cora's input syntax
 * @param argSorts the sorts of the symbol's arguments, in order
 * @param resultSort the sort of the term formed by applying this symbol
 */
public record TermSymbol(String notation, List<Sort> argSorts, Sort resultSort) implements Symbol {
  public TermSymbol {
    Objects.requireNonNull(notation);
    argSorts = List.copyOf(Objects.requireNonNull(argSorts));
    Objects.requireNonNull(resultSort);
  }
}
