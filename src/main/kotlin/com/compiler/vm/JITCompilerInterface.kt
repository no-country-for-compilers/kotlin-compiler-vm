package com.compiler.vm

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
     * @param args Function arguments (in call order)
     * @param operandStack Operand stack for returning result
     * @return VMResult.SUCCESS on successful execution, otherwise error code
     */
    fun execute(args: List<com.compiler.bytecode.Value>, operandStack: com.compiler.memory.RcOperandStack): VMResult
}

