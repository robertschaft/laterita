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
 * the OpenJDK source. See doc/laterita-spec.md (STD-08, MUT-12, MUT-13, OWN-00).
 */
package java.util;

import laterita.lang.annotation.*;          // @mut, @fix, @bound, @borrow, @take, @mutating, InheritFrom, ...
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
 * <p>Iteration is a single method. {@link #iterator()} is
 * {@code @mutating(InheritFrom.RECEIVER)} (MUT-13), so the cursor inherits the list's mutability:
 * over a {@code @mut} list it lends {@code @mut @bound T} and holds an exclusive borrow, over a
 * {@code @fix} list (e.g. {@code fix(list)}) it lends {@code @fix @bound T} and holds a shared
 * borrow so several coexist. The enhanced-for consumes exactly this, with no cursor selection.
 * Structural modification ({@code remove}/{@code set}/{@code add}) is the separate
 * {@link #listIterator()}, which a for-each never reaches.
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
     * Returns a cursor whose mutability is inherited from this list (MUT-13). One method serves
     * both read and in-place-update iteration; the two are its monomorphizations.
     */
    @Override
    public @mutating(InheritFrom.RECEIVER) @bound Iterator<T> iterator() {
        return new Itr();
    }

    /**
     * Returns a structural cursor. Always {@code @mutating}, so it takes an exclusive {@code @mut}
     * borrow (never inherited): {@code remove}/{@code set}/{@code add} always mutate the list.
     */
    public @mutating @bound ListIterator<T> listIterator() {
        return new ListItr();
    }

    /**
     * The read-or-update cursor.
     *
     * <p>Class-level {@code @mutating(InheritFrom.RECEIVER)} (MUT-12, MUT-13): the borrow it holds
     * on the enclosing {@code ArrayList} is inherited from the {@code this} that constructs it, so
     * it is exclusive when built from a {@code @mut} list and shared when built from a {@code @fix}
     * one. {@code @mut} covers its own {@link #cursor} field. Method-level {@code @mutating} on
     * {@link #next()} is the ordinary receiver mutation of advancing that cursor.
     */
    @mutating(InheritFrom.RECEIVER) @mut private class Itr implements Iterator<T> {

        /** Index of the next element to return. */
        int cursor;

        @Override
        public boolean hasNext() {
            return cursor < size;   // reads the enclosing size through the inherited borrow
        }

        @Override
        public @mutating @bound T next() {
            // TODO: if (cursor >= size) throw new NoSuchElementException();
            // Element mutability is inherited from the enclosing borrow (MUT-13):
            // @mut @bound T over a @mut list, @fix @bound T over a @fix one.
            var element = elementData[cursor];
            cursor = cursor + 1;    // mutates this cursor, never the list
            return element;
        }
    }

    /**
     * The structural cursor.
     *
     * <p>Class-level {@code @mutating} (not inherited): it always borrows the enclosing
     * {@code ArrayList} {@code @mut} and exclusively, which is what licenses {@link #remove()} to
     * restructure the list (MUT-12).
     */
    @mutating @mut private class ListItr implements ListIterator<T> {

        int cursor;
        int lastReturned = -1;

        @Override public boolean hasNext()      { return cursor < size; }
        @Override public boolean hasPrevious()  { return cursor > 0; }
        @Override public int     nextIndex()    { return cursor; }
        @Override public int     previousIndex(){ return cursor - 1; }

        @Override
        public @mutating @bound T next() {
            // TODO: if (cursor >= size) throw new NoSuchElementException();
            lastReturned = cursor;
            var element = elementData[cursor];   // @mut @bound T: the enclosing borrow is @mut
            cursor = cursor + 1;
            return element;
        }

        @Override
        public @mutating @bound T previous() {
            // TODO: if (cursor <= 0) throw new NoSuchElementException();
            cursor = cursor - 1;
            lastReturned = cursor;
            return elementData[cursor];
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
            //   if (lastReturned < cursor) cursor = cursor - 1;
            //   lastReturned = -1;
            //   return removed;
            broken("TODO: ArrayList.ListItr.remove");
        }

        @Override
        public @mutating void set(@take T e) {
            // TODO: if (lastReturned < 0) throw new IllegalStateException();
            // elementData[lastReturned] = e;   // replace through the @mut enclosing borrow
            broken("TODO: ArrayList.ListItr.set");
        }

        @Override
        public @mutating void add(@take T e) {
            // TODO: grow if size == elementData.length; shift tail up from cursor; insert e;
            //       size = size + 1; cursor = cursor + 1; lastReturned = -1.
            broken("TODO: ArrayList.ListItr.add");
        }
    }
}
