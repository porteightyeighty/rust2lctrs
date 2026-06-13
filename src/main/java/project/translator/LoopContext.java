package project.translator;

import java.util.ArrayList;
import java.util.List;
import project.lctrs.Term;

public class LoopContext {

  private Term continueTarget;
  private List<Term> breakPoints = new ArrayList<>();

  public LoopContext(Term continueTarget) {
    this.continueTarget = continueTarget;
  }

  public void addBreakPoint(Term breakTarget) {
    this.breakPoints.add(breakTarget);
  }

  public Term continueTarget() {
    return continueTarget;
  }

  public List<Term> breakPoints() {
    return List.copyOf(breakPoints);
  }
}
