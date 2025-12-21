package com.compiler.memory

import com.compiler.bytecode.Value

class RefCountGC(private val heap: Heap) {

    fun retain(value: Value) {
        val ref = value as? Value.ArrayRef ?: return
        val obj = heap.get(ref.heapId)
        obj.refCount += 1
    }

    fun release(value: Value) {
        val ref = value as? Value.ArrayRef ?: return
        val obj = heap.get(ref.heapId)

        obj.refCount -= 1
        if (obj.refCount < 0) {
            throw IllegalStateException("Negative refCount for heapId=${ref.heapId}")
        }

        if (obj.refCount == 0) {
            // For future objects with references, recursive release on children can be implemented here.
            heap.free(ref.heapId)
        }
    }
}
