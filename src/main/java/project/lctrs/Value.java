package project.lctrs;

/** A theory value: a constant of the underlying theory, either an integer or a boolean. */
public sealed interface Value extends Term permits IntValue, BoolValue {

  /**
   * Renders the plain value held by this object, e.g. {@code 42} for an integer value.
   *
   * @return the plain value held by this object.
   */
  String render();
}
