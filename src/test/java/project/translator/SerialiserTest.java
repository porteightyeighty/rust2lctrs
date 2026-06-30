package project.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static project.translator.AstHelper.BOOL;
import static project.translator.AstHelper.I32;
import static project.translator.AstHelper.add;
import static project.translator.AstHelper.block;
import static project.translator.AstHelper.call;
import static project.translator.AstHelper.intLit;
import static project.translator.AstHelper.let;
import static project.translator.AstHelper.lt;
import static project.translator.AstHelper.mul;
import static project.translator.AstHelper.neg;
import static project.translator.AstHelper.not;
import static project.translator.AstHelper.param;
import static project.translator.AstHelper.ret;
import static project.translator.AstHelper.sub;
import static project.translator.AstHelper.translateFn;
import static project.translator.AstHelper.var;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import project.lctrs.Constraint;
import project.lctrs.FnApp;
import project.lctrs.IntValue;
import project.lctrs.Lctrs;
import project.lctrs.Rule;
import project.lctrs.Serialiser;
import project.lctrs.Sort;
import project.lctrs.TermSymbol;
import project.lctrs.TheorySymbol;
import project.lctrs.VarDecl;

/**
 * Rendering tests for {@link Serialiser}. Inputs are built with {@link AstHelper} and run through
 * the {@link Translator}, so these assert over the LCTRS the pipeline actually produces — which is
 * why the test lives in this package (AstHelper is package-private here).
 */
final class SerialiserTest {

  private static String ls() {
    return System.lineSeparator();
  }

  /** The i32 within-width bound on {@code t} as an operand: {@code ((MIN ≤ t) ∧ (t ≤ MAX))}. */
  private static String i32Bound(String t) {
    return "((-2147483648 ≤ " + t + ") ∧ (" + t + " ≤ 2147483647))";
  }

  /** The same bound in a delimited position (constraint / argument), without its outer parens. */
  private static String i32BoundBare(String t) {
    return "(-2147483648 ≤ " + t + ") ∧ (" + t + " ≤ 2147483647)";
  }

