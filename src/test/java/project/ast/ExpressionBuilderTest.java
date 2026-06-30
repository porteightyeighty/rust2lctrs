package project.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import project.parser.RustParser.ExpressionContext;

public class ExpressionBuilderTest {

  private SpanTable spans;
  private ExpressionBuilder expressionBuilder;

  @BeforeEach
  void setUp() {
    spans = new SpanTable();
    expressionBuilder = new ExpressionBuilder(new SpanRecorder(spans));
  }

  @Test
  void buildsIntLiteralExpression() {
    String testInput = "10";
    ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    IntegerLiteral expected = new IntegerLiteral(BigInteger.valueOf(10));
    IntegerLiteral actual =
        assertInstanceOf(
            IntegerLiteral.class, expressionBuilder.buildExpression(expressionContext));
    assertEquals(expected, actual);
  }

  @Test
  void buildsBoolLiteralExpression() {
    String testInput = "true";
    ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    BooleanLiteral expected = new BooleanLiteral(true);
    BooleanLiteral actual =
        assertInstanceOf(
            BooleanLiteral.class, expressionBuilder.buildExpression(expressionContext));
    assertEquals(expected, actual);
  }

  @ParameterizedTest
  @CsvSource({
    // decimal with underscore separators
    "1_0,           10",
    "1_000_000,     1000000",
    // hexadecimal, mixed case, leading underscore
    "0xff,          255",
    "0xFF,          255",
    "0xdead_beef,   3735928559",
    "0x_ff,         255",
    // octal
    "0o17,          15",
    "0o755,         493",
    // binary, leading underscore
    "0b1010,        10",
    "0b_1010,       10",
    // type suffixes are stripped, on any radix
    "42u8,          42",
    "100i64,        100",
    "0xffu32,       255",
    "1_000usize,    1000",
    "255i128,       255",
    "10isize,       10",
  })
  void buildsIntLiteralWithRadixUnderscoresAndSuffix(String input, String expectedDecimal) {
    ExpressionContext expressionContext = TestHelper.parseExpr(input);
    IntegerLiteral actual =
        assertInstanceOf(
            IntegerLiteral.class, expressionBuilder.buildExpression(expressionContext));
    assertEquals(new BigInteger(expectedDecimal), actual.value());
  }

  @Test
  void buildsIntLiteralBeyondLongRange() {
    // 2^64 - 1 overflows a long; parsing must go through BigInteger.
    String testInput = "0xffff_ffff_ffff_ffff";
    ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    IntegerLiteral actual =
        assertInstanceOf(
            IntegerLiteral.class, expressionBuilder.buildExpression(expressionContext));
    assertEquals(new BigInteger("18446744073709551615"), actual.value());
  }

  @Test
  void rejectsFloatLiteral() {
    String testInput = "1.0";
    ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    assertThrows(
        UnsupportedConstructException.class,
        () -> expressionBuilder.buildExpression(expressionContext));
  }

  @Test
  void buildsArithmeticExpression() {
    String testInput = "1 + 2";
    ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    BinaryOp expected =
        new BinaryOp(
            BinaryOp.Op.ADD,
            new IntegerLiteral(BigInteger.valueOf(1)),
            new IntegerLiteral(BigInteger.valueOf(2)));
    BinaryOp actual =
        assertInstanceOf(BinaryOp.class, expressionBuilder.buildExpression(expressionContext));
    assertEquals(expected, actual);
  }

  @Test
  void buildsSelfRecursiveCallExpression() {
    expressionBuilder.setEnclosingFunction("f");
    ExpressionContext ctx = TestHelper.parseExpr("f(n, 1)");
    FunctionCall actual =
        assertInstanceOf(FunctionCall.class, expressionBuilder.buildExpression(ctx));
    assertEquals(new Identifier("f"), actual.function());
    assertEquals(
        java.util.List.of(
            new Variable(new Identifier("n")), new IntegerLiteral(BigInteger.valueOf(1))),
        actual.args());
  }

  @Test
  void buildsNestedSelfRecursiveCallExpression() {
    expressionBuilder.setEnclosingFunction("f");
    ExpressionContext ctx = TestHelper.parseExpr("f(f(n))");
    FunctionCall outer =
        assertInstanceOf(FunctionCall.class, expressionBuilder.buildExpression(ctx));
    FunctionCall inner = assertInstanceOf(FunctionCall.class, outer.args().get(0));
    assertEquals(new Variable(new Identifier("n")), inner.args().get(0));
  }

  @Test
  void rejectsCallToOtherFunction() {
    expressionBuilder.setEnclosingFunction("f");
    ExpressionContext ctx = TestHelper.parseExpr("g(n)");
    assertThrows(UnsupportedConstructException.class, () -> expressionBuilder.buildExpression(ctx));
  }

  @Test
  void rejectsMethodCall() {
    expressionBuilder.setEnclosingFunction("f");
    ExpressionContext ctx = TestHelper.parseExpr("x.foo()");
    assertThrows(UnsupportedConstructException.class, () -> expressionBuilder.buildExpression(ctx));
  }

