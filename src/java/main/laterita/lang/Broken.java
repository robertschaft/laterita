/*
 * Copyright (c) 2024, the Laterita project. Distributed under GPL v2 with the
 * Classpath Exception, the same terms as OpenJDK. See the LICENSE file.
 */
package laterita.lang;

/**
 * The {@link UncompilableException} for a path that has no implementation
 * (UNR-02).
 *
 * <p>The factories are normally statically imported, so a call site reads
 * {@code throw broken("files cannot be copied");}.
 * Reaching the construction is a compile-time error (UNR-01).
 */
public final class Broken extends UncompilableException {

    private static final long serialVersionUID = 1L;

    private Broken() {
        super();
    }

    private Broken(String reason) {
        super(reason);
    }

    /** Returns a {@code Broken} with no reason. */
    public static Broken broken() {
        return new Broken();
    }

    /** Returns a {@code Broken} carrying {@code reason}. */
    public static Broken broken(String reason) {
        return new Broken(reason);
    }
}
