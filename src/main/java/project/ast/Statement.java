package project.ast;

/** Marker interface for statement nodes that can appear inside a {@link Block}. */
public sealed interface Statement extends Node
    permits Let, If, Assignment, Return, While, Loop, Break, Continue {}
