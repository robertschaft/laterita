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
 * <p>The annotation may be used only in a {@code @mut} class on
 * <ul>
 *   <li>non-static methods or</li>
 *   <li>non-static, direct inner classes.</li>
 * </ul>
 *
 * <p>On a method (MUT-08), a {@code @mutating} method may:
 * <ul>
 *   <li>reassign the receiver's non-{@code final} fields,</li>
 *   <li>mutate through its {@code @mut} fields,</li>
 *   <li>return mutable borrows of its fields,</li>
 *   <li>call other {@code @mutating} methods on {@code this},</li>
 *   <li>call other non-{@code @mutating} methods on {@code this},</li>
 *   <li>call other {@code @mutating(InheritFrom.RECEIVER)} methods on {@code this} and assume they
 *     behave as {@code @mutating} methods (return mutable borrows).</li>
 * </ul>
 * A method without {@code @mutating} can do none of these.
 *
 * <p>{@code @mutating} methods can only be called on a {@code @mut} receiver (MUT-10).
 *
 * <p>On a non-static inner class (MUT-12), {@code @mutating} instead declares that the class holds a {@code @mut} borrow of its enclosing instance, so its methods may also mutate that enclosing instance.
 * Both, inner and enclosing class, must be {@code @mut}.
 *
 * <p>The {@link InheritFrom} value chooses the form (MUT-13).
 * {@link InheritFrom#NONE}, the default, is the always-mutating form above.
 * {@link InheritFrom#RECEIVER} behaves as plain {@code @mutating} when the object is effectively mutable at the method site.
 * Otherwise the method is used as a non-mutating method: returned values are treated as non-mutable.
 *
 * <p>A method marked {@code @mutating(InheritFrom.RECEIVER)} may:
 * <ul>
 *   <li>return (potentially) mutable borrows of its receiver's fields,</li>
 *   <li>call other {@code @mutating(InheritFrom.RECEIVER)} and non-{@code @mutating} methods on {@code this}.</li>
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
