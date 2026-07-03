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
 * the verdict. A benchmark with no {@code // cora:} marker has an empty {@link #expectedVerdict()}
 * and is snapshot-only.
 *
 * @param name short identifier (the {@code .rs} file name without extension), used as the test
 *     display name
 * @param rust path to the Rust source
 * @param golden path to the committed golden LCTRS (sibling {@code .lctrs} file)
 * @param expectedVerdict the verdict declared by the source's {@code // cora:} marker, if any
 * @param profile the overflow semantics to translate under, from the source's {@code // profile:}
 *     marker; defaults to {@link Profile#debug} when unmarked
 */
public record Benchmark(
    String name, Path rust, Path golden, Optional<Verdict> expectedVerdict, Profile profile) {

  /**
   * @return the benchmark name, so parameterised tests display it instead of the record dump
   */
  @Override
  public String toString() {
    return name;
  }
}