  /** Binary theory symbols render infix and parenthesised; program-point symbols stay prefix. */
  @Test
  void infixBinaryWithinPrefixApplication() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(param("n", I32)),
            I32,
            block(let("x", I32, add(var("n"), intLit(1))), ret(var("x"))));

    // n + 1 can overflow, so the entry splits: err rule first, then the guarded normal rule.
    String bound = i32BoundBare("(n + 1)");
    assertEquals("f(n) -> err | ¬(" + bound + ")", Serialiser.serialise(lctrs.rules().get(0)));
    assertEquals("f(n) -> u1(n, n + 1) | " + bound, Serialiser.serialise(lctrs.rules().get(1)));
    assertEquals("u1(n, x) -> ret(x)", Serialiser.serialise(lctrs.rules().get(2)));
  }

  /** Nested binary applications nest their parentheses. */
  @Test
  void nestedBinaryNestsParentheses() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(param("n", I32)),
            I32,
            block(let("x", I32, add(var("n"), mul(var("n"), intLit(2)))), ret(var("x"))));

    // The normal (guarded) rule is at index 1; index 0 is the err rule on the negated bound.
    // Constraint is a delimited position, so the outer ∧ drops its parens; the two operand bounds
    // keep theirs.
    String bound = i32Bound("(n * 2)") + " ∧ " + i32Bound("(n + (n * 2))");
    assertEquals(
        "f(n) -> u1(n, n + (n * 2)) | " + bound, Serialiser.serialise(lctrs.rules().get(1)));
  }

  /** Comparisons are binary theory symbols, so they render infix. */
  @Test
  void comparisonRendersInfix() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(param("n", I32)),
            I32,
            block(let("b", BOOL, lt(var("n"), intLit(1))), ret(intLit(0))));

    assertEquals("f(n) -> u1(n, n < 1)", Serialiser.serialise(lctrs.rules().get(0)));
  }

  /** A whole LCTRS is the signature, a blank line, then the rules. */
  @Test
  void fullLctrsHasSignatureBlankLineThenRules() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(param("n", I32)),
            I32,
            block(let("x", I32, add(var("n"), intLit(1))), ret(var("x"))));

    String bound = i32BoundBare("(n + 1)");
    String expected =
        "f :: Int -> result"
            + ls()
            + "ret :: Int -> result"
            + ls()
            + "err :: result"
            + ls()
            + "u1 :: Int -> Int -> result"
            + ls()
            + ls()
            + "f(n) -> err | ¬("
            + bound
            + ")"
            + ls()
            + "f(n) -> u1(n, n + 1) | "
            + bound
            + ls()
            + "u1(n, x) -> ret(x)"
            + ls();
    assertEquals(expected, Serialiser.serialise(lctrs));
  }

  /**
   * A constrained rule renders the constraint after the right-hand side, built directly here as a
   * focused check on the {@code | φ} rendering.
   */
  @Test
  void constrainedRuleRendersConstraint() {
    VarDecl n = new VarDecl("n", Sort.INT);
    TermSymbol u1 = new TermSymbol("u1", List.of(Sort.INT), Sort.INT);
    TermSymbol u2 = new TermSymbol("u2", List.of(Sort.INT), Sort.INT);
    FnApp lhs = new FnApp(u1, List.of(n));
    FnApp rhs = new FnApp(u2, List.of(n));
    Constraint phi =
        new Constraint(new FnApp(TheorySymbol.LT, List.of(n, new IntValue(BigInteger.ZERO))));
    Rule rule = new Rule(lhs, rhs, Optional.of(phi));

    assertEquals("u1(n) -> u2(n) | n < 0", Serialiser.serialise(rule));
  }

  /** A unary {@code ¬} over an atomic operand renders tight, without parens. */
  @Test
  void unaryNotOverAtomicRendersTight() {
    VarDecl b = new VarDecl("b", Sort.BOOL);
    FnApp not = new FnApp(TheorySymbol.NOT, List.of(b));

    assertEquals("¬b", Serialiser.serialise(not));
  }

  /** A unary {@code ¬} over a compound operand keeps its parens. */
  @Test
  void unaryNotOverCompoundKeepsParens() {
    VarDecl x = new VarDecl("x", Sort.INT);
    VarDecl y = new VarDecl("y", Sort.INT);
    FnApp not = new FnApp(TheorySymbol.NOT, List.of(new FnApp(TheorySymbol.GT, List.of(x, y))));

    assertEquals("¬(x > y)", Serialiser.serialise(not));
  }

  /** Boolean not (!b) lowers to the theory's unary ¬, rendered tight, through the full pipeline. */
  @Test
  void booleanNotLowersToTightNot() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(param("b", BOOL)),
            BOOL,
            block(let("c", BOOL, not(var("b"))), ret(var("c"))));

    assertEquals("f(b) -> u1(b, ¬b)", Serialiser.serialise(lctrs.rules().get(0)));
  }

  /**
   * Arithmetic negation (-n) lowers to the theory's unary minus, rendered tight over a variable.
   */
  @Test
  void unaryMinusLowersToPrefixMinus() {
    Lctrs lctrs =
        translateFn(
            "f", List.of(param("n", I32)), I32, block(let("x", I32, neg(var("n"))), ret(var("x"))));

    String bound = i32BoundBare("-n");
    assertEquals("f(n) -> err | ¬(" + bound + ")", Serialiser.serialise(lctrs.rules().get(0)));
    assertEquals("f(n) -> u1(n, -n) | " + bound, Serialiser.serialise(lctrs.rules().get(1)));
  }

  @Test
  void selfCallEmitsContinuationAndLandingPad() {
    Lctrs lctrs = translateFn("f", List.of(param("n", I32)), I32, block(ret(call("f", var("n")))));

    assertEquals("f(n) -> u1(n, f(n))", Serialiser.serialise(lctrs.rules().get(0)));
    assertEquals("u1(n, ret($call)) -> u2(n, $call)", Serialiser.serialise(lctrs.rules().get(1)));
    assertEquals("u1(n, err) -> err", Serialiser.serialise(lctrs.rules().get(2)));
    assertEquals("u2(n, $call) -> ret($call)", Serialiser.serialise(lctrs.rules().get(3)));
    assertEquals(4, lctrs.rules().size());
  }

  @Test
  void callResultFeedsEnclosingArithmetic() {
    // fn f(n: i32) -> i32 { return n * f(n); } — the call reduces to $call, and the residual
    // n * $call is evaluated at the resume point u2.
    Lctrs lctrs =
        translateFn(
            "f", List.of(param("n", I32)), I32, block(ret(mul(var("n"), call("f", var("n"))))));

    String bound = i32BoundBare("(n * $call)");
    assertEquals("f(n) -> u1(n, f(n))", Serialiser.serialise(lctrs.rules().get(0)));
    assertEquals("u1(n, ret($call)) -> u2(n, $call)", Serialiser.serialise(lctrs.rules().get(1)));
    assertEquals("u1(n, err) -> err", Serialiser.serialise(lctrs.rules().get(2)));
    assertEquals(
        "u2(n, $call) -> err | ¬(" + bound + ")", Serialiser.serialise(lctrs.rules().get(3)));
    assertEquals(
        "u2(n, $call) -> ret(n * $call) | " + bound, Serialiser.serialise(lctrs.rules().get(4)));
  }

  @Test
  void argOverflowDischargedAtCall() {
    // fn f(n: i32) -> i32 { return f(n - 1); } — the arg n - 1 can overflow, so its safety guards
    // the jump and an err rule is emitted on the negated bound, before the landing-pad rules.
    Lctrs lctrs =
        translateFn(
            "f", List.of(param("n", I32)), I32, block(ret(call("f", sub(var("n"), intLit(1))))));

    String bound = i32BoundBare("(n - 1)");
    assertEquals("f(n) -> err | ¬(" + bound + ")", Serialiser.serialise(lctrs.rules().get(0)));
    assertEquals("f(n) -> u1(n, f(n - 1)) | " + bound, Serialiser.serialise(lctrs.rules().get(1)));
    assertEquals("u1(n, ret($call)) -> u2(n, $call)", Serialiser.serialise(lctrs.rules().get(2)));
    assertEquals("u1(n, err) -> err", Serialiser.serialise(lctrs.rules().get(3)));
    assertEquals("u2(n, $call) -> ret($call)", Serialiser.serialise(lctrs.rules().get(4)));
  }
}
