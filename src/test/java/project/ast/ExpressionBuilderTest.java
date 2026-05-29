package project.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import project.parser.RustParser;

public class ExpressionBuilderTest {

  private SpanTable spans;
  private AstBuilder astBuilder;

  @BeforeEach
  void setUp() {
    spans = new SpanTable();
    astBuilder = new AstBuilder(spans);
  }

  @Test
  void buildsIntLiteralExpression() {
    String testInput = "10";
    RustParser.ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    IntegerLiteral expected = new IntegerLiteral(10);
    IntegerLiteral actual = assertInstanceOf(IntegerLiteral.class, astBuilder.buildExpression(expressionContext));
    assertEquals(expected, actual);
  }

  @Test
  void buildsBinaryOperatorExpression() {
    String testInput = "1 + 2";
    RustParser.ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    BinaryOp expected = new BinaryOp(BinaryOp.Op.ADD, new IntegerLiteral(1), new IntegerLiteral(2));
    BinaryOp actual = assertInstanceOf(BinaryOp.class, astBuilder.buildExpression(expressionContext));
    assertEquals(expected, actual);
  }

  @Test
  void rejectsFloatLiteral() {
    String testInput = "1.0";
    RustParser.ExpressionContext expressionContext = TestHelper.parseExpr(testInput);
    assertThrows(
        UnsupportedConstructException.class, () -> astBuilder.buildExpression(expressionContext));
  }
}
