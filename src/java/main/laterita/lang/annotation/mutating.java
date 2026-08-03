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
 * <p>On a method (MUT-08), a {@code @mutating} method may:
 * <ul>
 *   <li>reassign the receiver's non-{@code final} fields,</li>
 *   <li>mutate through its {@code @mut} fields, and return mutable borrows of them,</li>
 *   <li>call other {@code @mutating} methods on {@code this}.</li>
 * </ul>
 * A method without {@code @mutating} can do none of these. It may be declared only on a
 * {@code @mut} class and called only on a {@code @mut} receiver (MUT-10).
 *
 * <p>On a non-static inner class (MUT-12), {@code @mutating} instead declares that the class holds
 * a {@code @mut} borrow of its enclosing instance, so its methods may also mutate that enclosing
 * instance. Such a class must itself be {@code @mut} and sit inside a {@code @mut} class.
 *
 * <p>The {@link InheritFrom} value chooses the form (MUT-13). {@link InheritFrom#NONE}, the
 * default, is the always-mutating form above. {@link InheritFrom#RECEIVER} behaves as plain
 * {@code @mutating} when the method is called on a {@code @mut} object and as a non-mutating method
 * otherwise, and a {@code @bound} return inherits the receiver's mutability the same way.
 *
 * <p>Compile-time only: Laterita attaches no runtime metadata (COMP).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface mutating {

    /** How the receiver mode is determined. Defaults to the always-mutating form. */
    InheritFrom value() default InheritFrom.NONE;
}
