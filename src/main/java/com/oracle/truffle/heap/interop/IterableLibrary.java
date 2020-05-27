package com.oracle.truffle.heap.interop;

import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;

@GenerateLibrary
public abstract class IterableLibrary extends Library {

    public boolean isIterable(Object receiver) {
        return false;
    }

    public Object getIterator(Object receiver) throws UnsupportedMessageException {
        throw UnsupportedMessageException.create();
    }

}
