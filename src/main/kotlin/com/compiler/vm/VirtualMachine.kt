package com.compiler.vm

import com.compiler.bytecode.*
import com.compiler.memory.*

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

            // Check JIT compilation
            if (jitCompiler?.isEnabled() == true) {
                val compiled = jitCompiler.getCompiled(frame.function.name)
                if (compiled != null) {
                    // Execute compiled version
                    // For simplicity, JIT function call can be implemented here
                    // For now, continue with interpretation
                }
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
        if (operandStack.size() == 0) {
            return VMResult.STACK_UNDERFLOW
        }
        operandStack.popDrop()
        return VMResult.SUCCESS
    }

    // ========== Local Variables ==========

    private fun handleLoadLocal(frame: CallFrame, operand: Int): VMResult {
        if (operand < 0 || operand >= frame.function.localsCount) {
            return VMResult.INVALID_LOCAL_INDEX
        }
        try {
            val value = frame.locals.getCopy(operand)
            operandStack.pushCopy(value)
            return VMResult.SUCCESS
        } catch (e: Exception) {
            return VMResult.INVALID_LOCAL_INDEX
        }
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

    // ========== Integer Arithmetic ==========

    private fun handleAddInt(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.IntValue || b !is Value.IntValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.IntValue(a.value + b.value))
        return VMResult.SUCCESS
    }

    private fun handleSubInt(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.IntValue || b !is Value.IntValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.IntValue(a.value - b.value))
        return VMResult.SUCCESS
    }

    private fun handleMulInt(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.IntValue || b !is Value.IntValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.IntValue(a.value * b.value))
        return VMResult.SUCCESS
    }

    private fun handleDivInt(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.IntValue || b !is Value.IntValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        if (b.value == 0L) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.DIVISION_BY_ZERO
        }
        operandStack.pushMove(Value.IntValue(a.value / b.value))
        return VMResult.SUCCESS
    }

    private fun handleModInt(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.IntValue || b !is Value.IntValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        if (b.value == 0L) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.DIVISION_BY_ZERO
        }
        operandStack.pushMove(Value.IntValue(a.value % b.value))
        return VMResult.SUCCESS
    }

    private fun handleNegInt(): VMResult {
        if (operandStack.size() < 1) {
            return VMResult.STACK_UNDERFLOW
        }
        val a = operandStack.popMove()
        if (a !is Value.IntValue) {
            operandStack.pushMove(a)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.IntValue(-a.value))
        return VMResult.SUCCESS
    }

    // ========== Float Arithmetic ==========

    private fun handleAddFloat(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.FloatValue || b !is Value.FloatValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.FloatValue(a.value + b.value))
        return VMResult.SUCCESS
    }

    private fun handleSubFloat(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.FloatValue || b !is Value.FloatValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.FloatValue(a.value - b.value))
        return VMResult.SUCCESS
    }

    private fun handleMulFloat(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.FloatValue || b !is Value.FloatValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.FloatValue(a.value * b.value))
        return VMResult.SUCCESS
    }

    private fun handleDivFloat(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.FloatValue || b !is Value.FloatValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.FloatValue(a.value / b.value))
        return VMResult.SUCCESS
    }

    private fun handleNegFloat(): VMResult {
        if (operandStack.size() < 1) {
            return VMResult.STACK_UNDERFLOW
        }
        val a = operandStack.popMove()
        if (a !is Value.FloatValue) {
            operandStack.pushMove(a)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.FloatValue(-a.value))
        return VMResult.SUCCESS
    }

    // ========== Integer Comparisons ==========

    private fun handleEqInt(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.IntValue || b !is Value.IntValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.BoolValue(a.value == b.value))
        return VMResult.SUCCESS
    }

    private fun handleNeInt(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.IntValue || b !is Value.IntValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.BoolValue(a.value != b.value))
        return VMResult.SUCCESS
    }

    private fun handleLtInt(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.IntValue || b !is Value.IntValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.BoolValue(a.value < b.value))
        return VMResult.SUCCESS
    }

    private fun handleLeInt(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.IntValue || b !is Value.IntValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.BoolValue(a.value <= b.value))
        return VMResult.SUCCESS
    }

    private fun handleGtInt(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.IntValue || b !is Value.IntValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.BoolValue(a.value > b.value))
        return VMResult.SUCCESS
    }

    private fun handleGeInt(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.IntValue || b !is Value.IntValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.BoolValue(a.value >= b.value))
        return VMResult.SUCCESS
    }

    // ========== Float Comparisons ==========

    private fun handleEqFloat(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.FloatValue || b !is Value.FloatValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.BoolValue(a.value == b.value))
        return VMResult.SUCCESS
    }

    private fun handleNeFloat(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.FloatValue || b !is Value.FloatValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.BoolValue(a.value != b.value))
        return VMResult.SUCCESS
    }

    private fun handleLtFloat(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.FloatValue || b !is Value.FloatValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.BoolValue(a.value < b.value))
        return VMResult.SUCCESS
    }

    private fun handleLeFloat(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.FloatValue || b !is Value.FloatValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.BoolValue(a.value <= b.value))
        return VMResult.SUCCESS
    }

    private fun handleGtFloat(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.FloatValue || b !is Value.FloatValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.BoolValue(a.value > b.value))
        return VMResult.SUCCESS
    }

    private fun handleGeFloat(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.FloatValue || b !is Value.FloatValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.BoolValue(a.value >= b.value))
        return VMResult.SUCCESS
    }

    // ========== Logical Operations ==========

    private fun handleAnd(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.BoolValue || b !is Value.BoolValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.BoolValue(a.value && b.value))
        return VMResult.SUCCESS
    }

    private fun handleOr(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val b = operandStack.popMove()
        val a = operandStack.popMove()
        if (a !is Value.BoolValue || b !is Value.BoolValue) {
            operandStack.pushMove(a)
            operandStack.pushMove(b)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.BoolValue(a.value || b.value))
        return VMResult.SUCCESS
    }

    private fun handleNot(): VMResult {
        if (operandStack.size() < 1) {
            return VMResult.STACK_UNDERFLOW
        }
        val a = operandStack.popMove()
        if (a !is Value.BoolValue) {
            operandStack.pushMove(a)
            return VMResult.INVALID_VALUE_TYPE
        }
        operandStack.pushMove(Value.BoolValue(!a.value))
        return VMResult.SUCCESS
    }

    // ========== Control Flow ==========

    private fun handleJump(frame: CallFrame, operand: Int): VMResult {
        // Convert operand to signed (sign extension)
        val signedOperand = if (operand and 0x800000 != 0) {
            operand or 0xFF000000.toInt() // Sign extension
        } else {
            operand
        }
        
        // Calculate new PC value: newPC = currentPC + (operand * 4) + 4
        val newPC = frame.pc + (signedOperand * 4) + 4
        
        // Check bounds
        if (newPC < 0 || newPC > frame.function.instructions.size) {
            return VMResult.INVALID_OPCODE
        }
        
        // Set PC (compensate for automatic increment in loop)
        frame.pc = newPC - 4
        return VMResult.SUCCESS
    }

    private fun handleJumpIfFalse(frame: CallFrame, operand: Int): VMResult {
        if (operandStack.size() < 1) {
            return VMResult.STACK_UNDERFLOW
        }
        val value = operandStack.popMove()
        if (value !is Value.BoolValue) {
            operandStack.pushMove(value)
            return VMResult.INVALID_VALUE_TYPE
        }
        
        if (!value.value) {
            // Jump
            val signedOperand = if (operand and 0x800000 != 0) {
                operand or 0xFF000000.toInt()
            } else {
                operand
            }
            val newPC = frame.pc + (signedOperand * 4) + 4
            if (newPC < 0 || newPC > frame.function.instructions.size) {
                operandStack.pushMove(value)
                return VMResult.INVALID_OPCODE
            }
            frame.pc = newPC - 4
        }
        // If true, continue execution with next instruction
        return VMResult.SUCCESS
    }

    private fun handleJumpIfTrue(frame: CallFrame, operand: Int): VMResult {
        if (operandStack.size() < 1) {
            return VMResult.STACK_UNDERFLOW
        }
        val value = operandStack.popMove()
        if (value !is Value.BoolValue) {
            operandStack.pushMove(value)
            return VMResult.INVALID_VALUE_TYPE
        }
        
        if (value.value) {
            // Jump
            val signedOperand = if (operand and 0x800000 != 0) {
                operand or 0xFF000000.toInt()
            } else {
                operand
            }
            val newPC = frame.pc + (signedOperand * 4) + 4
            if (newPC < 0 || newPC > frame.function.instructions.size) {
                operandStack.pushMove(value)
                return VMResult.INVALID_OPCODE
            }
            frame.pc = newPC - 4
        }
        // If false, continue execution with next instruction
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
        
        // Create new CallFrame
        // returnAddress should point to the instruction after CALL
        // Since PC will be incremented by 4 after CALL, we need to use current PC + 4
        val newFrame = CallFrame(
            function = function,
            locals = RcLocals(memoryManager, function.localsCount),
            pc = 0,
            returnAddress = frame.pc + 4
        )
        
        // Initialize parameters
        for (i in args.indices) {
            newFrame.locals.setMove(i, args[i])
        }
        
        // Push new frame onto stack
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

    // ========== Arrays ==========

    private fun handleNewArrayInt(): VMResult {
        if (operandStack.size() < 1) {
            return VMResult.STACK_UNDERFLOW
        }
        val sizeValue = operandStack.popMove()
        if (sizeValue !is Value.IntValue) {
            operandStack.pushMove(sizeValue)
            return VMResult.INVALID_VALUE_TYPE
        }
        val size = sizeValue.value.toInt()
        if (size < 0) {
            operandStack.pushMove(sizeValue)
            return VMResult.ARRAY_INDEX_OUT_OF_BOUNDS
        }
        val ref = memoryManager.newIntArray(size)
        operandStack.pushMove(ref)
        return VMResult.SUCCESS
    }

    private fun handleNewArrayFloat(): VMResult {
        if (operandStack.size() < 1) {
            return VMResult.STACK_UNDERFLOW
        }
        val sizeValue = operandStack.popMove()
        if (sizeValue !is Value.IntValue) {
            operandStack.pushMove(sizeValue)
            return VMResult.INVALID_VALUE_TYPE
        }
        val size = sizeValue.value.toInt()
        if (size < 0) {
            operandStack.pushMove(sizeValue)
            return VMResult.ARRAY_INDEX_OUT_OF_BOUNDS
        }
        val ref = memoryManager.newFloatArray(size)
        operandStack.pushMove(ref)
        return VMResult.SUCCESS
    }

    private fun handleNewArrayBool(): VMResult {
        if (operandStack.size() < 1) {
            return VMResult.STACK_UNDERFLOW
        }
        val sizeValue = operandStack.popMove()
        if (sizeValue !is Value.IntValue) {
            operandStack.pushMove(sizeValue)
            return VMResult.INVALID_VALUE_TYPE
        }
        val size = sizeValue.value.toInt()
        if (size < 0) {
            operandStack.pushMove(sizeValue)
            return VMResult.ARRAY_INDEX_OUT_OF_BOUNDS
        }
        val ref = memoryManager.newBoolArray(size)
        operandStack.pushMove(ref)
        return VMResult.SUCCESS
    }

    private fun handleArrayLoad(): VMResult {
        if (operandStack.size() < 2) {
            return VMResult.STACK_UNDERFLOW
        }
        val indexValue = operandStack.popMove()
        val arrayRef = operandStack.popMove()
        
        if (indexValue !is Value.IntValue) {
            operandStack.pushMove(arrayRef)
            operandStack.pushMove(indexValue)
            return VMResult.INVALID_VALUE_TYPE
        }
        if (arrayRef !is Value.ArrayRef) {
            operandStack.pushMove(arrayRef)
            operandStack.pushMove(indexValue)
            return VMResult.INVALID_VALUE_TYPE
        }
        
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
        if (operandStack.size() < 3) {
            return VMResult.STACK_UNDERFLOW
        }
        val value = operandStack.popMove()
        val indexValue = operandStack.popMove()
        val arrayRef = operandStack.popMove()
        
        if (indexValue !is Value.IntValue) {
            operandStack.pushMove(arrayRef)
            operandStack.pushMove(indexValue)
            operandStack.pushMove(value)
            return VMResult.INVALID_VALUE_TYPE
        }
        if (arrayRef !is Value.ArrayRef) {
            operandStack.pushMove(arrayRef)
            operandStack.pushMove(indexValue)
            operandStack.pushMove(value)
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
        if (operandStack.size() < 1) {
            return VMResult.STACK_UNDERFLOW
        }
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
        if (operandStack.size() < 1) {
            return VMResult.STACK_UNDERFLOW
        }
        val arrayRef = operandStack.popMove()
        if (arrayRef !is Value.ArrayRef) {
            operandStack.pushMove(arrayRef)
            return VMResult.INVALID_VALUE_TYPE
        }
        
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

