package project.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
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
    Integer expected = new Integer(10);
    Integer actual = assertInstanceOf(Integer.class, expressionBuilder.buildExpression(expressionContext));
    assertEquals(expected, actual);
  }

  @Test
  void buildsBoolLiteralExpression() {
    String testInput = "true";
    ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    Boolean expected = new Boolean(true);
    Boolean actual = assertInstanceOf(Boolean.class, expressionBuilder.buildExpression(expressionContext));
    assertEquals(expected, actual);
  }

  @Test
  void rejectsFloatLiteral() {
    String testInput = "1.0";
    ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    assertThrows(
        UnsupportedConstructException.class, () -> expressionBuilder.buildExpression(expressionContext));
  }

  @Test
  void buildsArithmeticExpression() {
    String testInput = "1 + 2";
    ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    BinaryOp expected = new BinaryOp(BinaryOp.Op.ADD, new Integer(1), new Integer(2));
    BinaryOp actual =
        assertInstanceOf(BinaryOp.class, expressionBuilder.buildExpression(expressionContext));
    assertEquals(expected, actual);
  }

  @Test
  void buildsComparisonExpression() {
    String testInput = "1 == 2";
    ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    BinaryOp expected = new BinaryOp(BinaryOp.Op.EQ, new Integer(1), new Integer(2));
    BinaryOp actual =
        assertInstanceOf(BinaryOp.class, expressionBuilder.buildExpression(expressionContext));
    assertEquals(expected, actual);
  }

  @Test
  void rejectsLogicalExpression() {
    String testInput = "1 | 2";
    ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    assertThrows(
        UnsupportedConstructException.class, () -> expressionBuilder.buildExpression(expressionContext));
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
        UnsupportedConstructException.class, () -> expressionBuilder.buildExpression(expressionContext));
  }
}
