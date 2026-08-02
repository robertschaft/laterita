/*
 * Copyright (c) 2024, the Laterita project. Distributed under GPL v2 with the
 * Classpath Exception, the same terms as OpenJDK. See the LICENSE file.
 */
package laterita.lang.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares receiver mutation.
 *
 * <p>On a method (MUT-08): the method may mutate {@code this}, and may be called only on a
 * {@code @mut} receiver (MUT-10). On a non-static inner class (MUT-12): the class holds a
 * {@code @mut} borrow of its enclosing instance.
 *
 * <p>The {@link InheritFrom} value chooses between the always-mutating form and the
 * receiver-inherited form (MUT-13). {@link InheritFrom#NONE}, the default, is always-mutating.
 * {@link InheritFrom#RECEIVER} makes the receiver mode, and any {@code @bound} return's
 * mutability, inherit the actual receiver: mutating over a {@code @mut} receiver, read-only over
 * a {@code @fix} or shared one.
 *
 * <p>Compile-time only: Laterita attaches no runtime metadata (COMP).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface mutating {

    /** How the receiver mode is determined. Defaults to the always-mutating form. */
    InheritFrom value() default InheritFrom.NONE;
}
