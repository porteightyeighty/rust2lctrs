package project.lctrs;

import java.util.ArrayList;
import java.util.List;

public class Lctrs {

  List<Rule> rules;

  public Lctrs() {
    this.rules = new ArrayList<>();
  }

  public Lctrs appendRule(Rule rule) {
    this.rules.add(rule);
    return this;
  }

  public Lctrs appendRules(List<Rule> rules) {
    this.rules.addAll(rules);
    return this;
  }
}
