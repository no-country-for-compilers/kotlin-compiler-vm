package com.compiler.bytecode

import com.compiler.parser.ast.TypeNode

/**
 * Information about a function parameter in bytecode.
 */
data class ParameterInfo(
    val name: String,
    val type: TypeNode
)

/**
 * Compiled function in bytecode.
 */
data class CompiledFunction(
    val name: String,
    val parameters: List<ParameterInfo>,
    val returnType: TypeNode,
    val localsCount: Int,
    val instructions: ByteArray
)

/**
 * Bytecode module representing a compiled program.
 */
data class BytecodeModule(
    val intConstants: List<Long>,
    val floatConstants: List<Double>,
    val functions: List<CompiledFunction>,
    val entryPoint: String
)

