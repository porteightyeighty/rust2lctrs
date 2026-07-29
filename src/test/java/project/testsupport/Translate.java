package project.testsupport;

import project.ast.AstBuilder;
import project.ast.Crate;
import project.ast.DiagnosticRecorder;
import project.ast.SpanTable;
import project.lctrs.Serialiser;
import project.lctrs.Simplifier;
import project.parser.RustParsing;
import project.translator.IntegerSemantics;
import project.translator.Translator;

/**
 * Drives the full parse → AST → translate → serialise pipeline on a Rust source string, so the
 * snapshot and e2e layers feed on exactly what the real tool would produce. Mirrors the chain in
 * {@code project.cli.Rust2LctrsCommand}; kept in test support because the tests live outside that
 * package and only see public API.
 */
public final class Translate {

  private Translate() {}

  /**
   * Translates Rust source to its serialised LCTRS form under the default debug semantics.
   *
   * @param source the Rust source text (assumed valid, in-scope Rust)
   * @return the LCTRS rendered in Cora's input format
   */
  public static String toLctrs(String source) {
    return toLctrs(source, IntegerSemantics.debug);
  }

  /**
   * Translates Rust source to its serialised LCTRS form under chosen integer semantics.
   *
   * @param source the Rust source text (assumed valid, in-scope Rust)
   * @param semantics the integer semantics to encode
   * @return the LCTRS rendered in Cora's input format
   */
  public static String toLctrs(String source, IntegerSemantics semantics) {
    DiagnosticRecorder diagnostics = new DiagnosticRecorder();
    SpanTable spanTable = new SpanTable();
    AstBuilder astBuilder = new AstBuilder(spanTable, diagnostics);
    Crate crate = astBuilder.buildCrate(RustParsing.parse(source));
    if (!diagnostics.diagnostics().isEmpty()) {
      throw new IllegalArgumentException(
          "Source is out of scope; expected valid, in-scope Rust but got diagnostics: "
              + diagnostics.diagnostics());
    }
    return Serialiser.serialise(
        Simplifier.simplify(new Translator(crate, spanTable, semantics).translate()));
  }
}
