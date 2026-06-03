package project.lctrs;

import java.util.Objects;
import java.util.Optional;

/**
 * A constrained rewrite rule {@code l -> r [φ]}.
 *
 * @param lhs the left-hand side {@code l}
 * @param rhs the right-hand side {@code r}
 * @param constraint the constraint {@code φ}, or empty for an unconstrained rule
 */
public record Rule(Term lhs, Term rhs, Optional<Constraint> constraint) {
  public Rule {
    lhs = Objects.requireNonNull(lhs);
    rhs = Objects.requireNonNull(rhs);
    constraint = Objects.requireNonNull(constraint);
  }
}
