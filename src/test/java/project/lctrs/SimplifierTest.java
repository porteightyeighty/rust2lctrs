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

  private static Constraint constraint(Term formula) {
    return new Constraint(formula);
  }

  /**
   * The shape a division by the literal 2 leaves on its {@code err} rule: {@code ¬((2 ≠ 0) ∧ ¬((y =
   * MIN) ∧ (2 = -1)))} folds to {@code false}, so the rule can never fire and is dropped.
   */
  @Test
  void dropsRuleWithUnsatisfiableConstraint() {
    Term guard =
        app(
            TheorySymbol.AND,
            app(TheorySymbol.NEQ_INT, IntValue.of(2), IntValue.of(0)),
            app(
                TheorySymbol.NOT,
                app(
                    TheorySymbol.AND,
                    app(TheorySymbol.EQ_INT, X, IntValue.of(-2147483648L)),
                    app(TheorySymbol.EQ_INT, IntValue.of(2), IntValue.of(-1)))));
    Rule err =
        new Rule(app(U1, X), app(RET, X), Optional.of(constraint(app(TheorySymbol.NOT, guard))));
    Rule live = rule(app(U2, X), app(RET, X));

    Lctrs simplified =
        Simplifier.foldConstantConstraints(lctrs(List.of(F, U1, U2, RET), err, live));

    assertEquals(List.of(live), simplified.rules());
    assertEquals(List.of(F, U1, U2, RET), simplified.sigma());
  }

  /**
   * A partially constant conjunction keeps its variable part: {@code ((2 ≠ 0) ∧ ¬(false)) ∧ (x ≥
   * 0)} folds to {@code x ≥ 0}.
   */
  @Test
  void foldsConstantConjunctsKeepingVariablePart() {
    Term agree = app(TheorySymbol.GE, X, IntValue.of(0));
    Term formula =
        app(
            TheorySymbol.AND,
            app(
                TheorySymbol.AND,
                app(TheorySymbol.NEQ_INT, IntValue.of(2), IntValue.of(0)),
                app(TheorySymbol.NOT, new BoolValue(false))),
            agree);
    Rule r = new Rule(app(U1, X), app(RET, X), Optional.of(constraint(formula)));

    Lctrs simplified = Simplifier.foldConstantConstraints(lctrs(List.of(F, U1, RET), r));

    assertEquals(
        List.of(new Rule(app(U1, X), app(RET, X), Optional.of(constraint(agree)))),
        simplified.rules());
  }

  /** A constraint that folds to {@code true} is dropped, leaving the rule unconstrained. */
  @Test
  void dropsConstraintFoldingToTrue() {
    Term formula =
        app(
            TheorySymbol.OR,
            app(TheorySymbol.GT, IntValue.of(2), IntValue.of(0)),
            app(TheorySymbol.LT, X, IntValue.of(0)));
    Rule r = new Rule(app(U1, X), app(RET, X), Optional.of(constraint(formula)));

    Lctrs simplified = Simplifier.foldConstantConstraints(lctrs(List.of(F, U1, RET), r));

    assertEquals(List.of(rule(app(U1, X), app(RET, X))), simplified.rules());
  }

  /**
   * A constraint that is already the bare value {@code true} (e.g. from {@code if true} with no
   * safety clauses) is dropped even though folding it is an identity.
   */
  @Test
  void dropsBareTrueConstraint() {
    Rule r = new Rule(app(U1, X), app(RET, X), Optional.of(constraint(new BoolValue(true))));

    Lctrs simplified = Simplifier.foldConstantConstraints(lctrs(List.of(F, U1, RET), r));

    assertEquals(List.of(rule(app(U1, X), app(RET, X))), simplified.rules());
  }

  /** A rule whose constraint is already the bare value {@code false} can never fire. */
  @Test
  void dropsRuleWithBareFalseConstraint() {
    Rule dead = new Rule(app(U1, X), app(RET, X), Optional.of(constraint(new BoolValue(false))));
    Rule live = rule(app(U2, X), app(RET, X));

    Lctrs simplified =
        Simplifier.foldConstantConstraints(lctrs(List.of(F, U1, U2, RET), dead, live));

    assertEquals(List.of(live), simplified.rules());
  }

  /** A constraint with no constant atoms is untouched, and the LCTRS is returned as-is. */
  @Test
  void leavesVariableOnlyConstraintsAlone() {
    Term formula =
        app(
            TheorySymbol.AND,
            app(TheorySymbol.LT, X, IntValue.of(0)),
            app(TheorySymbol.NEQ_INT, app(TheorySymbol.MOD, X, IntValue.of(2)), IntValue.of(0)));
    Rule r = new Rule(app(U1, X), app(RET, X), Optional.of(constraint(formula)));
    Lctrs in = lctrs(List.of(F, U1, RET), r);

    assertEquals(in.rules(), Simplifier.foldConstantConstraints(in).rules());
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
