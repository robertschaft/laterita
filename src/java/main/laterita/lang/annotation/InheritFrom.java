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

    /** State the mode outright: the annotation carries its own, fixed meaning. The default. */
    NONE,

    /**
     * Inherit the mode from the receiver. A declaration so marked is polymorphic in the
     * receiver's mutability: it behaves as the plain annotation on a {@code @fix} or shared
     * receiver and as the mutating form on a {@code @mut} receiver, and a {@code @bound} return
     * inherits the receiver's mutability (MUT-13). Monomorphized once per receiver mutability.
     */
    RECEIVER
}
