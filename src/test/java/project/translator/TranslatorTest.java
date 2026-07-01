package project.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static project.translator.AstHelper.BOOL;
import static project.translator.AstHelper.I32;
import static project.translator.AstHelper.add;
import static project.translator.AstHelper.block;
import static project.translator.AstHelper.boolLit;
import static project.translator.AstHelper.call;
import static project.translator.AstHelper.crate;
import static project.translator.AstHelper.fn;
import static project.translator.AstHelper.fnUnit;
import static project.translator.AstHelper.intLit;
import static project.translator.AstHelper.let;
import static project.translator.AstHelper.not;
import static project.translator.AstHelper.param;
import static project.translator.AstHelper.ret;
import static project.translator.AstHelper.translateFn;
import static project.translator.AstHelper.translateUnitFn;
import static project.translator.AstHelper.var;
import static project.translator.AstHelper.whileStmt;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import project.ast.UnsupportedConstructException;
import project.lctrs.Constraint;
import project.lctrs.FnApp;
import project.lctrs.IntValue;
import project.lctrs.Lctrs;
import project.lctrs.Rule;
import project.lctrs.Sort;
import project.lctrs.Term;
import project.lctrs.TermSymbol;
import project.lctrs.TheorySymbol;
import project.lctrs.VarDecl;

class TranslatorTest {

  private static final IntValue I32_MIN = new IntValue(BigInteger.valueOf(Integer.MIN_VALUE));
  private static final IntValue I32_MAX = new IntValue(BigInteger.valueOf(Integer.MAX_VALUE));

  /** The i32 within-width bound on {@code t}: {@code (MIN <= t) AND (t <= MAX)}. */
  private static FnApp i32Bound(Term t) {
    return new FnApp(
        TheorySymbol.AND,
        List.of(
            new FnApp(TheorySymbol.LE, List.of(I32_MIN, t)),
            new FnApp(TheorySymbol.LE, List.of(t, I32_MAX))));
  }

  /** The nullary error sink {@code err}. */
  private static FnApp err() {
    return new FnApp(new TermSymbol("err", List.of(), Sort.RESULT), List.of());
  }

