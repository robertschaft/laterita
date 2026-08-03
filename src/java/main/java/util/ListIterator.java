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
 * the OpenJDK source. See doc/laterita-spec.md (STD-08).
 */
package java.util;

import laterita.lang.annotation.*;

/**
 * A structural cursor: it can {@code remove}, {@code set}, and {@code add} as well as traverse.
 *
 * <p>Obtained from a {@code @mutating listIterator()} rather than the enhanced-for's
 * {@link Iterable#iterator()}, so it always holds an exclusive {@code @mut} borrow of the
 * collection, never an inherited one, because structural modification always mutates the
 * collection (STD-08). An enhanced-for never reaches this type, matching the fact that a
 * for-each exposes no handle to remove.
 *
 * @param <T> the element type
 */
@mut public interface ListIterator<T> extends Iterator<T> {

    /** Returns {@code true} if there is an element in the reverse direction. */
    boolean hasPrevious();

    /** Advances backward and returns the previous element as a borrow bound to the collection. */
    @mutating @bound T previous();

    /** Index of the element a subsequent {@link #next()} would return. */
    int nextIndex();

    /** Index of the element a subsequent {@link #previous()} would return. */
    int previousIndex();

    /**
     * Removes the last element returned by {@link #next()} or {@link #previous()} and returns it
     * as an owned value. Statement-form {@code it.remove();} drops the value via {@code onDrop}
     * (DROP-01), matching Java's void-returning {@code remove}.
     */
    @mutating T remove();

    /** Replaces the last element returned by {@link #next()} or {@link #previous()}. */
    @mutating void set(@take T e);

    /** Inserts an element before the one a subsequent {@link #next()} would return. */
    @mutating void add(@take T e);
}
