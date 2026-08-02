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

import laterita.lang.annotation.*;          // @mut, @fix, @bound, @borrow, @take, @mutating, ...
import static laterita.lang.Intrinsics.give;    // move-out of an array slot (OWN-07)
import static laterita.lang.Intrinsics.broken;  // marks an unfinished path (UNR-01)

/**
 * A resizable-array implementation of a list, carrying Laterita ownership annotations.
 *
 * <p>The backing store {@link #elementData} is a fixed-length array, as in Java and Rust;
 * {@link #size} is the logical length. {@code add} appends within spare capacity and only
 * reallocates a larger array to grow (TODO); {@code remove} shifts elements down within the
 * fixed array and decrements {@code size}, never resizing in place.
 *
 * <p>Iteration comes in two forms, distinguished entirely by the borrow each cursor takes on
 * the list, and legible from the factory signature alone (OWN-00):
 * <ul>
 *   <li>{@link #iterator()} is a plain method, so {@code this} is a shared borrow and the
 *       returned {@link Itr} holds the list {@code @fix}. Any number may run at once, which is
 *       what allows nested read loops over the same list (OWN-03).</li>
 *   <li>{@link #iteratorMut()} is {@code @mutating}, so {@code this} is an exclusive {@code @mut}
 *       borrow the returned {@link MutItr} retains. It is the only live borrow of the list until
 *       dropped, and may {@link MutItr#remove()} elements.</li>
 * </ul>
 *
 * @param <T> the element type
 */
@mut public class ArrayList<T> implements Iterable<T> {

    /** Fixed-length backing store. TODO: {@code grow()} reallocates a larger array in {@code add}. */
    T[] elementData;

    /** Logical element count. Invariant: {@code 0 <= size <= elementData.length}. */
    int size;

    // TODO: constructors (default capacity, initial capacity, from another Collection).
    // TODO: add(@take T e), get(int i) -> @bound T, set, size(), isEmpty(), grow(), ...

    /**
     * Returns a read-only cursor over the list.
     *
     * <p>Plain method: {@code this} is shared, so the returned cursor borrows the list
     * {@code @fix} and several cursors may coexist (OWN-03).
     */
    @Override
    public @bound Iterator<T> iterator() {
        return new Itr();
    }

    /**
     * Returns a mutating cursor over the list.
     *
     * <p>{@code @mutating}: {@code this} is an exclusive {@code @mut} borrow that the returned
     * cursor retains, so it is the sole live borrow of the list for its lifetime (OWN-03).
     */
    public @mutating @bound MutIterator<T> iteratorMut() {
        return new MutItr();
    }
    // TODO: hoist iteratorMut() onto a shared MutIterable<T> super-interface, mirroring Iterable.

    /**
     * Read-only cursor.
     *
     * <p>{@code @mut} for its own advancing {@link #cursor} field, but NOT {@code @mutating}, so
     * it borrows the enclosing {@code ArrayList} shared (MUT-12). The enclosing fields
     * {@link ArrayList#size} and {@link ArrayList#elementData} below are read through that shared
     * borrow, which is why {@link #next()} can only hand out {@code @fix} elements.
     */
    @mut private class Itr implements Iterator<T> {

        /** Index of the next element to return. */
        int cursor;

        @Override
        public boolean hasNext() {
            return cursor < size;   // reads the enclosing size through the shared borrow
        }

        @Override
        public @mutating @fix @bound T next() {
            // TODO: if (cursor >= size) throw new NoSuchElementException();
            // Enclosing borrow is shared => elementData is @fix => element is @fix @bound T.
            var element = elementData[cursor];   // @fix @bound T, bound to the list
            cursor = cursor + 1;                 // mutates this cursor, never the list
            return element;
        }
    }

    /**
     * Mutating cursor.
     *
     * <p>{@code @mutating} makes it borrow the enclosing {@code ArrayList} {@code @mut} and hold
     * it exclusively (MUT-12), which is what licenses {@link #remove()} to restructure the list.
     * It must also be {@code @mut} (MUT-12), covering its own {@link #cursor} and
     * {@link #lastReturned} fields.
     */
    @mutating @mut private class MutItr implements MutIterator<T> {

        /** Index of the next element to return. */
        int cursor;

        /** Index handed out by the last {@link #next()}, or {@code -1} once removed. */
        int lastReturned = -1;

        @Override
        public boolean hasNext() {
            return cursor < size;
        }

        @Override
        public @mutating @bound T next() {
            // TODO: if (cursor >= size) throw new NoSuchElementException();
            // Element mutability rides on T: @mut @bound T when T is @mut, @fix @bound T when @fix.
            lastReturned = cursor;
            var element = elementData[cursor];   // @bound T, bound to the list
            cursor = cursor + 1;
            return element;
        }

        @Override
        public @mutating T remove() {
            // TODO: if (lastReturned < 0) throw new IllegalStateException();
            //
            // Fixed array, no resize. Through the @mut enclosing borrow:
            //   T removed = give(elementData[lastReturned]);          // move the element out, owned
            //   for (int i = lastReturned; i < size - 1; i++)         // shift the tail down by one
            //       elementData[i] = give(elementData[i + 1]);
            //   size = size - 1;                                      // shrink the logical length
            //   cursor = lastReturned;                               // re-visit the shifted-in element
            //   lastReturned = -1;
            //   return removed;
            broken("TODO: ArrayList.MutItr.remove");
        }
    }
}
