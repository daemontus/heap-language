package com.oracle.truffle.heap;

import com.oracle.truffle.api.interop.*;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import org.netbeans.api.annotations.common.NonNull;
import org.netbeans.lib.profiler.heap.Heap;
import org.netbeans.lib.profiler.heap.Instance;
import org.netbeans.lib.profiler.heap.JavaClass;

import java.util.Iterator;

/**
 * A native object provided by {@link HeapLanguage} to communicate with a memory-mapped {@link Heap}.
 * The API of the object is given by the OQL language specification.
 */
@ExportLibrary(InteropLibrary.class)
final class ObjectHeap implements TruffleObject {

    private static final String FOR_EACH_CLASS = "forEachClass";
    private static final String FOR_EACH_OBJECT = "forEachObject";
    private static final String FIND_CLASS = "findClass";
    private static final String FIND_OBJECT = "findObject";
    private static final String CLASSES = "classes";
    private static final String OBJECTS = "objects";
    private static final String FINALIZABLES = "finalizables";
    private static final String LIVEPATHS = "livepaths";
    private static final String ROOTS = "roots";

    private static final MemberDescriptor MEMBERS = MemberDescriptor.functions(
        FOR_EACH_CLASS, FOR_EACH_OBJECT, FIND_CLASS, FIND_OBJECT, CLASSES, OBJECTS, FINALIZABLES, LIVEPATHS, ROOTS
    );

    @NonNull
    private final Heap heap;

    public ObjectHeap(@NonNull Heap heap) {
        this.heap = heap;
    }

    @NonNull
    public Heap getHeap() {
        return heap;
    }

    /// The object is structured so that the actual implementation is first and all the interop code is at the end.

    /* Calls a callback function for each Java Class. The callback can return a boolean, and iteration is stopped
     * if this value is true. */
    private Object invoke_forEachClass(Object[] arguments)
            throws  ArityException,
            UnsupportedTypeException,
            UnsupportedMessageException
    {
        Args.checkArity(arguments, 1);
        TruffleObject callback = Args.unwrapExecutable(arguments, 0);
        InteropLibrary interop = InteropLibrary.getFactory().getUncached();
        //noinspection unchecked
        for (JavaClass javaClass : (Iterable<JavaClass>) this.heap.getAllClasses()) {
            Boolean stop = Types.tryAsBoolean(interop.execute(callback, ObjectJavaClass.create(javaClass)));
            if (stop != null && stop) break;
        }
        return this;
    }

    /* Calls a callback function for each Java object. Three arguments: callback, clazz and includeSubtypes.
     *  - clazz is the class whose instances are selected. If not specified, defaults to java.lang.Object.
     *  - includeSubtypes is a boolean flag that specifies whether to include subtype instances or not.
     *  Default value of this flag is true.
     */
    private Object invoke_forEachObject(Object[] arguments)
            throws ArityException,
            UnsupportedMessageException,
            UnsupportedTypeException
    {
        Args.checkArityBetween(arguments, 1, 3);
        TruffleObject callback = Args.unwrapExecutable(arguments, 0);

        JavaClass javaClass;
        if (arguments.length == 1) {    // set class to default: Object
            javaClass = HeapUtils.findClass(heap, "java.lang.Object");
        } else {
            javaClass = HeapLanguage.unwrapJavaClassArgument(arguments, 1, this.heap);
        }

        boolean includeSubtypes = true;
        if (arguments.length == 3) {
            includeSubtypes = Args.unwrapBoolean(arguments, 2);
        }

        InteropLibrary interop = InteropLibrary.getFactory().getUncached();
        Iterator<Instance> items = HeapUtils.getInstances (heap, javaClass, includeSubtypes);
        while (items.hasNext()) {
            Instance instance = items.next();
            interop.execute(callback, ObjectInstance.create(instance));
        }
        return this;
    }

    /* Finds Java Class of given name. */
    private Object invoke_findClass(Object[] arguments) throws ArityException, UnsupportedTypeException {
        Args.checkArity(arguments, 1);
        return ObjectJavaClass.create(HeapUtils.findClass(heap, Args.unwrapString(arguments, 0)));
    }

    /* Finds object from given object id. */
    private Object invoke_findObject(Object[] arguments) throws ArityException, UnsupportedTypeException {
        Args.checkArity(arguments, 1);
        long id = HeapLanguage.unwrapObjectIdArgument(arguments, 0);
        return ObjectInstance.create(heap.getInstanceByID(id));
    }

    /* Returns an enumeration of all Java classes. */
    private Object invoke_classes(Object[] arguments) throws ArityException {
        Args.checkArity(arguments, 0);
        //noinspection unchecked
        Iterator<JavaClass> it = heap.getAllClasses().iterator();
        return Iterators.exportIterator(new Iterator<Object>() {
            @Override
            public boolean hasNext() {
                return it.hasNext();
            }

            @Override
            public Object next() {
                return ObjectJavaClass.create(it.next());
            }
        });
    }

