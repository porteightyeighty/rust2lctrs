package project.parser;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Tests that the shared parse entry point fails fast on malformed input instead of recovering into
 * a garbage parse tree. This is the production-path guarantee that mirrors the test harness's
 * loud-failure behaviour: a syntax error must abort the pipeline, not be lowered silently.
 */
final class RustParsingTest {

  @Test
  void parsesWellFormedRust() {
    assertDoesNotThrow(() -> RustParsing.parse("fn f(x: i32) -> i32 { x }"));
  }

  @Test
  void throwsOnReservedKeywordAsFunctionName() {
    // `mod` is a reserved keyword: ANTLR previously logged the error and recovered, lowering a
    // function with a blank name. The parse must now abort.
    SyntaxErrorException e =
        assertThrows(
            SyntaxErrorException.class, () -> RustParsing.parse("fn mod(a: i32) -> i32 { a }"));
    assertEquals(1, e.line());
  }

  @Test
  void throwsOnMissingBody() {
    assertThrows(SyntaxErrorException.class, () -> RustParsing.parse("fn f(x: i32) -> i32"));
  }

  @Test
  void throwsOnTrailingGarbage() {
    // The `crate` rule is EOF-anchored, so unconsumed trailing input is a syntax error too.
    assertThrows(SyntaxErrorException.class, () -> RustParsing.parse("fn f() -> i32 { 0 } @#$"));
  }
}
