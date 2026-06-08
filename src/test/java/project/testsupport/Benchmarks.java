package project.testsupport;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Discovers the shared benchmark corpus under {@code src/test/resources/benchmarks}.
 *
 * <p>The corpus is read from the source tree (not the classpath) so that the golden files the
 * snapshot layer writes in update mode land in the git-tracked originals rather than in a copy
 * under {@code target/}. Tests run with the module root as their working directory, so the relative
 * path resolves.
 */
public final class Benchmarks {

  /**
   * Source-tree location of the corpus, relative to the module root (the test working directory).
   */
  public static final Path DIR = Path.of("src", "test", "resources", "benchmarks");

  /** Marker that declares a benchmark's expected Cora verdict, e.g. {@code // cora: YES}. */
  private static final String VERDICT_MARKER = "// cora:";

  private Benchmarks() {}

  /**
   * Loads every benchmark in the corpus, ordered by name.
   *
   * @return one {@link Benchmark} per {@code .rs} file, each paired with its sibling {@code .lctrs}
   *     golden (which need not exist yet) and parsed verdict marker
   * @throws UncheckedIOException if the corpus directory cannot be read
   */
  public static List<Benchmark> all() {
    try (Stream<Path> entries = Files.list(DIR)) {
      return entries
          .filter(p -> p.getFileName().toString().endsWith(".rs"))
          .sorted()
          .map(Benchmarks::load)
          .toList();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read benchmark corpus at " + DIR.toAbsolutePath(), e);
    }
  }

  private static Benchmark load(Path rust) {
    String fileName = rust.getFileName().toString();
    String name = fileName.substring(0, fileName.length() - ".rs".length());
    Path golden = rust.resolveSibling(name + ".lctrs");
    return new Benchmark(name, rust, golden, readVerdict(rust));
  }

  /**
   * Reads the {@code // cora: <verdict>} marker from a source file, if present. Scans the whole
   * file (not just the first line) so the marker can sit wherever reads most naturally.
   */
  private static Optional<Verdict> readVerdict(Path rust) {
    try {
      return Files.readAllLines(rust).stream()
          .map(String::strip)
          .filter(line -> line.startsWith(VERDICT_MARKER))
          .map(line -> line.substring(VERDICT_MARKER.length()).strip().toUpperCase(Locale.ROOT))
          .map(Verdict::valueOf)
          .findFirst();
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read benchmark source " + rust, e);
    }
  }
}
