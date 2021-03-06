/*
    Extension of benchmark 01 where we actually perform some work for each object instance - i.e. we test
    whether its package satisfies some required properties.
*/
var count = 0;
heap.forEachObject(function (it) {
    if (it.clazz.name.startsWith('benchmark.problem')) {
        count += 1;
    }
});
print("Counted instances: "+count);