  @Test
  void buildsComparisonExpression() {
    String testInput = "1 == 2";
    ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    BinaryOp expected =
        new BinaryOp(
            BinaryOp.Op.EQ,
            new IntegerLiteral(BigInteger.valueOf(1)),
            new IntegerLiteral(BigInteger.valueOf(2)));
    BinaryOp actual =
        assertInstanceOf(BinaryOp.class, expressionBuilder.buildExpression(expressionContext));
    assertEquals(expected, actual);
  }

  @Test
  void rejectsLogicalExpression() {
    String testInput = "1 | 2";
    ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    assertThrows(
        UnsupportedConstructException.class,
        () -> expressionBuilder.buildExpression(expressionContext));
  }

  @Test
  void buildsVariableExpression() {
    String testInput = "x";
    ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    Variable expected = new Variable(new Identifier("x"));
    Variable actual =
        assertInstanceOf(Variable.class, expressionBuilder.buildExpression(expressionContext));
    assertEquals(expected, actual);
  }

  @Test
  void foldsNegatedIntLiteralIntoNegativeLiteral() {
    String testInput = "-5";
    ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    IntegerLiteral actual =
        assertInstanceOf(
            IntegerLiteral.class, expressionBuilder.buildExpression(expressionContext));
    assertEquals(BigInteger.valueOf(-5), actual.value());
  }

  @Test
  void negatesVariableToUnaryMinus() {
    String testInput = "-x";
    ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    UnaryMinus expected = new UnaryMinus(new Variable(new Identifier("x")));
    UnaryMinus actual =
        assertInstanceOf(UnaryMinus.class, expressionBuilder.buildExpression(expressionContext));
    assertEquals(expected, actual);
  }

  @Test
  void negatesGroupedSubexpressionToUnaryMinus() {
    String testInput = "-(x + 1)";
    ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    UnaryMinus actual =
        assertInstanceOf(UnaryMinus.class, expressionBuilder.buildExpression(expressionContext));
    assertInstanceOf(BinaryOp.class, actual.operand());
  }

  @Test
  void unwrapsGroupedExpression() {
    String testInput = "(1 + 2)";
    ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    BinaryOp expected =
        new BinaryOp(
            BinaryOp.Op.ADD,
            new IntegerLiteral(BigInteger.valueOf(1)),
            new IntegerLiteral(BigInteger.valueOf(2)));
    BinaryOp actual =
        assertInstanceOf(BinaryOp.class, expressionBuilder.buildExpression(expressionContext));
    assertEquals(expected, actual);
  }

  @Test
  void groupingOverridesPrecedence() {
    // Without parens this is 1 + (2 * 3); the group must force (1 + 2) * 3.
    String testInput = "(1 + 2) * 3";
    ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    BinaryOp actual =
        assertInstanceOf(BinaryOp.class, expressionBuilder.buildExpression(expressionContext));
    assertEquals(BinaryOp.Op.MUL, actual.operator());
    assertEquals(
        new BinaryOp(
            BinaryOp.Op.ADD,
            new IntegerLiteral(BigInteger.valueOf(1)),
            new IntegerLiteral(BigInteger.valueOf(2))),
        actual.left());
    assertEquals(new IntegerLiteral(BigInteger.valueOf(3)), actual.right());
  }

  @Test
  void foldsDoubleNegationOfLiteral() {
    String testInput = "--5";
    ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    IntegerLiteral actual =
        assertInstanceOf(
            IntegerLiteral.class, expressionBuilder.buildExpression(expressionContext));
    assertEquals(BigInteger.valueOf(5), actual.value());
  }

  @Test
  void carriesBooleanNotAsUnaryNot() {
    String testInput = "!x";
    ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    UnaryNot expected = new UnaryNot(new Variable(new Identifier("x")));
    UnaryNot actual =
        assertInstanceOf(UnaryNot.class, expressionBuilder.buildExpression(expressionContext));
    assertEquals(expected, actual);
  }

  @Test
  void foldsBooleanNotOfLiteral() {
    String testInput = "!true";
    ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    BooleanLiteral actual =
        assertInstanceOf(
            BooleanLiteral.class, expressionBuilder.buildExpression(expressionContext));
    assertFalse(actual.value());
  }

  @Test
  void nestsDoubleBooleanNot() {
    String testInput = "!!x";
    ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    UnaryNot expected = new UnaryNot(new UnaryNot(new Variable(new Identifier("x"))));
    UnaryNot actual =
        assertInstanceOf(UnaryNot.class, expressionBuilder.buildExpression(expressionContext));
    assertEquals(expected, actual);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "foo::bar", // multi-segment path
        "std::mem::swap", // multi-segment path
        "x::<i32>", // generic arguments (turbofish)
      })
  void rejectsNonSimplePathExpressions(String input) {
    ExpressionContext expressionContext = TestHelper.parseExpr(input);
    assertThrows(
        UnsupportedConstructException.class,
        () -> expressionBuilder.buildExpression(expressionContext));
  }
}
