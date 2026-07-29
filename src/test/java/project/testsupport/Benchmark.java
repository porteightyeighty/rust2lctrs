package project.testsupport;

import java.nio.file.Path;
import java.util.Optional;
import project.translator.IntegerSemantics;

/**
 * One entry in the shared benchmark corpus: a Rust source file paired with its committed golden
 * LCTRS and, optionally, the termination verdict Cora is expected to return.
 *
 * <p>The same benchmark drives both test layers. The snapshot layer pins {@link #golden()} against
 * a fresh translation of {@link #rust()}; the e2e layer feeds that same golden to Cora and checks
 * the verdict. A benchmark whose {@code // <semantics>:} marker carries no verdict has an empty
 * {@link #expectedVerdict()} and is snapshot-only. A single {@code .rs} may produce one benchmark
 * per variant it is marked for.
 *
 * @param name short identifier (the {@code .rs} file name without extension), shared by the
 *     variants of a source and used as the golden's file name
 * @param rust path to the Rust source
 * @param golden path to the committed golden LCTRS ({@code <semantics>/<name>.lctrs} under the
 *     corpus)
 * @param expectedVerdict the verdict declared by the source's {@code // <semantics>:} marker, if
 *     any
 * @param semantics the integer semantics to translate under, from the marker this variant came
 *     from; {@link IntegerSemantics#debug} for an unmarked source
 */
public record Benchmark(
    String name,
    Path rust,
    Path golden,
    Optional<Verdict> expectedVerdict,
    IntegerSemantics semantics) {

  /**
   * @return name and semantics, so parameterised tests display a unique label per variant instead
   *     of the record dump
   */
  @Override
  public String toString() {
    return name + " [" + semantics + "]";
  }
}
