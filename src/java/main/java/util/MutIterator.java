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
 * the OpenJDK source. See doc/laterita-spec.md (MUT-12, STD-08, OWN-00).
 */
package java.util;

import laterita.lang.annotation.*;   // @mut, @fix, @bound, @take, @mutating, ...

/**
 * A mutating cursor over a collection. Laterita addition alongside the read-only
 * {@link Iterator} (STD-08).
 *
 * A {@code MutIterator} is obtained from a {@code @mutating} factory such as
 * {@link ArrayList#iteratorMut()}, so it holds an EXCLUSIVE {@code @mut} borrow of the
 * underlying collection: at most one is live at a time, and the collection is frozen to
 * every other access until the cursor is dropped (OWN-03). This exclusivity is what
 * licenses {@link #remove()} to restructure the collection.
 *
 * <p>{@link #next()} yields an element borrow whose mutability rides on the element type
 * {@code T}: {@code @mut @bound T} when {@code T} is {@code @mut}, {@code @fix @bound T}
 * when {@code T} is {@code @fix}. {@code remove()} is the one capability this adds over
 * the read-only {@link Iterator}, and it is available for any {@code T} because removal
 * restructures the collection without mutating the element.
 */
@mut public interface MutIterator<T> {

    /** Returns {@code true} if the iteration has more elements. */
    boolean hasNext();

    /**
     * Returns the next element as a borrow bound to the underlying collection.
     * The borrow's mutability is inherited from the element type {@code T}.
     */
    @mutating @bound T next();

    /**
     * Removes from the underlying collection the last element returned by {@link #next()}
     * and returns it as an owned value. Structural mutation, permitted because the cursor
     * holds the collection {@code @mut} (MUT-12).
     */
    @mutating T remove();
}
