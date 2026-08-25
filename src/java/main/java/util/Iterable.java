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
 * the OpenJDK source. See doc/laterita-spec.md (STD-08, MUT-17, OWN-00).
 */
package java.util;

import laterita.lang.annotation.*;

/**
 * Implemented by any type that can be the source of an enhanced-for.
 *
 * @param <T> the element type
 */
public interface Iterable<T> {

    /**
     * Returns a cursor over the elements.
     *
     * <p>{@code @readonly(InheritFrom.RECEIVER)}: the cursor inherits this
     * collection's mutability (MUT-17).
     * Over a mutable collection it is a mutable cursor holding an exclusive
     * borrow, and {@code next()} lends {@code @bound T}.
     * Over a {@code @ro} collection it is a read cursor holding a shared
     * borrow, and {@code next()} lends {@code @ro @bound T}, so several coexist
     * (OWN-03).
     * There is no separate mutable factory: the two forms are the
     * monomorphizations of this one method.
     */
    @readonly(InheritFrom.RECEIVER) @bound Iterator<T> iterator();
}
