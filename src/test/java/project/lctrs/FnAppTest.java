package project.lctrs;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public final class FnAppTest {

  @Test
  void acceptsValidFnApp() {
    TermSymbol symbol = new TermSymbol("a", List.of(Sort.BOOL), Sort.BOOL);
    assertDoesNotThrow(() -> new FnApp(symbol, List.of(new BoolValue(true))));
  }

  @Test
  void rejectsMismatchedArgSort() {
    TermSymbol symbol = new TermSymbol("a", List.of(Sort.BOOL), Sort.BOOL);
    assertThrows(
        IllegalArgumentException.class,
        () -> new FnApp(symbol, List.of(new IntValue(BigInteger.valueOf(1)))));
  }

  @Test
  void rejectsMismatchedArityTooManyArgs() {
    TermSymbol symbol = new TermSymbol("a", List.of(Sort.BOOL), Sort.BOOL);
    assertThrows(
        IllegalArgumentException.class,
        () -> new FnApp(symbol, List.of(new BoolValue(true), new BoolValue(true))));
  }

  @Test
  void rejectsMismatchedArityTooManySorts() {
    TermSymbol symbol = new TermSymbol("a", List.of(Sort.BOOL, Sort.BOOL), Sort.BOOL);
    assertThrows(
        IllegalArgumentException.class, () -> new FnApp(symbol, List.of(new BoolValue(true))));
  }

  @Test
  void sortIsTheSymbolResultSort() {
    TermSymbol symbol = new TermSymbol("p", List.of(Sort.INT), Sort.BOOL);
    FnApp app = new FnApp(symbol, List.of(new IntValue(BigInteger.ONE)));
    assertEquals(Sort.BOOL, app.sort());
  }

  @Test
  void acceptsTheorySymbolApplication() {
    FnApp app =
        new FnApp(
            TheorySymbol.ADD, List.of(new IntValue(BigInteger.ONE), new IntValue(BigInteger.TWO)));
    assertEquals(Sort.INT, app.sort());
  }

  @Test
  void sortOfNestedTermIsCheckedAgainstArgSorts() {
    // The arg's own sort() drives the check, so a composite (non-Value) term must slot in by sort.
    FnApp inner =
        new FnApp(
            TheorySymbol.ADD, List.of(new IntValue(BigInteger.ONE), new IntValue(BigInteger.TWO)));
    assertDoesNotThrow(
        () -> new FnApp(TheorySymbol.LT, List.of(inner, new IntValue(BigInteger.ZERO))));
    assertThrows(
        IllegalArgumentException.class,
        () -> new FnApp(TheorySymbol.AND, List.of(inner, new BoolValue(true))));
  }

  @Test
  void argsAreDefensivelyCopied() {
    TermSymbol symbol = new TermSymbol("a", List.of(Sort.BOOL), Sort.BOOL);
    List<Term> args = new ArrayList<>(List.of(new BoolValue(true)));
    FnApp app = new FnApp(symbol, args);
    args.add(new BoolValue(false));
    assertEquals(1, app.args().size());
  }
}
