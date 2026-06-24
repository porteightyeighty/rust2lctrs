package project.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import project.parser.RustParser.AssignmentExpressionContext;

public class AssignmentBuilderTest {

  private SpanTable spans;
  private DiagnosticRecorder diagnostics;
  private StatementBuilder statementBuilder;

  @BeforeEach
  void setUp() {
    spans = new SpanTable();
    diagnostics = new DiagnosticRecorder();
    statementBuilder = new StatementBuilder(new SpanRecorder(spans), diagnostics);
  }

  @Test
  void buildsAssignmentToLiteral() {
    String testInput = "x = 5";
    AssignmentExpressionContext ctx = (AssignmentExpressionContext) TestHelper.parseExpr(testInput);
    Assignment expected =
        new Assignment(new Identifier("x"), new IntegerLiteral(BigInteger.valueOf(5)));
    assertEquals(expected, statementBuilder.buildAssignment(ctx));
  }

  @Test
  void buildsAssignmentToExpression() {
    String testInput = "x = y + 1";
    AssignmentExpressionContext ctx = (AssignmentExpressionContext) TestHelper.parseExpr(testInput);
    Assignment expected =
        new Assignment(
            new Identifier("x"),
            new BinaryOp(
                BinaryOp.Op.ADD,
                new Variable(new Identifier("y")),
                new IntegerLiteral(BigInteger.valueOf(1))));
    assertEquals(expected, statementBuilder.buildAssignment(ctx));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "a.b = 5", // field target
        "*p = 5", // dereference target
        "arr[0] = 5", // index target
      })
  void rejectsNonVariableAssignmentTargets(String input) {
    AssignmentExpressionContext ctx = (AssignmentExpressionContext) TestHelper.parseExpr(input);
    assertThrows(UnsupportedConstructException.class, () -> statementBuilder.buildAssignment(ctx));
  }
}
