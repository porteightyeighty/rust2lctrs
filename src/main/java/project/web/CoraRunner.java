package project.web;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Runs the external <a href="https://github.com/hezzel/cora">Cora</a> binary on an LCTRS for the
 * demo's {@code /v1/analyse} route.
 *
 * <p>The LCTRS is always one the translator produced, never user-supplied text, so the risk is
 * resource exhaustion rather than code execution. Three limits bound it: at most {@code
 * maxConcurrent} analyses run at once (further requests are refused, not queued), each is killed
 * with its whole process tree (Cora's JVM and the Z3 processes it spawns) after {@code timeout},
 * and the captured output is truncated at {@link #MAX_OUTPUT} bytes.
 */
public final class CoraRunner {

  /** Upper bound on the Cora output returned to the client. */
  static final int MAX_OUTPUT = 256 * 1024;

  /** What an analysis came to. */
  public enum Outcome {
    /** Cora ran to completion; {@link Analysis#output()} starts with its verdict. */
    FINISHED,
    /** Cora was still running when the timeout expired and was killed. */
    TIMED_OUT,
    /** Every analysis slot was in use, so Cora was not started. */
    BUSY,
    /** Cora exited without printing a verdict line, or could not be started. */
    FAILED
  }

  /**
   * The result of one analysis.
   *
   * @param outcome how the run ended
   * @param output Cora's combined stdout and stderr (possibly truncated), or a short explanation
   *     when Cora produced none
   * @param millis wall-clock time spent running Cora
   */
  public record Analysis(Outcome outcome, String output, long millis) {}

  private final Path binary;
  private final Duration timeout;
  private final Semaphore slots;
  private final Map<String, String> environment;

  /**
   * @param binary the Cora executable
   * @param timeout how long one analysis may run
   * @param maxConcurrent how many analyses may run at once
   * @param environment extra environment variables for the Cora process (for example {@code
   *     JAVA_OPTS} to cap its heap)
   */
  public CoraRunner(
      Path binary, Duration timeout, int maxConcurrent, Map<String, String> environment) {
    this.binary = binary;
    this.timeout = timeout;
    this.slots = new Semaphore(maxConcurrent);
    this.environment = Map.copyOf(environment);
  }

  /**
   * Builds a runner from the environment: {@code CORA_BIN} (a path, or a command on {@code PATH};
   * default {@code cora}), {@code CORA_TIMEOUT_SECONDS} (default 30), {@code CORA_CONCURRENCY}
   * (default 2) and {@code CORA_JAVA_OPTS} (passed to Cora as {@code JAVA_OPTS}; default {@code
   * -Xmx384m}).
   *
   * @return the runner, or empty if no Cora executable can be found
   */
  public static Optional<CoraRunner> fromEnvironment() {
    Map<String, String> env = System.getenv();
    return resolve(env.getOrDefault("CORA_BIN", "cora"), env.get("PATH"))
        .map(
            bin ->
                new CoraRunner(
                    bin,
                    Duration.ofSeconds(
                        Long.parseLong(env.getOrDefault("CORA_TIMEOUT_SECONDS", "30"))),
                    Integer.parseInt(env.getOrDefault("CORA_CONCURRENCY", "2")),
                    Map.of("JAVA_OPTS", env.getOrDefault("CORA_JAVA_OPTS", "-Xmx384m"))));
  }

  /** Resolves a direct path, or searches {@code path} for a bare command name. */
  static Optional<Path> resolve(String bin, String path) {
    if (bin.contains(File.separator)) {
      Path direct = Path.of(bin);
      return Files.isExecutable(direct) ? Optional.of(direct) : Optional.empty();
    }
    if (path == null) {
      return Optional.empty();
    }
    for (String dir : path.split(File.pathSeparator)) {
      if (!dir.isEmpty() && Files.isExecutable(Path.of(dir, bin))) {
        return Optional.of(Path.of(dir, bin));
      }
    }
    return Optional.empty();
  }

  /** The configured per-analysis time limit. */
  public Duration timeout() {
    return timeout;
  }

  /**
   * Analyses an LCTRS for termination under call-by-value, the strategy the encoding targets.
   *
   * @param lctrs the serialised LCTRS
   * @return the analysis; never throws for a failed or slow Cora run
   * @throws InterruptedException if the calling thread is interrupted while waiting
   */
  public Analysis analyse(String lctrs) throws InterruptedException {
    if (!slots.tryAcquire()) {
      return new Analysis(Outcome.BUSY, "", 0);
    }
    long started = System.nanoTime();
    Path input = null;
    Path captured = null;
    try {
      input = Files.createTempFile("r2l-", ".lctrs");
      captured = Files.createTempFile("r2l-", ".out");
      Files.writeString(input, lctrs, StandardCharsets.UTF_8);

      ProcessBuilder builder =
          new ProcessBuilder(binary.toString(), "--strategy", "cbv", input.toString())
              .redirectErrorStream(true)
              .redirectOutput(captured.toFile());
      builder.environment().putAll(environment);
      Process process = builder.start();

      boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (!finished) {
        killTree(process);
      }
      String output = readCapped(captured);
      long millis = (System.nanoTime() - started) / 1_000_000;
      if (!finished) {
        return new Analysis(Outcome.TIMED_OUT, output, millis);
      }
      return startsWithVerdict(output)
          ? new Analysis(Outcome.FINISHED, output, millis)
          : new Analysis(Outcome.FAILED, output, millis);
    } catch (IOException e) {
      long millis = (System.nanoTime() - started) / 1_000_000;
      return new Analysis(Outcome.FAILED, "could not run Cora: " + e.getMessage(), millis);
    } finally {
      deleteQuietly(input);
      deleteQuietly(captured);
      slots.release();
    }
  }

  /** Kills the process and everything it spawned (Cora starts Z3 as a child process). */
  private static void killTree(Process process) throws InterruptedException {
    process.descendants().forEach(ProcessHandle::destroyForcibly);
    process.destroyForcibly();
    process.waitFor(5, TimeUnit.SECONDS);
  }

  /** Whether Cora's first non-blank line starts with one of its verdicts. */
  static boolean startsWithVerdict(String output) {
    return output
        .lines()
        .map(String::strip)
        .filter(line -> !line.isEmpty())
        .findFirst()
        .map(line -> line.split("\\s+", 2)[0])
        .filter(first -> first.equals("YES") || first.equals("MAYBE") || first.equals("NO"))
        .isPresent();
  }

  private static String readCapped(Path file) throws IOException {
    try (InputStream in = Files.newInputStream(file)) {
      byte[] bytes = in.readNBytes(MAX_OUTPUT);
      String text = new String(bytes, StandardCharsets.UTF_8);
      return in.read() == -1 ? text : text + "\n[output truncated]\n";
    }
  }

  private static void deleteQuietly(Path file) {
    if (file == null) {
      return;
    }
    try {
      Files.deleteIfExists(file);
    } catch (IOException ignored) {
      // A leftover temp file is harmless; the analysis result matters more.
    }
  }
}
