package project.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static project.translator.AstHelper.BOOL;
import static project.translator.AstHelper.I32;
import static project.translator.AstHelper.add;
import static project.translator.AstHelper.block;
import static project.translator.AstHelper.intLit;
import static project.translator.AstHelper.let;
import static project.translator.AstHelper.lt;
import static project.translator.AstHelper.mul;
import static project.translator.AstHelper.param;
import static project.translator.AstHelper.ret;
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
 *
 * <p>Two branches of the serialiser cannot be reached this way yet: the translator only emits
 * unconstrained rules and never the unary {@code ¬}. Those cases construct LCTRS terms directly and
 * are flagged below; revisit them as AstHelper-driven tests once control-flow translation lands.
 */
final class SerialiserTest {

  private static String ls() {
    return System.lineSeparator();
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

    assertEquals("f(n) -> u1(n, (n + 1))", Serialiser.serialise(lctrs.rules().get(0)));
    assertEquals("u1(n, x) -> x", Serialiser.serialise(lctrs.rules().get(1)));
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

    assertEquals("f(n) -> u1(n, (n + (n * 2)))", Serialiser.serialise(lctrs.rules().get(0)));
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

    assertEquals("f(n) -> u1(n, (n < 1))", Serialiser.serialise(lctrs.rules().get(0)));
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

    String expected =
        "f :: Int -> Int"
            + ls()
            + "u1 :: Int -> Int -> Int"
            + ls()
            + ls()
            + "f(n) -> u1(n, (n + 1))"
            + ls()
            + "u1(n, x) -> x"
            + ls();
    assertEquals(expected, Serialiser.serialise(lctrs));
  }

  /**
   * A constrained rule renders the constraint after the right-hand side. The translator does not
   * yet emit constraints, so the rule is built directly.
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

    assertEquals("u1(n) -> u2(n) | (n < 0)", Serialiser.serialise(rule));
  }

  /**
   * The unary {@code ¬} stays prefix. The translator does not yet emit it, so it is built directly.
   */
  @Test
  void unaryNotRendersPrefix() {
    VarDecl b = new VarDecl("b", Sort.BOOL);
    FnApp not = new FnApp(TheorySymbol.NOT, List.of(b));

    assertEquals("¬(b)", Serialiser.serialise(not));
  }
}
