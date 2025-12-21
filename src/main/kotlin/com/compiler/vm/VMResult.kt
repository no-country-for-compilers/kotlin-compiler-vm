package com.compiler.vm

/**
 * Return codes for virtual machine error handling.
 */
enum class VMResult {
    SUCCESS,                    // Successful execution
    DIVISION_BY_ZERO,           // Division by zero
    ARRAY_INDEX_OUT_OF_BOUNDS,  // Array index out of bounds
    STACK_UNDERFLOW,            // Not enough values on stack
    INVALID_OPCODE,             // Invalid opcode
    INVALID_HEAP_ID,            // Invalid heapId
    NEGATIVE_REF_COUNT,         // Negative refCount
    INVALID_FUNCTION_INDEX,     // Invalid function index
    INVALID_CONSTANT_INDEX,     // Invalid constant index
    INVALID_LOCAL_INDEX,        // Invalid local variable index
    INVALID_ARRAY_TYPE,         // Invalid array type
    INVALID_VALUE_TYPE,         // Invalid value type
    CALL_STACK_OVERFLOW,        // Call stack overflow
    OPERAND_STACK_OVERFLOW      // Operand stack overflow
}

