package project.lctrs;

import java.util.List;

/**
 * The fixed symbols of the theory signature, with their sorts.
 *
 * <p>These are the interpreted symbols whose meaning is fixed by the underlying theory (integer
 * arithmetic and the booleans). Each constant carries a fixed arity and sorts; sort-polymorphic
 * equality is split into monomorphic constants ({@link #EQ_INT}, {@link #EQ_BOOL}, {@link
 * #NEQ_INT}, {@link #NEQ_BOOL}) so every symbol has a single concrete signature.
 */
public enum TheorySymbol implements Symbol {
  // Integer arithmetic
  ADD("+", List.of(Sort.INT, Sort.INT), Sort.INT),
  SUB("-", List.of(Sort.INT, Sort.INT), Sort.INT),
  NEG("-", List.of(Sort.INT), Sort.INT),
  MUL("*", List.of(Sort.INT, Sort.INT), Sort.INT),
  DIV("/", List.of(Sort.INT, Sort.INT), Sort.INT),
  MOD("%", List.of(Sort.INT, Sort.INT), Sort.INT),

  // Integer comparisons
  LT("<", List.of(Sort.INT, Sort.INT), Sort.BOOL),
  LE("≤", List.of(Sort.INT, Sort.INT), Sort.BOOL),
  GT(">", List.of(Sort.INT, Sort.INT), Sort.BOOL),
  GE("≥", List.of(Sort.INT, Sort.INT), Sort.BOOL),
  EQ_INT("=", List.of(Sort.INT, Sort.INT), Sort.BOOL),
  EQ_BOOL("=", List.of(Sort.BOOL, Sort.BOOL), Sort.BOOL),
  NEQ_INT("≠", List.of(Sort.INT, Sort.INT), Sort.BOOL),
  NEQ_BOOL("≠", List.of(Sort.BOOL, Sort.BOOL), Sort.BOOL),

  // Boolean connectives
  AND("∧", List.of(Sort.BOOL, Sort.BOOL), Sort.BOOL),
  OR("∨", List.of(Sort.BOOL, Sort.BOOL), Sort.BOOL),
  NOT("¬", List.of(Sort.BOOL), Sort.BOOL);

  private final String notation;
  private final List<Sort> argSorts;
  private final Sort resultSort;

  TheorySymbol(String notation, List<Sort> argSorts, Sort resultSort) {
    this.notation = notation;
    this.argSorts = List.copyOf(argSorts);
    this.resultSort = resultSort;
  }

  @Override
  public String notation() {
    return notation;
  }

  @Override
  public List<Sort> argSorts() {
    return argSorts;
  }

  @Override
  public Sort resultSort() {
    return resultSort;
  }
}
