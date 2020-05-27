package com.oracle.truffle.heap.interop;

import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;

@GenerateLibrary
public abstract class IteratorLibrary extends Library {

    static final LibraryFactory<IteratorLibrary> FACTORY = LibraryFactory.resolve(IteratorLibrary.class);

    public static LibraryFactory<IteratorLibrary> getFactory() {
        return FACTORY;
    }

    public Object next(Object receiver) {
        return null;
    }

}
