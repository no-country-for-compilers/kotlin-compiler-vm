package com.compiler.memory

sealed interface HeapObject {
    var refCount: Int
    val sizeBytes: Int
}

data class IntArrayObject(
    val elements: LongArray,
    override var refCount: Int = 0
) : HeapObject {
    override val sizeBytes: Int get() = elements.size * java.lang.Long.BYTES
}

data class FloatArrayObject(
    val elements: DoubleArray,
    override var refCount: Int = 0
) : HeapObject {
    override val sizeBytes: Int get() = elements.size * java.lang.Double.BYTES
}

data class BoolArrayObject(
    val elements: BooleanArray,
    override var refCount: Int = 0
) : HeapObject {
    override val sizeBytes: Int get() = elements.size * java.lang.Byte.BYTES
}