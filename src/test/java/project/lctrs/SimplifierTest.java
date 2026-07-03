package project.lctrs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SimplifierTest {

  private static final VarDecl X = new VarDecl("x", Sort.INT);

  private static final TermSymbol F = new TermSymbol("f", List.of(Sort.INT), Sort.RESULT);
  private static final TermSymbol U1 = new TermSymbol("u1", List.of(Sort.INT), Sort.RESULT);
  private static final TermSymbol U2 = new TermSymbol("u2", List.of(Sort.INT), Sort.RESULT);
  private static final TermSymbol RET = new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT);

  private static FnApp app(Symbol s, Term... args) {
    return new FnApp(s, List.of(args));
  }

  private static Rule rule(Term lhs, Term rhs) {
    return new Rule(lhs, rhs, Optional.empty());
  }

  private static Lctrs lctrs(List<Symbol> sigma, Rule... rules) {
    return new Lctrs().appendSymbols(sigma).appendRules(List.of(rules));
  }

  /**
   * A forwarding chain {@code f -> u1 -> u2 -> ret} collapses transitively: the entry rule is
   * redirected straight to {@code ret_Int}, the forwarding rules disappear, and so do their head
   * symbols in the signature. The entry symbol {@code f} is protected even though its rule is
   * itself a pure forward.
   */
  @Test
  void collapsesChainTransitivelyAndProtectsEntry() {
    Lctrs simplified =
        Simplifier.removeForwardingRules(
            lctrs(
                List.of(F, U1, U2, RET),
                rule(app(F, X), app(U1, X)),
                rule(app(U1, X), app(U2, X)),
                rule(app(U2, X), app(RET, X))),
            Set.of(F));

    assertEquals(List.of(rule(app(F, X), app(RET, X))), simplified.rules());
    assertEquals(List.of(F, RET), simplified.sigma());
  }

  /** Redirection reaches occurrences nested inside a right-hand side, not just at its root. */
  @Test
  void redirectsNestedOccurrences() {
    TermSymbol wrap = new TermSymbol("u3", List.of(Sort.RESULT), Sort.RESULT);
    Lctrs simplified =
        Simplifier.removeForwardingRules(
            lctrs(
                List.of(F, U1, U2, wrap),
                rule(app(F, X), app(wrap, app(U1, X))),
                rule(app(U1, X), app(U2, X)),
                rule(app(U2, X), app(RET, X))),
            Set.of(F));

    assertEquals(List.of(rule(app(F, X), app(wrap, app(RET, X)))), simplified.rules());
  }

  /**
   * Non-candidates survive untouched: a constrained rule, a head with two rules, a rule that drops
   * an argument, and a left-hand side that pattern-matches a constructor rather than binding
   * distinct variables.
   */
  @Test
  void keepsRulesThatAreNotPureForwards() {
    TermSymbol u3 = new TermSymbol("u3", List.of(Sort.INT, Sort.INT), Sort.RESULT);
    TermSymbol u4 = new TermSymbol("u4", List.of(Sort.INT), Sort.RESULT);
    VarDecl y = new VarDecl("y", Sort.INT);
    IntValue zero = new IntValue(BigInteger.ZERO);
    Constraint phi = new Constraint(new FnApp(TheorySymbol.LT, List.of(X, zero)));
    Rule constrained = new Rule(app(U1, X), app(U2, X), Optional.of(phi));
    Rule firstOfTwo = rule(app(U2, X), app(RET, X));
    Rule secondOfTwo = rule(app(U2, X), app(U1, X));
    Rule dropsArg = rule(app(u3, X, y), app(RET, y));
    // Same argument list on both sides, but a value is not a variable, so no forward.
    Rule patternLhs = rule(app(u4, zero), app(RET, zero));
    Lctrs in =
        lctrs(
            List.of(F, U1, U2, u3, u4, RET),
            constrained,
            firstOfTwo,
            secondOfTwo,
            dropsArg,
            patternLhs);

    Lctrs simplified = Simplifier.removeForwardingRules(in, Set.of(F));

    assertEquals(in.rules(), simplified.rules());
    assertEquals(in.sigma(), simplified.sigma());
  }

  /**
   * A forwarding cycle encodes nontermination (e.g. {@code loop { loop {} } }), so its members are
   * kept; a forward merely leading into the cycle is still removed.
   */
  @Test
  void keepsForwardingCycles() {
    Lctrs simplified =
        Simplifier.removeForwardingRules(
            lctrs(
                List.of(F, U1, U2),
                rule(app(F, X), app(U1, X)),
                rule(app(U1, X), app(U2, X)),
                rule(app(U2, X), app(U1, X))),
            Set.of(F));

    assertEquals(
        List.of(
            rule(app(F, X), app(U1, X)),
            rule(app(U1, X), app(U2, X)),
            rule(app(U2, X), app(U1, X))),
        simplified.rules());
    assertEquals(List.of(F, U1, U2), simplified.sigma());
  }

  /**
   * A head that also appears inside another rule's left-hand side is not removable: redirecting
   * right-hand sides alone would strand that pattern.
   */
  @Test
  void keepsHeadOccurringInAnotherLhs() {
    TermSymbol cont = new TermSymbol("u3", List.of(Sort.RESULT), Sort.RESULT);
    Lctrs in =
        lctrs(
            List.of(F, U1, RET, cont),
            rule(app(U1, X), app(RET, X)),
            rule(app(cont, app(U1, X)), app(RET, X)));

    Lctrs simplified = Simplifier.removeForwardingRules(in, Set.of(F));

    assertEquals(in.rules(), simplified.rules());
  }
}
