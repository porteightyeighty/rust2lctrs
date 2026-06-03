package project.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import project.parser.RustParser.CrateContext;

public class CrateBuilderTest {

  private SpanTable spans;
  private AstBuilder astBuilder;

  @BeforeEach
  void setUp() {
    spans = new SpanTable();
    astBuilder = new AstBuilder(spans);
  }

  @Test
  void buildsCrateWithSingleFunction() {
    String testInput = "fn x() -> i32 { return 0; }";
    CrateContext crateContext = TestHelper.parseCrate(testInput);
    BodyBlock block =
        new BodyBlock(List.of(), new Return(new IntegerLiteral(BigInteger.valueOf(0))));

    Crate expected =
        new Crate(
            List.of(new FunctionDeclaration(new Identifier("x"), List.of(), block, Type.Int.i32)));
    Crate actual = astBuilder.buildCrate(crateContext);
    assertEquals(expected, actual);
  }

  @Test
  void rejectsCrateWithMultipleFunctions() {
    String testInput = "fn f() -> i32 { return 0; } fn g(a: i32) -> i32 { return 1; }";
    CrateContext crateContext = TestHelper.parseCrate(testInput);
    assertThrows(UnsupportedConstructException.class, () -> astBuilder.buildCrate(crateContext));
  }
}
