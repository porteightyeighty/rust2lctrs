package project.translator;

import java.util.ArrayList;
import java.util.List;
import project.lctrs.Term;

/**
 * Per-loop translation state for one enclosing loop. Records where a {@code continue} jumps to (the
 * loop's incoming configuration) and the set of configurations at which a {@code break} leaves the
 * loop, so the merge point reached after the loop can be wired up once the body is lowered.
 */
public class LoopContext {

  private Term continueTarget;
  private List<Term> breakPoints = new ArrayList<>();

  /**
   * Creates a loop context with the configuration a {@code continue} jumps back to.
   *
   * @param continueTarget the configuration reached on a {@code continue}
   */
  public LoopContext(Term continueTarget) {
    this.continueTarget = continueTarget;
  }

  /**
   * Records a configuration at which control breaks out of this loop.
   *
   * @param breakTarget the configuration at the {@code break} site
   */
  public void addBreakPoint(Term breakTarget) {
    this.breakPoints.add(breakTarget);
  }

  /**
   * Returns the configuration a {@code continue} in this loop jumps back to.
   *
   * @return the continue target
   */
  public Term continueTarget() {
    return continueTarget;
  }

  /**
   * Returns an immutable snapshot of the configurations at which control breaks out of this loop.
   *
   * @return the break-site configurations, in the order they were recorded
   */
  public List<Term> breakPoints() {
    return List.copyOf(breakPoints);
  }
}
