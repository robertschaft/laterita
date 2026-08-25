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
 * the OpenJDK source.
 * See doc/laterita-spec.md (STD-08, MUT-17, MUT-50, MUT-51, OWN-00).
 */
package java.util;

// @fixed, @bound, @borrow, @take, @readonly, InheritFrom, ...
import laterita.lang.annotation.*;
// give: move-out of an array slot (OWN-07)
import static laterita.lang.Intrinsics.give;
// broken: marks an unfinished path (UNR-01)
import static laterita.lang.Intrinsics.broken;

/**
 * A resizable-array implementation of a list, carrying Laterita ownership
 * annotations.
 *
 * <p>The backing store {@link #elementData} is a fixed-length array, as in Java
 * and Rust, and {@link #size} is the logical length.
 * {@code add} appends within spare capacity and only reallocates a larger array
 * to grow (TODO).
 * {@code remove} shifts elements down within the fixed array and decrements
 * {@code size}, never resizing in place.
 *
 * <p>Iteration is a single method.
 * {@link #iterator()} is {@code @readonly(InheritFrom.RECEIVER)} (MUT-17), so
 * the cursor inherits the list's mutability: over a mutable list it lends
 * {@code @bound T} and holds an exclusive borrow, over a {@code @fixed} list
 * (e.g. {@code fixed(list)}) it lends {@code @fixed @bound T} and holds a
 * shared borrow so several coexist.
 * The enhanced-for consumes exactly this, with no cursor selection.
 * Structural modification ({@code remove}/{@code set}/{@code add}) is the
 * separate {@link #listIterator()}, which a for-each never reaches.
 *
 * @param <T> the element type
 */
public class ArrayList<T> implements Iterable<T> {

    /**
     * Fixed-length backing store.
     * A bare field is mutated through (MUT-21), which the element writes in
     * {@link ListItr} require.
     * TODO: {@code grow()} reallocates a larger array in {@code add}.
     */
    T[] elementData;

    /**
     * Logical element count.
     * Invariant: {@code 0 <= size <= elementData.length}.
     */
    int size;

    // TODO: constructors (default capacity, initial capacity, from another
    //       Collection).
    // TODO: add(@take T e), get(int i) -> @bound T, set, size(), isEmpty(),
    //       grow(), ...

    /**
     * Returns a cursor whose mutability is inherited from this list (MUT-17).
     * One method serves both read and in-place-update iteration, and the two
     * are its monomorphizations.
     */
    @Override
    public @readonly(InheritFrom.RECEIVER) @bound Iterator<T> iterator() {
        return new Itr();
    }

    /**
     * Returns a structural cursor.
     * Never {@code @readonly}, so it takes an exclusive borrow (never
     * inherited): {@code remove}/{@code set}/{@code add} always mutate the
     * list.
     */
    public @bound ListIterator<T> listIterator() {
        return new ListItr();
    }

    /**
     * The read-or-update cursor.
     *
     * <p>Class-level {@code @readonly(InheritFrom.RECEIVER)} (MUT-50, MUT-51):
     * the borrow it holds on the enclosing {@code ArrayList} is inherited from
     * the {@code this} that constructs it, so it is exclusive when built from a
     * mutable list and shared when built from a {@code @fixed} one.
     * The class is mutable, which covers its own {@link #cursor} field.
     * The absence of {@code @readonly} on {@link #next()} is the ordinary
     * receiver mutation of advancing that cursor.
     */
    @readonly(InheritFrom.RECEIVER)
    private class Itr implements Iterator<T> {

        /** Index of the next element to return. */
        int cursor;

        @Override
        public boolean hasNext() {
            // reads the enclosing size through the inherited borrow
            return cursor < size;
        }

        @Override
        public @bound T next() {
            // TODO: if (cursor >= size) throw new NoSuchElementException();
            // Element mutability is inherited from the enclosing borrow
            // (MUT-17):
            // @bound T over a mutable list, @fixed @bound T over a
            // @fixed one.
            var element = elementData[cursor];
            cursor = cursor + 1;    // mutates this cursor, never the list
            return element;
        }
    }

    /**
     * The structural cursor.
     *
     * <p>No class-level {@code InheritFrom.RECEIVER}: it takes the default
     * enclosing borrow, mutable and exclusive, which is what licenses
     * {@link #remove()} to restructure the list (MUT-50).
     */
    private class ListItr implements ListIterator<T> {

        int cursor;
        int lastReturned = -1;

        @Override public boolean hasNext()      { return cursor < size; }
        @Override public boolean hasPrevious()  { return cursor > 0; }
        @Override public int     nextIndex()    { return cursor; }
        @Override public int     previousIndex(){ return cursor - 1; }

        @Override
        public @bound T next() {
            // TODO: if (cursor >= size) throw new NoSuchElementException();
            lastReturned = cursor;
            // @bound T: the enclosing borrow is mutable
            var element = elementData[cursor];
            cursor = cursor + 1;
            return element;
        }

        @Override
        public @bound T previous() {
            // TODO: if (cursor <= 0) throw new NoSuchElementException();
            cursor = cursor - 1;
            lastReturned = cursor;
            return elementData[cursor];
        }

        @Override
        public T remove() {
            // TODO: if (lastReturned < 0) throw new IllegalStateException();
            //
            // Fixed array, no resize.
            // Through the mutable enclosing borrow, move the element out
            // owned, shift the tail down by one, and shrink the logical
            // length:
            //   T removed = give(elementData[lastReturned]);
            //   for (int i = lastReturned; i < size - 1; i++)
            //       elementData[i] = give(elementData[i + 1]);
            //   size = size - 1;
            //   if (lastReturned < cursor) cursor = cursor - 1;
            //   lastReturned = -1;
            //   return removed;
            return broken("TODO: ArrayList.ListItr.remove");
        }

        @Override
        public void set(@take T e) {
            // TODO: if (lastReturned < 0) throw new IllegalStateException();
            // Replace through the mutable enclosing borrow:
            //   elementData[lastReturned] = e;
            broken("TODO: ArrayList.ListItr.set");
        }

        @Override
        public void add(@take T e) {
            // TODO: grow if size == elementData.length, shift the tail up
            //       from cursor, then insert e:
            //         size = size + 1; cursor = cursor + 1;
            //         lastReturned = -1;
            broken("TODO: ArrayList.ListItr.add");
        }
    }
}
