package com.compiler.vm

import com.compiler.bytecode.CompiledFunction
import com.compiler.memory.RcLocals

/**
 * Call frame contains all information needed to execute a function.
 *
 * @param function Compiled function
 * @param locals Local variables + parameters
 * @param pc Program Counter (byte offset)
 * @param returnAddress PC of the calling function (for RETURN)
 */
data class CallFrame(
    val function: CompiledFunction,
    val locals: RcLocals,
    var pc: Int,
    val returnAddress: Int?
)

