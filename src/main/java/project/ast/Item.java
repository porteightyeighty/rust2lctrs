package project.ast;

/** Marker interface for top-level items that can appear directly inside a {@link Crate}. */
public sealed interface Item permits FunctionDef {}
