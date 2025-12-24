package com.compiler.vm

import com.compiler.bytecode.*
import com.compiler.memory.*
import com.compiler.vm.jit.BytecodeOptimizerJIT

/**
 * Virtual machine for executing bytecode.
 * Uses interpretation for sequential instruction execution.
 */
class VirtualMachine(
    private val module: BytecodeModule,
    private val jitCompiler: JITCompilerInterface? = null
) {
    private val operandStack: RcOperandStack
    private val callStack: ArrayDeque<CallFrame> = ArrayDeque()
    private val memoryManager: MemoryManager = MemoryManager()

    init {
        operandStack = RcOperandStack(memoryManager)
    }

    /**
     * Get the number of objects currently in the heap.
     * Useful for testing heap cleanup.
     */
    fun getHeapObjectCount(): Int = memoryManager.debugHeapObjectCount()

    /**
     * Execute bytecode module.
     * @return VMResult.SUCCESS on successful execution, otherwise error code
     */
    fun execute(): VMResult {
        // Initialization: find entry point function
        val entryFunction = module.functions.find { it.name == module.entryPoint }
            ?: return VMResult.INVALID_FUNCTION_INDEX

        // Create first CallFrame
        val initialFrame = CallFrame(
            function = entryFunction,
            locals = RcLocals(memoryManager, entryFunction.localsCount),
            pc = 0,
            returnAddress = null
        )
        callStack.addLast(initialFrame)

        // Main interpretation loop
        while (callStack.isNotEmpty()) {
            val frame = callStack.last()

            // Check for function end
            if (frame.pc >= frame.function.instructions.size) {
                // Function completed without explicit return
                // For void functions, this should behave like RETURN_VOID
                // For non-void functions, this is an error (should have explicit return)
                // But we'll handle it gracefully by treating it as RETURN_VOID
                frame.locals.clearAndReleaseAll()
                val returnAddress = frame.returnAddress
                callStack.removeLast()
                
                // Restore caller PC if there is a caller
                if (callStack.isNotEmpty() && returnAddress != null) {
                    val callerFrame = callStack.last()
                    callerFrame.pc = returnAddress
                }
                continue
            }


            // Save stack size and PC before instruction execution
            val stackSizeBefore = callStack.size
            val pcBefore = frame.pc
            
            // Interpret one instruction
            val result = interpretInstruction(frame)
            if (result != VMResult.SUCCESS) {
                // Clear all frames on error
                while (callStack.isNotEmpty()) {
                    callStack.removeLast().locals.clearAndReleaseAll()
                }
                operandStack.clearAndReleaseAll()
                
                // Clear heap to free all remaining memory
                memoryManager.clearHeap()
                
                return result
            }

            // If stack decreased (RETURN removed frame), don't increment PC
            // Caller frame PC is already set in handleReturn
            if (callStack.size < stackSizeBefore) {
                continue
            }

            // If PC was changed by the instruction (jump), don't increment it
            // Otherwise, increment PC by 4 bytes (instruction size)
            if (frame.pc == pcBefore) {
                frame.pc += 4
            }
        }

        // Clear operand stack before completion
        operandStack.clearAndReleaseAll()
        
        // Clear heap to free all remaining memory
        memoryManager.clearHeap()
        
        return VMResult.SUCCESS
    }

    /**
     * Interpret one instruction.
     */
    private fun interpretInstruction(frame: CallFrame): VMResult {
        val instructions = frame.function.instructions
        val pc = frame.pc

        // Check bounds
        if (pc >= instructions.size) {
            return VMResult.SUCCESS // End of function
        }

        // Read opcode
        val opcode = instructions[pc].toUByte().toInt()

        // Read operand (3 bytes, big-endian)
        val operand = if (pc + 3 < instructions.size) {
            ((instructions[pc + 1].toUByte().toInt() shl 16) or
             (instructions[pc + 2].toUByte().toInt() shl 8) or
             instructions[pc + 3].toUByte().toInt())
        } else {
            0
        }

        // Execute instruction
        return when (opcode.toByte()) {
            // Constants
            Opcodes.PUSH_INT -> handlePushInt(operand)
            Opcodes.PUSH_FLOAT -> handlePushFloat(operand)
            Opcodes.PUSH_BOOL -> handlePushBool(operand)
            Opcodes.POP -> handlePop()

            // Local variables
            Opcodes.LOAD_LOCAL -> handleLoadLocal(frame, operand)
            Opcodes.STORE_LOCAL -> handleStoreLocal(frame, operand)
            Opcodes.INC_LOCAL -> handleIncLocal(frame, operand)
            Opcodes.DEC_LOCAL -> handleDecLocal(frame, operand)
            Opcodes.ADD_LOCAL_IMMEDIATE -> handleAddLocalImmediate(frame, operand)

            // Integer arithmetic
            Opcodes.ADD_INT -> handleAddInt()
            Opcodes.SUB_INT -> handleSubInt()
            Opcodes.MUL_INT -> handleMulInt()
            Opcodes.DIV_INT -> handleDivInt()
            Opcodes.MOD_INT -> handleModInt()
            Opcodes.NEG_INT -> handleNegInt()

            // Float arithmetic
            Opcodes.ADD_FLOAT -> handleAddFloat()
            Opcodes.SUB_FLOAT -> handleSubFloat()
            Opcodes.MUL_FLOAT -> handleMulFloat()
            Opcodes.DIV_FLOAT -> handleDivFloat()
            Opcodes.NEG_FLOAT -> handleNegFloat()

            // Integer comparisons
            Opcodes.EQ_INT -> handleEqInt()
            Opcodes.NE_INT -> handleNeInt()
            Opcodes.LT_INT -> handleLtInt()
            Opcodes.LE_INT -> handleLeInt()
            Opcodes.GT_INT -> handleGtInt()
            Opcodes.GE_INT -> handleGeInt()

            // Float comparisons
            Opcodes.EQ_FLOAT -> handleEqFloat()
            Opcodes.NE_FLOAT -> handleNeFloat()
            Opcodes.LT_FLOAT -> handleLtFloat()
            Opcodes.LE_FLOAT -> handleLeFloat()
            Opcodes.GT_FLOAT -> handleGtFloat()
            Opcodes.GE_FLOAT -> handleGeFloat()

            // Logical operations
            Opcodes.AND -> handleAnd()
            Opcodes.OR -> handleOr()
            Opcodes.NOT -> handleNot()

            // Control flow
            Opcodes.JUMP -> handleJump(frame, operand)
            Opcodes.JUMP_IF_FALSE -> handleJumpIfFalse(frame, operand)
            Opcodes.JUMP_IF_TRUE -> handleJumpIfTrue(frame, operand)

            // Functions
            Opcodes.CALL -> handleCall(frame, operand)
            Opcodes.RETURN -> handleReturn(frame)
            Opcodes.RETURN_VOID -> handleReturnVoid(frame)

            // Arrays
            Opcodes.NEW_ARRAY_INT -> handleNewArrayInt()
            Opcodes.NEW_ARRAY_FLOAT -> handleNewArrayFloat()
            Opcodes.NEW_ARRAY_BOOL -> handleNewArrayBool()
            Opcodes.ARRAY_LOAD -> handleArrayLoad()
            Opcodes.ARRAY_STORE -> handleArrayStore()

            // Built-in functions
            Opcodes.PRINT -> handlePrint()
            Opcodes.PRINT_ARRAY -> handlePrintArray()

            else -> VMResult.INVALID_OPCODE
        }
    }

    // ========== Constants ==========

    private fun handlePushInt(operand: Int): VMResult {
        if (operand < 0 || operand >= module.intConstants.size) {
            return VMResult.INVALID_CONSTANT_INDEX
        }
        val value = module.intConstants[operand]
        operandStack.pushMove(Value.IntValue(value))
        return VMResult.SUCCESS
    }

    private fun handlePushFloat(operand: Int): VMResult {
        if (operand < 0 || operand >= module.floatConstants.size) {
            return VMResult.INVALID_CONSTANT_INDEX
        }
        val value = module.floatConstants[operand]
        operandStack.pushMove(Value.FloatValue(value))
        return VMResult.SUCCESS
    }

    private fun handlePushBool(operand: Int): VMResult {
        val value = operand != 0
        operandStack.pushMove(Value.BoolValue(value))
        return VMResult.SUCCESS
    }

    private fun handlePop(): VMResult {
        if (operandStack.size() == 0) return VMResult.STACK_UNDERFLOW
        operandStack.popDrop()
        return VMResult.SUCCESS
    }

    // ========== Local Variables ==========

    private fun handleLoadLocal(frame: CallFrame, operand: Int): VMResult {
        if (operand < 0 || operand >= frame.function.localsCount) {
            return VMResult.INVALID_LOCAL_INDEX
        }
        val value = frame.locals.getCopy(operand)
        operandStack.pushCopy(value)
        return VMResult.SUCCESS
    }

    private fun handleStoreLocal(frame: CallFrame, operand: Int): VMResult {
        if (operand < 0 || operand >= frame.function.localsCount) {
            return VMResult.INVALID_LOCAL_INDEX
        }
        if (operandStack.size() == 0) {
            return VMResult.STACK_UNDERFLOW
        }
        val value = operandStack.popMove()
        frame.locals.setMove(operand, value)
        return VMResult.SUCCESS
    }

    private fun handleIncLocal(frame: CallFrame, operand: Int): VMResult {
        if (operand < 0 || operand >= frame.function.localsCount) {
            return VMResult.INVALID_LOCAL_INDEX
        }
        val currentValue = frame.locals.getCopy(operand)
        if (currentValue is Value.IntValue) {
            val newValue = Value.IntValue(currentValue.value + 1)
            frame.locals.setMove(operand, newValue)
            return VMResult.SUCCESS
        }
        return VMResult.INVALID_VALUE_TYPE
    }

    private fun handleDecLocal(frame: CallFrame, operand: Int): VMResult {
        if (operand < 0 || operand >= frame.function.localsCount) {
            return VMResult.INVALID_LOCAL_INDEX
        }
        val currentValue = frame.locals.getCopy(operand)
        if (currentValue is Value.IntValue) {
            val newValue = Value.IntValue(currentValue.value - 1)
            frame.locals.setMove(operand, newValue)
            return VMResult.SUCCESS
        }
        return VMResult.INVALID_VALUE_TYPE
    }

    private fun handleAddLocalImmediate(frame: CallFrame, operand: Int): VMResult {
        // operand format: (localIndex << 16) | (constIndex & 0xFFFF)
        val localIndex = (operand shr 16) and 0xFFFF
        val constIndex = operand and 0xFFFF
        
        if (localIndex < 0 || localIndex >= frame.function.localsCount) {
            return VMResult.INVALID_LOCAL_INDEX
        }
        if (constIndex < 0 || constIndex >= module.intConstants.size) {
            return VMResult.INVALID_CONSTANT_INDEX
        }
        
        val currentValue = frame.locals.getCopy(localIndex)
        if (currentValue is Value.IntValue) {
            val constantValue = module.intConstants[constIndex]
            val newValue = Value.IntValue(currentValue.value + constantValue)
            frame.locals.setMove(localIndex, newValue)
            return VMResult.SUCCESS
        }
        return VMResult.INVALID_VALUE_TYPE
    }

    // ========== Helper functions for stack operations ==========
    
    private inline fun <reified T : Value> safePop(): T? {
        if (operandStack.size() < 1) return null
        val value = operandStack.popMove()
        return if (value is T) value else {
            operandStack.pushMove(value) // restore on type mismatch
            null
        }
    }
    
    private inline fun <reified T1 : Value, reified T2 : Value> safePopTwo(): Pair<T1, T2>? {
        if (operandStack.size() < 2) return null
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a is T1 && b is T2) {
            return Pair(a, b)
        } else {
            operandStack.pushMove(a) // restore on type mismatch
            operandStack.pushMove(b)
            return null
        }
    }
    
    private inline fun <reified T1 : Value, reified T2 : Value, reified T3 : Value> safePopThree(): Triple<T1, T2, T3>? {
        if (operandStack.size() < 3) return null
        val c = operandStack.popMove()
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a is T1 && b is T2 && c is T3) {
            return Triple(a, b, c)
        } else {
            operandStack.pushMove(a) // restore on type mismatch
            operandStack.pushMove(b)
            operandStack.pushMove(c)
            return null
        }
    }

    // ========== Helper functions for binary operations ==========
    
    private inline fun binaryIntOp(
        op: (Long, Long) -> Long,
        noinline checkZero: ((Long) -> Boolean)? = null
    ): VMResult {
        val (a, b) = safePopTwo<Value.IntValue, Value.IntValue>() ?: 
            return if (operandStack.size() < 2) VMResult.STACK_UNDERFLOW else VMResult.INVALID_VALUE_TYPE
        
        if (checkZero != null && checkZero(b.value)) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.DIVISION_BY_ZERO
        }
        operandStack.pushMove(Value.IntValue(op(a.value, b.value)))
        return VMResult.SUCCESS
    }

    private inline fun binaryFloatOp(op: (Double, Double) -> Double): VMResult {
        val (a, b) = safePopTwo<Value.FloatValue, Value.FloatValue>() ?: 
            return if (operandStack.size() < 2) VMResult.STACK_UNDERFLOW else VMResult.INVALID_VALUE_TYPE
        operandStack.pushMove(Value.FloatValue(op(a.value, b.value)))
        return VMResult.SUCCESS
    }

    private fun unaryIntOp(op: (Long) -> Long): VMResult {
        val a = safePop<Value.IntValue>() ?: 
            return if (operandStack.size() < 1) VMResult.STACK_UNDERFLOW else VMResult.INVALID_VALUE_TYPE
        operandStack.pushMove(Value.IntValue(op(a.value)))
        return VMResult.SUCCESS
    }

    private fun unaryFloatOp(op: (Double) -> Double): VMResult {
        val a = safePop<Value.FloatValue>() ?: 
            return if (operandStack.size() < 1) VMResult.STACK_UNDERFLOW else VMResult.INVALID_VALUE_TYPE
        operandStack.pushMove(Value.FloatValue(op(a.value)))
        return VMResult.SUCCESS
    }

    // ========== Integer Arithmetic ==========

    private fun handleAddInt() = binaryIntOp(Long::plus)
    private fun handleSubInt() = binaryIntOp(Long::minus)
    private fun handleMulInt() = binaryIntOp(Long::times)
    private fun handleDivInt() = binaryIntOp(Long::div) { it == 0L }
    private fun handleModInt() = binaryIntOp(Long::rem) { it == 0L }
    private fun handleNegInt() = unaryIntOp { -it }

    // ========== Float Arithmetic ==========

    private fun handleAddFloat() = binaryFloatOp(Double::plus)
    private fun handleSubFloat() = binaryFloatOp(Double::minus)
    private fun handleMulFloat() = binaryFloatOp(Double::times)
    private fun handleDivFloat() = binaryFloatOp(Double::div)
    private fun handleNegFloat() = unaryFloatOp { -it }

    // ========== Helper functions for comparisons ==========
    
    private inline fun compareIntOp(op: (Long, Long) -> Boolean): VMResult {
        val (a, b) = safePopTwo<Value.IntValue, Value.IntValue>() ?: 
            return if (operandStack.size() < 2) VMResult.STACK_UNDERFLOW else VMResult.INVALID_VALUE_TYPE
        operandStack.pushMove(Value.BoolValue(op(a.value, b.value)))
        return VMResult.SUCCESS
    }

    private inline fun compareFloatOp(op: (Double, Double) -> Boolean): VMResult {
        val (a, b) = safePopTwo<Value.FloatValue, Value.FloatValue>() ?: 
            return if (operandStack.size() < 2) VMResult.STACK_UNDERFLOW else VMResult.INVALID_VALUE_TYPE
        operandStack.pushMove(Value.BoolValue(op(a.value, b.value)))
        return VMResult.SUCCESS
    }

    // ========== Integer Comparisons ==========

    private fun handleEqInt() = compareIntOp { a, b -> a == b }
    private fun handleNeInt() = compareIntOp { a, b -> a != b }
    private fun handleLtInt() = compareIntOp { a, b -> a < b }
    private fun handleLeInt() = compareIntOp { a, b -> a <= b }
    private fun handleGtInt() = compareIntOp { a, b -> a > b }
    private fun handleGeInt() = compareIntOp { a, b -> a >= b }

    // ========== Float Comparisons ==========

    private fun handleEqFloat() = compareFloatOp { a, b -> a == b }
    private fun handleNeFloat() = compareFloatOp { a, b -> a != b }
    private fun handleLtFloat() = compareFloatOp { a, b -> a < b }
    private fun handleLeFloat() = compareFloatOp { a, b -> a <= b }
    private fun handleGtFloat() = compareFloatOp { a, b -> a > b }
    private fun handleGeFloat() = compareFloatOp { a, b -> a >= b }

    // ========== Helper functions for logical operations ==========
    
    private inline fun binaryBoolOp(op: (Boolean, Boolean) -> Boolean): VMResult {
        val (a, b) = safePopTwo<Value.BoolValue, Value.BoolValue>() ?: 
            return if (operandStack.size() < 2) VMResult.STACK_UNDERFLOW else VMResult.INVALID_VALUE_TYPE
        operandStack.pushMove(Value.BoolValue(op(a.value, b.value)))
        return VMResult.SUCCESS
    }

    // ========== Logical Operations ==========

    private fun handleAnd() = binaryBoolOp { a, b -> a && b }
    private fun handleOr() = binaryBoolOp { a, b -> a || b }
    private fun handleNot(): VMResult {
        val a = safePop<Value.BoolValue>() ?: 
            return if (operandStack.size() < 1) VMResult.STACK_UNDERFLOW else VMResult.INVALID_VALUE_TYPE
        operandStack.pushMove(Value.BoolValue(!a.value))
        return VMResult.SUCCESS
    }

    // ========== Helper function for jumps ==========
    
    private fun calculateJumpPC(frame: CallFrame, operand: Int): Int? {
        val signedOperand = if (operand and 0x800000 != 0) {
            operand or 0xFF000000.toInt() // Sign extension
        } else {
            operand
        }
        val newPC = frame.pc + (signedOperand * 4) + 4
        return if (newPC < 0 || newPC > frame.function.instructions.size) {
            null
        } else {
            newPC - 4 // Compensate for automatic increment in loop
        }
    }

    // ========== Control Flow ==========

    private fun handleJump(frame: CallFrame, operand: Int): VMResult {
        val newPC = calculateJumpPC(frame, operand) ?: return VMResult.INVALID_OPCODE
        frame.pc = newPC
        return VMResult.SUCCESS
    }

    private fun handleJumpIfFalse(frame: CallFrame, operand: Int): VMResult {
        val value = safePop<Value.BoolValue>() ?: 
            return if (operandStack.size() < 1) VMResult.STACK_UNDERFLOW else VMResult.INVALID_VALUE_TYPE
        if (!value.value) {
            val newPC = calculateJumpPC(frame, operand) ?: return VMResult.INVALID_OPCODE
            frame.pc = newPC
        }
        return VMResult.SUCCESS
    }

    private fun handleJumpIfTrue(frame: CallFrame, operand: Int): VMResult {
        val value = safePop<Value.BoolValue>() ?: 
            return if (operandStack.size() < 1) VMResult.STACK_UNDERFLOW else VMResult.INVALID_VALUE_TYPE
        if (value.value) {
            val newPC = calculateJumpPC(frame, operand) ?: return VMResult.INVALID_OPCODE
            frame.pc = newPC
        }
        return VMResult.SUCCESS
    }

    // ========== Functions ==========

    private fun handleCall(frame: CallFrame, operand: Int): VMResult {
        if (operand < 0 || operand >= module.functions.size) {
            return VMResult.INVALID_FUNCTION_INDEX
        }
        
        val function = module.functions[operand]
        
        // Profiling for JIT
        if (jitCompiler?.isEnabled() == true) {
            jitCompiler.recordCall(function.name)
        }
        
        // Pop arguments from stack in reverse order
        if (operandStack.size() < function.parameters.size) {
            return VMResult.STACK_UNDERFLOW
        }
        
        val args = mutableListOf<Value>()
        for (i in function.parameters.indices.reversed()) {
            args.add(0, operandStack.popMove())
        }
        
        // Check for optimized bytecode (BytecodeOptimizerJIT)
        val optimizedFunction = if (jitCompiler is com.compiler.vm.jit.BytecodeOptimizerJIT) {
            jitCompiler.getOptimizedFunction(operand)
        } else {
            null
        }
        
        // Use optimized function if available, otherwise use original
        // Validate that optimized function has same signature as original
        val functionToUse = if (optimizedFunction != null) {
            // Verify that optimized function has same parameters and localsCount
            if (optimizedFunction.parameters.size == function.parameters.size &&
                optimizedFunction.localsCount == function.localsCount &&
                optimizedFunction.returnType == function.returnType) {
                optimizedFunction
            } else {
                // If signature doesn't match, use original function
                function
            }
        } else {
            function
        }
        
        // Create new CallFrame
        // returnAddress should point to the instruction after CALL
        // Since PC will be incremented by 4 after CALL, we need to use current PC + 4
        val newFrame = CallFrame(
            function = functionToUse,
            locals = RcLocals(memoryManager, functionToUse.localsCount),
            pc = 0,
            returnAddress = frame.pc + 4
        )
        
        // Initialize parameters
        for (i in args.indices) {
            newFrame.locals.setMove(i, args[i])
        }
        
        // Check JIT compilation at function entry (pc == 0)
        // This avoids expensive ConcurrentHashMap lookup on every instruction
        if (jitCompiler?.isEnabled() == true) {
            val compiled = jitCompiler.getCompiled(function.name)
            if (compiled != null) {
                try {
                    // Execute compiled function - it manages its own call stack internally
                    // and handles return values/PC restoration automatically
                    val result = compiled.execute(newFrame, operandStack, memoryManager)
                    if (result != VMResult.SUCCESS) {
                        newFrame.locals.clearAndReleaseAll()
                        while (callStack.isNotEmpty()) {
                            callStack.removeLast().locals.clearAndReleaseAll()
                        }
                        operandStack.clearAndReleaseAll()
                        memoryManager.clearHeap()
                        return result
                    }

                    // Compiled function completed successfully
                    // The function's return value (if any) is already on operandStack
                    // and caller's PC is already restored by the compiled function
                    // We just need to clean up the frame we created
                    newFrame.locals.clearAndReleaseAll()
                    // Note: newFrame was never added to callStack, so we don't need to remove it
                    return VMResult.SUCCESS
                } catch (t: Throwable) {
                    t.printStackTrace(System.err)
                    newFrame.locals.clearAndReleaseAll()
                    while (callStack.isNotEmpty()) {
                        callStack.removeLast().locals.clearAndReleaseAll()
                    }
                    operandStack.clearAndReleaseAll()
                    memoryManager.clearHeap()
                    return VMResult.INVALID_OPCODE
                }
            }
        }
        
        // Push new frame onto stack for interpretation
        callStack.addLast(newFrame)
        
        return VMResult.SUCCESS
    }

    private fun handleReturn(frame: CallFrame): VMResult {
        if (operandStack.size() < 1) {
            return VMResult.STACK_UNDERFLOW
        }
        
        // Pop return value
        val returnValue = operandStack.popMove()
        
        // Release all ArrayRef in locals
        frame.locals.clearAndReleaseAll()
        
        // Remove current frame
        callStack.removeLast()
        
        // If there is a caller frame, push value onto its stack
        if (callStack.isNotEmpty()) {
            val callerFrame = callStack.last()
            operandStack.pushMove(returnValue)
            callerFrame.pc = frame.returnAddress ?: callerFrame.pc
        }
        
        return VMResult.SUCCESS
    }

    private fun handleReturnVoid(frame: CallFrame): VMResult {
        // For void functions, operand stack should be empty
        // But we don't check this because it's not part of the VM spec
        // The stack will be cleared when the function frame is removed
        
        // Release all ArrayRef in locals
        frame.locals.clearAndReleaseAll()
        
        // Remove current frame
        val returnAddress = frame.returnAddress
        callStack.removeLast()
        
        // Restore caller PC
        if (callStack.isNotEmpty() && returnAddress != null) {
            val callerFrame = callStack.last()
            callerFrame.pc = returnAddress
        }
        
        return VMResult.SUCCESS
    }

    // ========== Helper function for array creation ==========
    
    private inline fun newArray(createArray: (Int) -> Value.ArrayRef): VMResult {
        val sizeValue = safePop<Value.IntValue>() ?: 
            return if (operandStack.size() < 1) VMResult.STACK_UNDERFLOW else VMResult.INVALID_VALUE_TYPE
        val size = sizeValue.value.toInt()
        if (size < 0) {
            operandStack.pushMove(sizeValue)
            return VMResult.ARRAY_INDEX_OUT_OF_BOUNDS
        }
        operandStack.pushMove(createArray(size))
        return VMResult.SUCCESS
    }

    // ========== Arrays ==========

    private fun handleNewArrayInt() = newArray { memoryManager.newIntArray(it) }
    private fun handleNewArrayFloat() = newArray { memoryManager.newFloatArray(it) }
    private fun handleNewArrayBool() = newArray { memoryManager.newBoolArray(it) }

    private fun handleArrayLoad(): VMResult {
        val (arrayRef, indexValue) = safePopTwo<Value.ArrayRef, Value.IntValue>() ?: 
            return if (operandStack.size() < 2) VMResult.STACK_UNDERFLOW else VMResult.INVALID_VALUE_TYPE
        
        val index = indexValue.value.toInt()
        
        // Determine array type and load value
        try {
            val arrayType = memoryManager.getArrayType(arrayRef)
                ?: return VMResult.INVALID_ARRAY_TYPE
            
            when (arrayType) {
                "int" -> {
                    val value = memoryManager.intArrayLoad(arrayRef, index)
                    operandStack.pushMove(Value.IntValue(value))
                }
                "float" -> {
                    val value = memoryManager.floatArrayLoad(arrayRef, index)
                    operandStack.pushMove(Value.FloatValue(value))
                }
                "bool" -> {
                    val value = memoryManager.boolArrayLoad(arrayRef, index)
                    operandStack.pushMove(Value.BoolValue(value))
                }
                else -> return VMResult.INVALID_ARRAY_TYPE
            }
            return VMResult.SUCCESS
        } catch (e: IndexOutOfBoundsException) {
            operandStack.pushMove(arrayRef)
            operandStack.pushMove(indexValue)
            return VMResult.ARRAY_INDEX_OUT_OF_BOUNDS
        } catch (e: IllegalStateException) {
            operandStack.pushMove(arrayRef)
            operandStack.pushMove(indexValue)
            return VMResult.INVALID_HEAP_ID
        }
    }

    private fun handleArrayStore(): VMResult {
        if (operandStack.size() < 3) return VMResult.STACK_UNDERFLOW
        
        val value = operandStack.popMove()
        val (arrayRef, indexValue) = safePopTwo<Value.ArrayRef, Value.IntValue>() ?: run {
            operandStack.pushMove(value) // restore value on type mismatch
            return VMResult.INVALID_VALUE_TYPE
        }
        
        val index = indexValue.value.toInt()
        
        try {
            when (value) {
                is Value.IntValue -> {
                    memoryManager.intArrayStore(arrayRef, index, value.value)
                    return VMResult.SUCCESS
                }
                is Value.FloatValue -> {
                    memoryManager.floatArrayStore(arrayRef, index, value.value)
                    return VMResult.SUCCESS
                }
                is Value.BoolValue -> {
                    memoryManager.boolArrayStore(arrayRef, index, value.value)
                    return VMResult.SUCCESS
                }
                else -> {
                    operandStack.pushMove(arrayRef)
                    operandStack.pushMove(indexValue)
                    operandStack.pushMove(value)
                    return VMResult.INVALID_VALUE_TYPE
                }
            }
        } catch (e: IndexOutOfBoundsException) {
            operandStack.pushMove(arrayRef)
            operandStack.pushMove(indexValue)
            operandStack.pushMove(value)
            return VMResult.ARRAY_INDEX_OUT_OF_BOUNDS
        } catch (e: IllegalStateException) {
            operandStack.pushMove(arrayRef)
            operandStack.pushMove(indexValue)
            operandStack.pushMove(value)
            return VMResult.INVALID_HEAP_ID
        }
    }

    // ========== Built-in Functions ==========

    private fun handlePrint(): VMResult {
        if (operandStack.size() < 1) return VMResult.STACK_UNDERFLOW
        val value = operandStack.popMove()
        when (value) {
            is Value.IntValue -> print(value.value)
            is Value.FloatValue -> print(value.value)
            is Value.BoolValue -> print(value.value)
            else -> {
                operandStack.pushMove(value)
                return VMResult.INVALID_VALUE_TYPE
            }
        }
        return VMResult.SUCCESS
    }

    private fun handlePrintArray(): VMResult {
        val arrayRef = safePop<Value.ArrayRef>() ?: 
            return if (operandStack.size() < 1) VMResult.STACK_UNDERFLOW else VMResult.INVALID_VALUE_TYPE
        
        try {
            val arrayType = memoryManager.getArrayType(arrayRef)
                ?: return VMResult.INVALID_ARRAY_TYPE
            
            val size = memoryManager.getArraySize(arrayRef)
            
            print("[")
            when (arrayType) {
                "int" -> {
                    for (i in 0 until size) {
                        if (i > 0) print(", ")
                        print(memoryManager.intArrayLoad(arrayRef, i))
                    }
                }
                "float" -> {
                    for (i in 0 until size) {
                        if (i > 0) print(", ")
                        print(memoryManager.floatArrayLoad(arrayRef, i))
                    }
                }
                "bool" -> {
                    for (i in 0 until size) {
                        if (i > 0) print(", ")
                        print(memoryManager.boolArrayLoad(arrayRef, i))
                    }
                }
                else -> return VMResult.INVALID_ARRAY_TYPE
            }
            print("]")
            return VMResult.SUCCESS
        } catch (e: IllegalStateException) {
            operandStack.pushMove(arrayRef)
            return VMResult.INVALID_HEAP_ID
        }
    }
}

