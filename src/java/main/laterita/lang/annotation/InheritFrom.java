/*
 * Copyright (c) 2024, the Laterita project. Distributed under GPL v2 with the
 * Classpath Exception, the same terms as OpenJDK. See the LICENSE file.
 */
package laterita.lang.annotation;

/**
 * Selects where an ownership annotation takes its mode from, instead of stating it outright.
 *
 * <p>Used as the value of {@code @mutating} (MUT-13), and reserved for the same role on other
 * axes should they adopt it (for example {@code @mut(InheritFrom.RECEIVER)} or
 * {@code @own(InheritFrom.RECEIVER)}), which is why it is a shared enum rather than a bare flag.
 */
public enum InheritFrom {

    /** The annotation carries its own, fixed meaning. The default. */
    NONE,

    /**
     * Inherit the mode from the receiver.
     *
     * <p>Two variants of the annotated method or class are generated: one as if this annotation is
     * active and another with it inactive. The variant aligned with the static mutability of the
     * object (receiver) at the method's call site is used (MUT-13).
     */
    RECEIVER
}
