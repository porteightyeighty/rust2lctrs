package project.lctrs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Sweep over the hand-written {@link TheorySymbol} signature table. The table is data, not logic,
 * so the realistic failure mode is a typo: a symbol with the wrong arity, the wrong argument sort,
 * or the wrong result sort. These tests restate the intended signatures independently and assert
 * the whole enum against them, so any single-symbol slip is caught.
 */
public final class TheorySymbolTest {

  /** Binary integer arithmetic: (INT, INT) -> INT. */
  private static final Set<TheorySymbol> ARITHMETIC =
      EnumSet.of(
          TheorySymbol.ADD, TheorySymbol.SUB, TheorySymbol.MUL, TheorySymbol.DIV, TheorySymbol.MOD);

  /** Binary integer comparisons: (INT, INT) -> BOOL. */
  private static final Set<TheorySymbol> INT_COMPARISON =
      EnumSet.of(
          TheorySymbol.LT,
          TheorySymbol.LE,
          TheorySymbol.GT,
          TheorySymbol.GE,
          TheorySymbol.EQ_INT,
          TheorySymbol.NEQ_INT);

  /** Binary boolean comparisons and connectives: (BOOL, BOOL) -> BOOL. */
  private static final Set<TheorySymbol> BOOL_BINARY =
      EnumSet.of(TheorySymbol.EQ_BOOL, TheorySymbol.NEQ_BOOL, TheorySymbol.AND, TheorySymbol.OR);

  /** Unary boolean connective: (BOOL) -> BOOL. */
  private static final Set<TheorySymbol> BOOL_UNARY = EnumSet.of(TheorySymbol.NOT);

  @Test
  void arithmeticSymbolsAreBinaryIntToInt() {
    for (TheorySymbol s : ARITHMETIC) {
      assertEquals(List.of(Sort.INT, Sort.INT), s.argSorts(), s + " argSorts");
      assertEquals(Sort.INT, s.resultSort(), s + " resultSort");
    }
  }

  @Test
  void intComparisonsAreBinaryIntToBool() {
    for (TheorySymbol s : INT_COMPARISON) {
      assertEquals(List.of(Sort.INT, Sort.INT), s.argSorts(), s + " argSorts");
      assertEquals(Sort.BOOL, s.resultSort(), s + " resultSort");
    }
  }

  @Test
  void boolBinarySymbolsAreBinaryBoolToBool() {
    for (TheorySymbol s : BOOL_BINARY) {
      assertEquals(List.of(Sort.BOOL, Sort.BOOL), s.argSorts(), s + " argSorts");
      assertEquals(Sort.BOOL, s.resultSort(), s + " resultSort");
    }
  }

  @Test
  void notIsTheOnlyUnarySymbol() {
    assertEquals(List.of(Sort.BOOL), TheorySymbol.NOT.argSorts());
    assertEquals(Sort.BOOL, TheorySymbol.NOT.resultSort());
    for (TheorySymbol s : TheorySymbol.values()) {
      int expectedArity = s == TheorySymbol.NOT ? 1 : 2;
      assertEquals(expectedArity, s.argSorts().size(), s + " arity");
    }
  }

  @Test
  void everySymbolBelongsToExactlyOneGroup() {
    // Forces this test to be updated whenever a symbol is added to the enum, so new symbols can't
    // slip past the sweep above untested.
    for (TheorySymbol s : TheorySymbol.values()) {
      int memberships =
          (ARITHMETIC.contains(s) ? 1 : 0)
              + (INT_COMPARISON.contains(s) ? 1 : 0)
              + (BOOL_BINARY.contains(s) ? 1 : 0)
              + (BOOL_UNARY.contains(s) ? 1 : 0);
      assertEquals(1, memberships, s + " must belong to exactly one signature group");
    }
  }

  @Test
  void notationIsNonEmpty() {
    for (TheorySymbol s : TheorySymbol.values()) {
      assertFalse(s.notation().isEmpty(), s + " notation");
    }
  }

  @Test
  void argSortsIsImmutable() {
    List<Sort> argSorts = TheorySymbol.ADD.argSorts();
    assertThrows(UnsupportedOperationException.class, () -> argSorts.add(Sort.INT));
  }

  @Test
  void argSortsIsStableAcrossCalls() {
    assertSame(TheorySymbol.ADD.argSorts(), TheorySymbol.ADD.argSorts());
  }

  @Test
  void equalityNotationIsSharedAcrossDistinctSymbols() {
    // EQ_INT and EQ_BOOL deliberately share the "=" notation but are distinct symbols with
    // different argument sorts; the table must not collapse them.
    assertTrue(TheorySymbol.EQ_INT != TheorySymbol.EQ_BOOL);
    assertEquals(TheorySymbol.EQ_INT.notation(), TheorySymbol.EQ_BOOL.notation());
    assertEquals(List.of(Sort.INT, Sort.INT), TheorySymbol.EQ_INT.argSorts());
    assertEquals(List.of(Sort.BOOL, Sort.BOOL), TheorySymbol.EQ_BOOL.argSorts());
  }
}
