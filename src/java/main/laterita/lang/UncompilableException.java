/*
 * Copyright (c) 2024, the Laterita project. Distributed under GPL v2 with the
 * Classpath Exception, the same terms as OpenJDK. See the LICENSE file.
 */
package laterita.lang;

/**
 * The supertype of the exceptions that declare a path unreachable (UNR-01).
 *
 * <p>It is a compile-time error to construct one on a path the compiler
 * cannot prove dead, so a Laterita-compiled program never throws one.
 * The reason string reaches the diagnostic.
 *
 * <p>A subclass names a narrower reason in its own type.
 * {@link Broken} is the general one.
 *
 * <p>The type exists because {@code javac} has to accept the source as
 * ordinary Java (COMP-06): {@code throw} is the construct it understands as
 * ending a path, and a source compiled by plain {@code javac} reports the
 * reason at run time instead.
 */
public abstract class UncompilableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Constructs one with no reason. */
    protected UncompilableException() {
        super();
    }

    /** Constructs one carrying {@code reason}. */
    protected UncompilableException(String reason) {
        super(reason);
    }
}
