package project.lctrs;

import java.util.Objects;

/**
 * The constraint φ of a rule {@code l -> r [φ]}: a boolean-sorted term over the theory signature.
 *
 * @param formula the constraint term; must be of sort {@link Sort#BOOL}
 */
public record Constraint(Term formula) {
  public Constraint {
    Objects.requireNonNull(formula);
    if (formula.sort() != Sort.BOOL) {
      throw new IllegalArgumentException(
          "Constraint formula must be boolean sort, got " + formula.sort());
    }
  }
}
