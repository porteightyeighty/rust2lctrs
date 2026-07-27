package project.testsupport;

import java.nio.file.Path;
import java.util.Optional;
import project.translator.Profile;

/**
 * One entry in the shared benchmark corpus: a Rust source file paired with its committed golden
 * LCTRS and, optionally, the termination verdict Cora is expected to return.
 *
 * <p>The same benchmark drives both test layers. The snapshot layer pins {@link #golden()} against
 * a fresh translation of {@link #rust()}; the e2e layer feeds that same golden to Cora and checks
 * the verdict. A benchmark whose {@code // <profile>:} marker carries no verdict has an empty
 * {@link #expectedVerdict()} and is snapshot-only. A single {@code .rs} may produce one benchmark
 * per profile it is marked for.
 *
 * @param name short identifier (the {@code .rs} file name without extension), shared by the profile
 *     variants of a source and used as the golden's file name
 * @param rust path to the Rust source
 * @param golden path to the committed golden LCTRS ({@code <profile>/<name>.lctrs} under the
 *     corpus)
 * @param expectedVerdict the verdict declared by the source's {@code // <profile>:} marker, if any
 * @param profile the overflow semantics to translate under, selected by which {@code // <profile>:}
 *     marker this variant came from; {@link Profile#debug} for an unmarked source
 */
public record Benchmark(
    String name, Path rust, Path golden, Optional<Verdict> expectedVerdict, Profile profile) {

  /**
   * @return name and profile, so parameterised tests display a unique label per variant instead of
   *     the record dump
   */
  @Override
  public String toString() {
    return name + " [" + profile + "]";
  }
}
