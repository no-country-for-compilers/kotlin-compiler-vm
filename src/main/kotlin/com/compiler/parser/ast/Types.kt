package com.compiler.parser.ast

/**
 * Представление типа в языке. Может быть примитивным (int/float/bool/void) или составным (массив).
 */
sealed class TypeNode : ASTNode() {
    /** Целочисленный тип (signed 64-bit). */
    object IntType : TypeNode()

    /** Тип с плавающей точкой (double precision). */
    object FloatType : TypeNode()

    /** Булев тип. */
    object BoolType : TypeNode()

    /** Void-тип (используется для функций, не возвращающих значение). */
    object VoidType : TypeNode()

    /**
     * Тип массива.
     *
     * @property elementType тип элементов массива (может быть любым TypeNode, в т.ч. ArrayType для
     * многомерных массивов)
     */
    data class ArrayType(val elementType: TypeNode) : TypeNode()
}
