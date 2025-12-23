package com.compiler.vm

import com.compiler.bytecode.Value
import com.compiler.memory.RcOperandStack
import com.compiler.memory.MemoryManager

/**
 * Interface for integrating JIT compiler with virtual machine.
 */
interface JITCompilerInterface {
    /**
     * Record function call for profiling.
     * Called by VM on each CALL instruction.
     */
    fun recordCall(functionName: String)
    
    /**
     * Get compiled version of function if ready.
     * Returns null if function is not yet compiled.
     */
    fun getCompiled(functionName: String): CompiledFunctionExecutor?
    
    /**
     * Check if JIT is enabled.
     */
    fun isEnabled(): Boolean
}

/**
 * Interface for executing JIT-compiled function.
 */
interface CompiledFunctionExecutor {
    /**
     * Execute compiled function.
     * 
     * @param frame Call frame for the function
     * @param stack Operand stack for the VM
     * @param memoryManager Memory manager for the VM
     * @return VMResult.SUCCESS on successful execution, otherwise error code
     */
    fun execute(frame: CallFrame, stack: RcOperandStack, memoryManager: MemoryManager): VMResult
}

