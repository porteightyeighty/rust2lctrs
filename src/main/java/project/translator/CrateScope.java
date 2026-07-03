package project.translator;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import project.translator.Translator.Signature;

/**
 * Crate-wide state shared by every function's {@link Context}.
 *
 * @param counter the program-point counter, shared so {@code u}-symbols stay globally unique
 * @param registry the function name to {@link Signature} map for resolving calls
 */
record CrateScope(AtomicInteger counter, Map<String, Signature> registry, Profile profile) {}
