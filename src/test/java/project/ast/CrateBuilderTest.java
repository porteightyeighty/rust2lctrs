package project.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import project.parser.RustParser;

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
    RustParser.CrateContext crateContext = TestHelper.parseCrate(testInput);
    BodyBlock block = new BodyBlock(List.of(), new Return(new IntLit(0)));

    Crate expected =
        new Crate(
            List.of(new FunctionDeclaration(new Identifier("x"), List.of(), block, Type.Int.i32)));
    Crate actual = astBuilder.buildCrate(crateContext);
    assertEquals(expected, actual);
  }
}
