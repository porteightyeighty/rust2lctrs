package project.testsupport;

/**
 * The termination verdict Cora prints on its first output line. Shared between the e2e layer (which
 * obtains it by running Cora) and the benchmark metadata (which records the expected one).
 */
public enum Verdict {
  /** Termination proof found. */
  YES,
  /** No proof found; non-termination not established. */
  MAYBE,
  /** Non-termination proof found. */
  NO,
  /** Output did not start with a recognised verdict. */
  UNKNOWN
}
