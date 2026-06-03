package project.lctrs;

import java.util.List;

/**
 * A function symbol in the LCTRS signature, either a fixed theory symbol or a program-defined term
 * symbol. Carries the symbol's surface notation and its sort profile (argument sorts and result
 * sort).
 */
public sealed interface Symbol permits TermSymbol, TheorySymbol {

  /** Returns how this symbol is written in Cora's input syntax. */
  String notation();

  /** Returns the sorts of the symbol's arguments, in order. */
  List<Sort> argSorts();

  /** Returns the sort of the term formed by applying this symbol. */
  Sort resultSort();
}
