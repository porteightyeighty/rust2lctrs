package project.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import project.parser.RustParser.CrateContext;

public class CrateBuilderTest {

  private SpanTable spans;
  private DiagnosticRecorder diagnostics;
  private AstBuilder astBuilder;

  @BeforeEach
  void setUp() {
    spans = new SpanTable();
    diagnostics = new DiagnosticRecorder();
    astBuilder = new AstBuilder(spans, diagnostics);
  }

  @Test
  void buildsCrateWithSingleFunction() {
    String testInput = "fn x() -> i32 { return 0; }";
    CrateContext crateContext = TestHelper.parseCrate(testInput);
    Block block = new Block(List.of(new Return(new IntegerLiteral(BigInteger.valueOf(0)))));

    Crate expected =
        new Crate(
            List.of(
                new FunctionDeclaration(
                    new Identifier("x"), List.of(), block, Optional.of(Type.Int.i32))));
    Crate actual = astBuilder.buildCrate(crateContext);
    assertEquals(expected, actual);
  }

  @Test
  void buildsCrateWithMultipleFunctions() {
    String testInput = "fn f() -> i32 { return 0; } fn g(a: i32) -> i32 { return 1; }";
    CrateContext crateContext = TestHelper.parseCrate(testInput);
    Crate actual = astBuilder.buildCrate(crateContext);
    assertEquals(2, actual.items().size());
    assertEquals(List.of(), diagnostics.diagnostics());
  }

  @Test
  void keepsUnitReturningFunctionAndItsBody() {
    String testInput = "fn main() { println!(\"hi\"); } fn f() -> i32 { return 0; }";
    CrateContext crateContext = TestHelper.parseCrate(testInput);
    Crate actual = astBuilder.buildCrate(crateContext);
    assertEquals(2, actual.items().size());
    FunctionDeclaration mainFn = (FunctionDeclaration) actual.items().get(0);
    assertEquals(new Identifier("main"), mainFn.identifier());
    assertEquals(Optional.empty(), mainFn.returnType());
    assertEquals(1, diagnostics.diagnostics().size());
  }

  @Test
  void unitTailExpressionBuildsEmptyReturn() {
    String testInput = "fn f() { () }";
    CrateContext crateContext = TestHelper.parseCrate(testInput);
    Crate actual = astBuilder.buildCrate(crateContext);
    assertEquals(List.of(), diagnostics.diagnostics());
    FunctionDeclaration fn = (FunctionDeclaration) actual.items().get(0);
    assertEquals(Optional.empty(), fn.returnType());
    assertEquals(new Block(List.of(new Return(Optional.empty()))), fn.block());
  }

  @Test
  void explicitUnitReturnSameAsOmitted() {
    String testInput = "fn main() -> () { return; } fn f() -> i32 { return 0; }";
    CrateContext crateContext = TestHelper.parseCrate(testInput);
    Crate actual = astBuilder.buildCrate(crateContext);
    assertEquals(2, actual.items().size());
    FunctionDeclaration mainFn = (FunctionDeclaration) actual.items().get(0);
    assertEquals(new Identifier("main"), mainFn.identifier());
    assertEquals(Optional.empty(), mainFn.returnType());
    assertEquals(List.of(), diagnostics.diagnostics());
  }
}
