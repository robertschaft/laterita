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
 * the OpenJDK source. See doc/laterita-spec.md (STD-08, MUT-13).
 */
package java.util;

import laterita.lang.annotation.*;

/**
 * A read-or-update cursor over a collection. The distinction between a read cursor and an
 * in-place-update cursor is not two types: it is which mutability this instance inherited from
 * the {@link Iterable#iterator()} receiver that produced it (MUT-13). Structural modification
 * ({@code remove}, {@code set}, {@code add}) is not here but on {@link ListIterator}.
 *
 * @param <T> the element type
 */
@mut public interface Iterator<T> {

    /** Returns {@code true} if the iteration has more elements. */
    boolean hasNext();

    /**
     * Advances and returns the next element as a borrow bound to the collection.
     *
     * <p>{@code @mutating} advances this cursor. The returned {@code @bound T}'s mutability was
     * fixed when the cursor was created: {@code @mut @bound T} for a cursor built from a
     * {@code @mut} collection, {@code @fix @bound T} for one built from a {@code @fix} collection.
     */
    @mutating @bound T next();
}
