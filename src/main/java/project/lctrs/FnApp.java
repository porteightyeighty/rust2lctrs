package project.lctrs;

import java.util.List;
import java.util.Objects;

/**
 * A function application {@code f(t₁, …, tₙ)}: a symbol applied to argument terms. The canonical
 * constructor checks the argument count and per-argument sorts against the symbol's signature.
 *
 * @param symbol the function symbol applied
 * @param args the argument terms, matching the symbol's argument sorts in order
 */
public record FnApp(Symbol symbol, List<Term> args) implements Term {
  public FnApp {
    Objects.requireNonNull(symbol);
    Objects.requireNonNull(args);
    List<Sort> argSorts = symbol.argSorts();
    if (argSorts.size() != args.size()) {
      throw new IllegalArgumentException(
          "FnApp has wrong number of arguments, expected "
              + argSorts.size()
              + " got "
              + args.size());
    }
    for (int i = 0; i < args.size(); i++) {
      Sort expected = argSorts.get(i);
      Sort actual = args.get(i).sort();
      if (!expected.equals(actual)) {
        throw new IllegalArgumentException(
            "Sort mismatch at arg "
                + i
                + " of "
                + symbol
                + ": expected "
                + expected
                + ", got "
                + actual);
      }
    }
    args = List.copyOf(args);
  }

  /** {@inheritDoc} The sort of an application is the symbol's result sort. */
  @Override
  public Sort sort() {
    return symbol.resultSort();
  }

  /**
   * The integer sum {@code a + b}.
   *
   * @param a the left summand
   * @param b the right summand
   * @return the application of {@link TheorySymbol#ADD} to the two terms
   */
  public static FnApp add(Term a, Term b) {
    return new FnApp(TheorySymbol.ADD, List.of(a, b));
  }

  /**
   * The integer difference {@code a - b}.
   *
   * @param a the minuend
   * @param b the subtrahend
   * @return the application of {@link TheorySymbol#SUB} to the two terms
   */
  public static FnApp subtract(Term a, Term b) {
    return new FnApp(TheorySymbol.SUB, List.of(a, b));
  }

  /**
   * The integer remainder {@code a % b}, with Cora's Euclidean semantics (result in {@code [0,
   * |b|)}).
   *
   * @param a the dividend
   * @param b the divisor
   * @return the application of {@link TheorySymbol#MOD} to the two terms
   */
  public static FnApp modulo(Term a, Term b) {
    return new FnApp(TheorySymbol.MOD, List.of(a, b));
  }
}
