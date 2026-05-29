package project.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import project.parser.RustParser;

public class AssignmentBuilderTest {

  private SpanTable spans;
  private AstBuilder astBuilder;

  @BeforeEach
  void setUp() {
    spans = new SpanTable();
    astBuilder = new AstBuilder(spans);
  }

  @Test
  void buildsAssignmentToLiteral() {
    String testInput = "x = 5";
    RustParser.AssignmentExpressionContext ctx =
        (RustParser.AssignmentExpressionContext) TestHelper.parseExpr(testInput);
    Assignment expected = new Assignment(new Identifier("x"), new Integer(5));
    assertEquals(expected, astBuilder.buildAssignment(ctx));
  }

  @Test
  void buildsAssignmentToExpression() {
    String testInput = "x = y + 1";
    RustParser.AssignmentExpressionContext ctx =
        (RustParser.AssignmentExpressionContext) TestHelper.parseExpr(testInput);
    Assignment expected =
        new Assignment(
            new Identifier("x"),
            new BinaryOp(BinaryOp.Op.ADD, new Variable(new Identifier("y")), new Integer(1)));
    assertEquals(expected, astBuilder.buildAssignment(ctx));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "a.b = 5", // field target
        "*p = 5", // dereference target
        "arr[0] = 5", // index target
      })
  void rejectsNonVariableAssignmentTargets(String input) {
    RustParser.AssignmentExpressionContext ctx =
        (RustParser.AssignmentExpressionContext) TestHelper.parseExpr(input);
    assertThrows(
        UnsupportedConstructException.class, () -> astBuilder.buildAssignment(ctx));
  }
}
