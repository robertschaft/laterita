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
 * <p>The annotation may be used only in a mutable class (MUT-10) on
 * non-static methods, and with {@link InheritFrom#RECEIVER} also on
 * non-static, direct inner classes.
 *
 * <p>On a method (MUT-13), a {@code @mutating} method may:
 * <ul>
 *   <li>reassign the receiver's non-{@code final} fields,</li>
 *   <li>mutate through its fields that carry no {@code @fixed},</li>
 *   <li>return mutable borrows of its fields,</li>
 *   <li>call other {@code @mutating} methods on {@code this},</li>
 *   <li>call other non-{@code @mutating} methods on {@code this},</li>
 *   <li>call other {@code @mutating(InheritFrom.RECEIVER)} methods on
 *     {@code this} and assume they behave as {@code @mutating} methods
 *     (return mutable borrows).</li>
 * </ul>
 * A method without {@code @mutating} can do none of these.
 *
 * <p>{@code @mutating} methods can only be called on a mutable receiver
 * (MUT-15).
 *
 * <p>A non-static inner class already borrows its enclosing instance mutably
 * (MUT-50), so plain {@code @mutating} adds nothing there.
 * With {@link InheritFrom#RECEIVER} the enclosing borrow instead follows the
 * {@code this} that constructs the inner instance (MUT-51).
 *
 * <p>The {@link InheritFrom} value chooses the form (MUT-17, MUT-51).
 * {@link InheritFrom#NONE}, the default, is the always-mutating form above.
 * {@link InheritFrom#RECEIVER} behaves as plain {@code @mutating} when the
 * object is effectively mutable at the method site.
 * Otherwise the method is used as a non-mutating method: returned values are
 * treated as non-mutable.
 *
 * <p>A method marked {@code @mutating(InheritFrom.RECEIVER)} may:
 * <ul>
 *   <li>return (potentially) mutable borrows of its receiver's fields,</li>
 *   <li>call other {@code @mutating(InheritFrom.RECEIVER)} and
 *     non-{@code @mutating} methods on {@code this}.</li>
 * </ul>
 *
 * <p>Compile-time only: Laterita attaches no runtime metadata (COMP).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface mutating {

    /**
     * How the receiver mode is determined.
     * Defaults to the always-mutating form.
     */
    InheritFrom value() default InheritFrom.NONE;
}
