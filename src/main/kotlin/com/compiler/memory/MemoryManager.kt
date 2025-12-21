package com.compiler.memory

import com.compiler.bytecode.Value

class MemoryManager(
) {
    // Keep gc and heap synchronized
    private val realHeap: Heap = Heap()
    private val realGc: RefCountGC = RefCountGC(realHeap)

    // Create with refCount=1: ownership is given to VM
    fun newIntArray(size: Int): Value.ArrayRef {
        val id = realHeap.allocIntArray(size)
        val ref = Value.ArrayRef(id)
        realGc.retain(ref)
        return ref
    }

    fun newFloatArray(size: Int): Value.ArrayRef {
        val id = realHeap.allocFloatArray(size)
        val ref = Value.ArrayRef(id)
        realGc.retain(ref)
        return ref
    }

    fun newBoolArray(size: Int): Value.ArrayRef {
        val id = realHeap.allocBoolArray(size)
        val ref = Value.ArrayRef(id)
        realGc.retain(ref)
        return ref
    }

    fun intArrayLoad(ref: Value.ArrayRef, index: Int): Long {
        val obj = realHeap.get(ref.heapId)
        val arr = (obj as? IntArrayObject)
            ?: throw IllegalStateException("heapId=${ref.heapId} is not int[]")
        checkIndex(index, arr.elements.size)
        return arr.elements[index]
    }

    fun intArrayStore(ref: Value.ArrayRef, index: Int, value: Long) {
        val obj = realHeap.get(ref.heapId)
        val arr = (obj as? IntArrayObject)
            ?: throw IllegalStateException("heapId=${ref.heapId} is not int[]")
        checkIndex(index, arr.elements.size)
        arr.elements[index] = value
    }

    fun floatArrayLoad(ref: Value.ArrayRef, index: Int): Double {
        val obj = realHeap.get(ref.heapId)
        val arr = (obj as? FloatArrayObject)
            ?: throw IllegalStateException("heapId=${ref.heapId} is not float[]")
        checkIndex(index, arr.elements.size)
        return arr.elements[index]
    }

    fun floatArrayStore(ref: Value.ArrayRef, index: Int, value: Double) {
        val obj = realHeap.get(ref.heapId)
        val arr = (obj as? FloatArrayObject)
            ?: throw IllegalStateException("heapId=${ref.heapId} is not float[]")
        checkIndex(index, arr.elements.size)
        arr.elements[index] = value
    }

    fun boolArrayLoad(ref: Value.ArrayRef, index: Int): Boolean {
        val obj = realHeap.get(ref.heapId)
        val arr = (obj as? BoolArrayObject)
            ?: throw IllegalStateException("heapId=${ref.heapId} is not bool[]")
        checkIndex(index, arr.elements.size)
        return arr.elements[index]
    }

    fun boolArrayStore(ref: Value.ArrayRef, index: Int, value: Boolean) {
        val obj = realHeap.get(ref.heapId)
        val arr = (obj as? BoolArrayObject)
            ?: throw IllegalStateException("heapId=${ref.heapId} is not bool[]")
        checkIndex(index, arr.elements.size)
        arr.elements[index] = value
    }

    fun retain(v: Value) = realGc.retain(v)
    fun release(v: Value) = realGc.release(v)

    fun debugRefCount(ref: Value.ArrayRef): Int = realHeap.get(ref.heapId).refCount
    fun debugHeapObjectCount(): Int = realHeap.objectCount()

    /**
     * Determine array type.
     * @return "int", "float", "bool" or null if not an array
     */
    fun getArrayType(ref: Value.ArrayRef): String? {
        val obj = realHeap.get(ref.heapId)
        return when (obj) {
            is IntArrayObject -> "int"
            is FloatArrayObject -> "float"
            is BoolArrayObject -> "bool"
            else -> null
        }
    }

    /**
     * Get array size.
     */
    fun getArraySize(ref: Value.ArrayRef): Int {
        val obj = realHeap.get(ref.heapId)
        return when (obj) {
            is IntArrayObject -> obj.elements.size
            is FloatArrayObject -> obj.elements.size
            is BoolArrayObject -> obj.elements.size
            else -> throw IllegalStateException("heapId=${ref.heapId} is not an array")
        }
    }

    private fun checkIndex(index: Int, size: Int) {
        if (index < 0 || index >= size) {
            throw IndexOutOfBoundsException("Index $index out of bounds for size $size")
        }
    }
}
