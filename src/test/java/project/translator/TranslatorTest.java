package project.translator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static project.translator.AstHelper.I32;
import static project.translator.AstHelper.add;
import static project.translator.AstHelper.block;
import static project.translator.AstHelper.intLit;
import static project.translator.AstHelper.let;
import static project.translator.AstHelper.param;
import static project.translator.AstHelper.ret;
import static project.translator.AstHelper.translateFn;
import static project.translator.AstHelper.var;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import project.lctrs.FnApp;
import project.lctrs.IntValue;
import project.lctrs.Lctrs;
import project.lctrs.Rule;
import project.lctrs.Sort;
import project.lctrs.TermSymbol;
import project.lctrs.TheorySymbol;
import project.lctrs.VarDecl;

class TranslatorTest {

  /**
   * {@code fn f(n: i32) -> i32 { let x = n + 1; x }} lowers to a single reassignment-free rule
   * {@code f(n) -> u1(n, n + 1)}. The point of interest is that the bound value on the right-hand
   * side is evaluated over the <em>pre-binding</em> scope: the first slot is the live variable
   * {@code n} and the second is {@code n + 1}, never {@code x}.
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
    FnApp retX = new FnApp(new TermSymbol("ret", List.of(Sort.INT), Sort.RESULT), List.of(x));
    Rule expected1 = new Rule(entry, u1, Optional.empty());
    Rule expected2 = new Rule(u1scope, retX, Optional.empty());
    assertEquals(List.of(expected1, expected2), lctrs.rules());
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
    FnApp retX = new FnApp(new TermSymbol("ret", List.of(Sort.INT), Sort.RESULT), List.of(x));

    Rule expected1 = new Rule(entry, u1, Optional.empty());
    Rule expected2 = new Rule(u1scope, u2, Optional.empty());
    Rule expected3 = new Rule(u2scope, retX, Optional.empty());

    assertEquals(List.of(expected1, expected2, expected3), lctrs.rules());
  }
}
