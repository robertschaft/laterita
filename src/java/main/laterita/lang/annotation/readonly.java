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
 * Declares that a method does not modify its receiver.
 *
 * <p>A method may mutate its receiver unless it carries this annotation
 * (MUT-13).
 *
 * <p>A {@code @readonly} method may not:
 * <ul>
 *   <li>reassign the receiver's non-{@code final} fields,</li>
 *   <li>mutate through its fields that carry no {@code @ro},</li>
 *   <li>return mutable borrows of its fields,</li>
 *   <li>call a mutating method on {@code this}.</li>
 * </ul>
 * It may call other {@code @readonly} methods on {@code this}.
 *
 * <p>A method without {@code @readonly} is callable only on a mutable
 * receiver (MUT-15).
 * On an immutable class every method is {@code @readonly} and writing it is
 * redundant (MUT-10).
 *
 * <p>On a non-static inner class the annotation states the same one level
 * out: the class holds a shared borrow of its enclosing instance rather than
 * a mutable one (MUT-50).
 *
 * <p>The {@link InheritFrom} value chooses the form (MUT-17, MUT-51).
 * {@link InheritFrom#NONE}, the default, is the always-read-only form above.
 * {@link InheritFrom#RECEIVER} behaves as {@code @readonly} when the receiver
 * is not mutable at the method site, and as a mutating method when it is.
 *
 * <p>A method marked {@code @readonly(InheritFrom.RECEIVER)} may:
 * <ul>
 *   <li>return (potentially) mutable borrows of its receiver's fields,</li>
 *   <li>call other {@code @readonly(InheritFrom.RECEIVER)} and
 *     {@code @readonly} methods on {@code this}.</li>
 * </ul>
 *
 * <p>Compile-time only: Laterita attaches no runtime metadata (COMP).
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.SOURCE)
public @interface readonly {

    /** Selects the always-read-only or the receiver-inherited form. */
    InheritFrom value() default InheritFrom.NONE;
}
