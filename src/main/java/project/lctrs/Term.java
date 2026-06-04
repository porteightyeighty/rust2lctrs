package project.lctrs;

/**
 * A term over the LCTRS signature: a variable, a theory value, or a function application. Terms
 * form the building blocks of rules and constraints.
 */
public sealed interface Term permits FnApp, VarDecl, Value {

  /** Returns the sort of the term. */
  Sort sort();
}
