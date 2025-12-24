package com.compiler.memory

class Heap {
    private val objects = mutableMapOf<Int, HeapObject>()
    private var nextId: Int = 1

    fun allocIntArray(size: Int): Int {
        require(size >= 0) { "Array size must be non-negative, got $size" }
        val id = nextId++
        objects[id] = IntArrayObject(LongArray(size), refCount = 0)
        return id
    }

    fun allocFloatArray(size: Int): Int {
        require(size >= 0) { "Array size must be non-negative, got $size" }
        val id = nextId++
        objects[id] = FloatArrayObject(DoubleArray(size), refCount = 0)
        return id
    }

    fun allocBoolArray(size: Int): Int {
        require(size >= 0) { "Array size must be non-negative, got $size" }
        val id = nextId++
        objects[id] = BoolArrayObject(BooleanArray(size), refCount = 0)
        return id
    }

    fun get(id: Int): HeapObject =
        objects[id] ?: throw IllegalStateException("Invalid heapId=$id (object not found)")

    fun exists(id: Int): Boolean = objects.containsKey(id)

    fun free(id: Int) {
        if (!objects.containsKey(id)) {
            throw IllegalStateException("Double free or invalid free for heapId=$id")
        }
        objects.remove(id)
    }

    fun objectCount(): Int = objects.size

    /**
     * Clear all objects from the heap.
     * Used when VM shuts down to free all remaining memory.
     */
    fun clearAll() {
        objects.clear()
    }
}
