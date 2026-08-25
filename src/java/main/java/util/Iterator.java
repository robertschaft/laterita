/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * Laterita fork: ownership, borrow, and mutability annotations added on top of
 * the OpenJDK source. See doc/laterita-spec.md (STD-08, MUT-17).
 */
package java.util;

import laterita.lang.annotation.*;
// broken: marks an unsupported path (UNR-01)
import static laterita.lang.Broken.broken;

/**
 * A read-or-update cursor over a collection.
 * The distinction between a read cursor and an in-place-update cursor is not
 * two types: it is which mutability this instance inherited from the
 * {@link Iterable#iterator()} receiver that produced it (MUT-17).
 * Structural {@code set} and {@code add} live on {@link ListIterator}.
 * {@code remove()} is declared here for source compatibility with
 * {@code java.util.Iterator} but is broken by default.
 *
 * @param <T> the element type
 */
public interface Iterator<T> {

    /** Returns {@code true} if the iteration has more elements. */
    boolean hasNext();

    /**
     * Advances and returns the next element as a borrow bound to this
     * cursor.
     *
     * <p>Not {@code @readonly}: it advances this cursor.
     * The returned {@code @bound T}'s mutability was settled when the cursor
     * was created: {@code @bound T} for a cursor built from a mutable
     * collection, {@code @fixed @bound T} for one built from a {@code @fixed}
     * collection.
     */
    @bound T next();

    /**
     * Removes the last element returned by {@link #next()}, present for source
     * compatibility with {@code java.util.Iterator}.
     * A plain read cursor cannot remove, so this is broken by default
     * (STD-08): calling it is a compile error unless overridden, as
     * {@link ListIterator} does with the working form.
     */
    default T remove() {
        throw broken(
                "Iterator.remove: obtain a ListIterator to remove elements");
    }
}
