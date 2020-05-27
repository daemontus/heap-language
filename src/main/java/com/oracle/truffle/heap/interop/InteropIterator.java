package com.oracle.truffle.heap.interop;

import com.oracle.truffle.api.interop.ArityException;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;

import java.util.Iterator;

/**
 * <p>Exposes Java iterator as a lazy truffle array. Currently, truffle does not have an iterator interface
 * so this is basically the best we can do unless we want to emulate each language individually.</p>
 *
 * <p>If you want to access the values in a truly lazy way, we also export the {@code hasNext()/next()} methods
 * as defined by Java {@link Iterator}.</p>
 */
@ExportLibrary(InteropLibrary.class)
public final class InteropIterator<T> implements TruffleObject, Iterator<T> {

    private static final String HAS_NEXT = "hasNext";
    private static final String NEXT = "next";

    private static final MemberDescriptor MEMBERS = MemberDescriptor.functions(HAS_NEXT, NEXT);

    private final Iterator<T> iterator;
    private int nextIndex = 0;

    public InteropIterator(Iterator<T> iterator) {
        this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {
        return this.iterator.hasNext();
    }

    @Override
    public T next() {
        T item = this.iterator.next();
        nextIndex += 1; // do not increment if next() throws
        return item;
    }

    @ExportMessage
    public static boolean hasArrayElements(@SuppressWarnings("unused") InteropIterator<?> receiver) {
        return true;
    }

    @ExportMessage
    public static long getArraySize(InteropIterator<?> receiver) {
        return receiver.iterator.hasNext() ? receiver.nextIndex + 1 : receiver.nextIndex;
    }

    @ExportMessage
    public static boolean isArrayElementReadable(InteropIterator<?> receiver, long index) {
        return receiver.iterator.hasNext() && index == receiver.nextIndex;
    }

    @ExportMessage
    public static Object readArrayElement(InteropIterator<?> receiver, long index) {
        if (!isArrayElementReadable(receiver, index)) {
            throw new ArrayIndexOutOfBoundsException((int) index);
        }
        return receiver.next();
    }

    @ExportMessage
    static boolean hasMembers(@SuppressWarnings("unused") InteropIterator<?> receiver) {
        return true;
    }

    @ExportMessage
    static Object getMembers(@SuppressWarnings("unused") InteropIterator<?> receiver,
                             @SuppressWarnings("unused") boolean includeInternal) {
        return InteropIterator.MEMBERS;
    }

    @ExportMessage
    static boolean isMemberInvocable(@SuppressWarnings("unused") InteropIterator<?> receiver, String member) {
        return MEMBERS.hasFunction(member);
    }

    @ExportMessage
    static Object invokeMember(InteropIterator<?> receiver, String member, Object[] arguments)
            throws ArityException, UnknownIdentifierException
    {
        Args.checkArity(arguments, 0);
        switch (member) {
            case NEXT:
                return receiver.next();
            case HAS_NEXT:
                return receiver.hasNext();
            default:
                throw UnknownIdentifierException.create(member);
        }
    }

}