    /* Returns an enumeration of Java objects. Three arguments: clazz, includeSubtypes and filter.
     *  - clazz is the class whose instances are selected. If not specified, defaults to java.lang.Object.
     *  - includeSubtypes is a boolean flag that specifies whether to include subtype instances or not.
     *  Default value of this flag is true.
     *  - An optional filter expression to filter the result set of objects.
     */
    private Object invoke_objects(Object[] arguments)
            throws ArityException,
            UnsupportedTypeException {
        Args.checkArityBetween(arguments, 0, 3);

        JavaClass javaClass;
        if (arguments.length == 0) {
            javaClass = HeapUtils.findClass(heap, "java.lang.Object");
        } else {
            javaClass = HeapLanguage.unwrapJavaClassArgument(arguments, 0, heap);
        }

        boolean includeSubtypes = true;
        if (arguments.length >= 2) {
            includeSubtypes = Args.unwrapBoolean(arguments, 1);
        }

        TruffleObject filter = null;
        if (arguments.length >= 3) {
            filter = HeapLanguage.unwrapCallbackArgument(arguments, 2, "it");
        }

        final Iterator<Instance> instances = HeapUtils.getInstances(this.heap, javaClass, includeSubtypes);
        if (filter == null) {
            return Iterators.exportIterator(new IteratorObjectInstance(instances));
        } else {
            final TruffleObject finalFilter = filter;
            InteropLibrary interop = InteropLibrary.getFactory().getUncached();
            return Iterators.exportIterator(new IteratorFilter<Instance>(instances) {
                @Override
                public Object check(Instance item) {
                    try {
                        TruffleObject value = ObjectInstance.create(item);
                        Object isValid = interop.execute(finalFilter, value);
                        return Types.asBoolean(isValid) ? value : null;
                    } catch (UnsupportedTypeException | ArityException | UnsupportedMessageException e) {
                        throw new IllegalStateException("Cannot execute filter callback.", e);
                    }
                }
            });
        }
    }

    private Object invoke_finalizables(Object[] arguments) throws ArityException {
        Args.checkArity(arguments, 0);
        Iterator<Instance> instances = HeapUtils.getFinalizerObjects(heap);
        return Iterators.exportIterator(new IteratorObjectInstance(instances));
    }

    private Object invoke_livepaths(Object[] arguments) {
        throw new IllegalStateException("Unimplemented.");   // TODO

    }

    private Object invoke_roots(Object[] arguments) {
        throw new IllegalStateException("Unimplemented.");   // TODO
    }

    @ExportMessage
    static boolean hasMembers(@SuppressWarnings("unused") ObjectHeap receiver) {
        return true;
    }

    @ExportMessage
    static boolean isMemberInvocable(@SuppressWarnings("unused") ObjectHeap receiver, String member) {
        return MEMBERS.hasFunction(member);
    }

    @ExportMessage
    static Object getMembers(
            @SuppressWarnings("unused") ObjectHeap receiver,
            @SuppressWarnings("unused") boolean includeInternal
    ) {
        return MEMBERS;
    }

    @ExportMessage
    static boolean isMemberReadable(@SuppressWarnings("unused") ObjectHeap receiver, String member) {
        // Invokable members with no arguments can be also seen as properties...
        // (if you are a very creative person...)
        switch (member) {
            case FINALIZABLES:
            case OBJECTS:
            case CLASSES:
                return true;
            default:
                return false;
        }
    }

    @ExportMessage
    static Object readMember(ObjectHeap receiver, String member) throws UnknownIdentifierException {
        try {
            switch (member) {
                case FINALIZABLES:
                    return receiver.invoke_finalizables(new Object[0]);
                case CLASSES:
                    return receiver.invoke_classes(new Object[0]);
                case OBJECTS:
                    return receiver.invoke_objects(new Object[0]);
                default:
                    throw UnknownIdentifierException.create(member);
            }
        } catch (ArityException | UnsupportedTypeException e) {
            throw new RuntimeException(e);  // should be unreachable
        }
    }

    @ExportMessage
    static Object invokeMember(
            ObjectHeap receiver, String member, Object[] arguments
    ) throws ArityException,
            UnsupportedTypeException,
            UnknownIdentifierException,
            UnsupportedMessageException
    {
        switch (member) {
            case FOR_EACH_CLASS:
                return receiver.invoke_forEachClass(arguments);
            case FOR_EACH_OBJECT:
                return receiver.invoke_forEachObject(arguments);
            case FIND_CLASS:
                return receiver.invoke_findClass(arguments);
            case FIND_OBJECT:
                return receiver.invoke_findObject(arguments);
            case CLASSES:
                return receiver.invoke_classes(arguments);
            case OBJECTS:
                return receiver.invoke_objects(arguments);
            case FINALIZABLES:
                return receiver.invoke_finalizables(arguments);
            case LIVEPATHS:
                return receiver.invoke_livepaths(arguments);
            case ROOTS:
                return receiver.invoke_roots(arguments);
            default:
                throw UnknownIdentifierException.create(member);
        }
    }

}