  /**
   * {@code fn f(n: i32) -> i32 { let x = n + 1; x }}. The point of interest is that the bound value
   * on the right-hand side is evaluated over the <em>pre-binding</em> scope: the first slot is the
   * live variable {@code n} and the second is {@code n + 1}, never {@code x}. Because {@code n + 1}
   * can overflow, the entry splits into a guarded normal rule and an {@code err} rule on the
   * negated bound.
   */
  @Test
  void letBindingEvaluatesValueOverPreBindingScope() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(param("n", I32)),
            I32,
            block(let("x", I32, add(var("n"), intLit(1))), ret(var("x"))));

    VarDecl n = new VarDecl("n", Sort.INT);
    VarDecl x = new VarDecl("x", Sort.INT);
    FnApp entry = new FnApp(new TermSymbol("f", List.of(Sort.INT), Sort.RESULT), List.of(n));
    FnApp nPlusOne = new FnApp(TheorySymbol.ADD, List.of(n, new IntValue(BigInteger.ONE)));
    TermSymbol u1Symbol = new TermSymbol("u1", List.of(Sort.INT, Sort.INT), Sort.RESULT);
    FnApp u1 = new FnApp(u1Symbol, List.of(n, nPlusOne));
    FnApp u1scope = new FnApp(u1Symbol, List.of(n, x));
    FnApp retX = new FnApp(new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT), List.of(x));
    FnApp bound = i32Bound(nPlusOne);
    Rule errRule =
        new Rule(
            entry, err(), Optional.of(new Constraint(new FnApp(TheorySymbol.NOT, List.of(bound)))));
    Rule normalRule = new Rule(entry, u1, Optional.of(new Constraint(bound)));
    Rule retRule = new Rule(u1scope, retX, Optional.empty());
    assertEquals(List.of(errRule, normalRule, retRule), lctrs.rules());
  }

  /**
   * Two sequential {@code let} bindings thread their configurations: the outgoing configuration of
   * the first rule is the incoming configuration (left-hand side) of the second. For {@code fn f(n:
   * i32) -> i32 { let x = n + 1; let y = x; x }} this gives {@code f(n) -> u1(n, n + 1)} followed
   * by {@code u1(n, x) -> u2(n, x, x)}: the {@code u1} that closes rule 1 reopens as the head of
   * rule 2's left-hand side, now over the scope variables.
   */
  @Test
  void sequentialLetsThreadConfigurations() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(param("n", I32)),
            I32,
            block(let("x", I32, add(var("n"), intLit(1))), let("y", I32, var("x")), ret(var("x"))));

    VarDecl n = new VarDecl("n", Sort.INT);
    VarDecl x = new VarDecl("x", Sort.INT);
    VarDecl y = new VarDecl("y", Sort.INT);
    FnApp entry = new FnApp(new TermSymbol("f", List.of(Sort.INT), Sort.RESULT), List.of(n));
    FnApp nPlusOne = new FnApp(TheorySymbol.ADD, List.of(n, new IntValue(BigInteger.ONE)));
    TermSymbol u1Symbol = new TermSymbol("u1", List.of(Sort.INT, Sort.INT), Sort.RESULT);
    FnApp u1 = new FnApp(u1Symbol, List.of(n, nPlusOne));
    FnApp u1scope = new FnApp(u1Symbol, List.of(n, x));
    TermSymbol u2Symbol = new TermSymbol("u2", List.of(Sort.INT, Sort.INT, Sort.INT), Sort.RESULT);

    FnApp u2 = new FnApp(u2Symbol, List.of(n, x, x));
    FnApp u2scope = new FnApp(u2Symbol, List.of(n, x, y));
    FnApp retX = new FnApp(new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT), List.of(x));

    FnApp bound = i32Bound(nPlusOne);
    Rule errRule =
        new Rule(
            entry, err(), Optional.of(new Constraint(new FnApp(TheorySymbol.NOT, List.of(bound)))));
    // Only the first let (n + 1) can overflow; let y = x carries no bound, so rules 3 and 4 thread
    // configurations unconstrained.
    Rule normalRule = new Rule(entry, u1, Optional.of(new Constraint(bound)));
    Rule expected2 = new Rule(u1scope, u2, Optional.empty());
    Rule expected3 = new Rule(u2scope, retX, Optional.empty());

    assertEquals(List.of(errRule, normalRule, expected2, expected3), lctrs.rules());
  }

  /**
   * A shadowing {@code let} stays a distinct LCTRS variable rather than collapsing onto the binding
   * it shadows. For {@code fn f(n: i32) -> i32 { let x = n; let x = x + 1; x }} the second {@code
   * x} mints a fresh name {@code x_1}: its value {@code x + 1} is evaluated over the <em>outer</em>
   * {@code x}.
   */
  @Test
  void shadowingLetMintsDistinctVariable() {
    Lctrs lctrs =
        translateFn(
            "f",
            List.of(param("n", I32)),
            I32,
            block(let("x", I32, var("n")), let("x", I32, add(var("x"), intLit(1))), ret(var("x"))));

    VarDecl n = new VarDecl("n", Sort.INT);
    VarDecl x = new VarDecl("x", Sort.INT);
    VarDecl xShadow = new VarDecl("x_1", Sort.INT);
    FnApp entry = new FnApp(new TermSymbol("f", List.of(Sort.INT), Sort.RESULT), List.of(n));
    TermSymbol u1Symbol = new TermSymbol("u1", List.of(Sort.INT, Sort.INT), Sort.RESULT);
    FnApp u1 = new FnApp(u1Symbol, List.of(n, n));
    FnApp u1scope = new FnApp(u1Symbol, List.of(n, x));
    TermSymbol u2Symbol = new TermSymbol("u2", List.of(Sort.INT, Sort.INT, Sort.INT), Sort.RESULT);
    FnApp xPlusOne = new FnApp(TheorySymbol.ADD, List.of(x, new IntValue(BigInteger.ONE)));
    FnApp u2 = new FnApp(u2Symbol, List.of(n, x, xPlusOne));
    FnApp u2scope = new FnApp(u2Symbol, List.of(n, x, xShadow));
    FnApp retShadow =
        new FnApp(new TermSymbol("ret_Int", List.of(Sort.INT), Sort.RESULT), List.of(xShadow));

    FnApp bound = i32Bound(xPlusOne);
    // let x = n carries no bound (single unguarded rule); only the shadowing let x = x + 1
    // overflows.
    Rule firstLet = new Rule(entry, u1, Optional.empty());
    Rule errRule =
        new Rule(
            u1scope,
            err(),
            Optional.of(new Constraint(new FnApp(TheorySymbol.NOT, List.of(bound)))));
    Rule normalRule = new Rule(u1scope, u2, Optional.of(new Constraint(bound)));
    Rule retRule = new Rule(u2scope, retShadow, Optional.empty());

    assertEquals(List.of(firstLet, errRule, normalRule, retRule), lctrs.rules());
  }

  /**
   * {@code !} on an integer is bitwise negation, which is out of scope. It is indistinguishable
   * from logical not until the operand's sort is known, so it is rejected at translation rather
   * than lowered to ¬ over an int.
   */
  @Test
  void rejectsBitwiseNegationOnIntegerVariable() {
    assertThrows(
        UnsupportedConstructException.class,
        () ->
            translateFn(
                "f",
                List.of(param("n", I32)),
                I32,
                block(let("x", BOOL, not(var("n"))), ret(intLit(0)))));
  }

  @Test
  void rejectsBitwiseComplementOnIntegerLiteral() {
    assertThrows(
        UnsupportedConstructException.class,
        () ->
            translateFn(
                "f",
                List.of(param("n", I32)),
                I32,
                block(let("x", BOOL, not(intLit(5))), ret(intLit(0)))));
  }

  @Test
  void translatesUnitFunctionWithWhileLoop() {
    Lctrs lctrs =
        translateUnitFn(
            "f", List.of(), block(whileStmt(boolLit(true), block(ret(Optional.empty())))));

    FnApp entry = new FnApp(new TermSymbol("f", List.of(), Sort.RESULT), List.of());
    TermSymbol retUnitSym = new TermSymbol("ret_unit", List.of(), Sort.RESULT);

    // There will be a merge point for the while loop, which falls through to the unit return tail
    boolean foundUnitTail =
        lctrs.rules().stream().anyMatch(r -> r.rhs().equals(new FnApp(retUnitSym, List.of())));

    assertEquals(true, foundUnitTail);
  }

  @Test
  void translatesEmptyReturn() {
    Lctrs lctrs = translateUnitFn("f", List.of(), block(ret(Optional.empty())));

    // f() -> ret_unit()
    FnApp entry = new FnApp(new TermSymbol("f", List.of(), Sort.RESULT), List.of());
    FnApp retUnit = new FnApp(new TermSymbol("ret_unit", List.of(), Sort.RESULT), List.of());
    Rule expected = new Rule(entry, retUnit, Optional.empty());

    assertEquals(true, lctrs.rules().contains(expected));
  }

  @Test
  void rejectsCallingAUnitReturningFunction() {
    var g = fnUnit("g", List.of(), block(ret(Optional.empty())));
    var f = fn("f", List.of(), I32, block(let("x", I32, call("g")), ret(intLit(0))));

    assertThrows(
        UnsupportedConstructException.class, () -> new Translator(crate(g, f)).translate());
  }
}
