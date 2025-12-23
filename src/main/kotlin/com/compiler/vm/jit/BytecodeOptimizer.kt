package com.compiler.vm.jit

import com.compiler.bytecode.CompiledFunction
import com.compiler.bytecode.InstructionBuilder
import com.compiler.bytecode.Opcodes

/**
 * Optimizes bytecode by replacing common patterns with optimized instructions.
 */
object BytecodeOptimizer {
    
    /**
     * Optimizes a function's bytecode.
     * Returns a new CompiledFunction with optimized instructions.
     * 
     * @param function function to optimize
     * @param module module containing constants (for checking constant values)
     */
    fun optimizeFunction(function: CompiledFunction, module: com.compiler.bytecode.BytecodeModule? = null): CompiledFunction {
        val originalInstructions = function.instructions
        val optimized = optimizeInstructions(originalInstructions, module)
        
        // Validate that optimized instructions are not empty
        if (optimized.isEmpty() && originalInstructions.isNotEmpty()) {
            // If optimization failed, return original
            return function
        }
        
        return function.copy(instructions = optimized)
    }
    
    /**
     * Optimizes bytecode instructions by replacing patterns:
     * - LOAD_LOCAL #X, PUSH_INT #constIndex, ADD_INT, STORE_LOCAL #X -> INC_LOCAL #X (if const == 1)
     * - LOAD_LOCAL #X, PUSH_INT #constIndex, SUB_INT, STORE_LOCAL #X -> DEC_LOCAL #X (if const == 1)
     * 
     * Also recalculates relative jump addresses after optimizations.
     */
    private fun optimizeInstructions(instructions: ByteArray, module: com.compiler.bytecode.BytecodeModule?): ByteArray {
        // First pass: find all optimizations and build address mapping
        val optimizations = mutableMapOf<Int, Triple<Byte, Int, Int>>() // original address -> (newOpcode, newOperand, skipCount)
        var originalAddress = 0
        
        while (originalAddress * 4 < instructions.size) {
            val byteIndex = originalAddress * 4
            if (byteIndex + 3 >= instructions.size) {
                break
            }
            
            val opcode = instructions[byteIndex]
            val operand = readOperand(instructions, byteIndex + 1)
            
            // Try to match optimization patterns
            val optimized = tryOptimizePattern(instructions, originalAddress, opcode, operand, module)
            if (optimized != null) {
                optimizations[originalAddress] = optimized
                originalAddress += optimized.third // Skip the matched instructions
            } else {
                originalAddress++
            }
        }
        
        // Build address mapping: original address -> new address
        val addressMap = mutableMapOf<Int, Int>()
        var newAddress = 0
        originalAddress = 0
        while (originalAddress * 4 < instructions.size) {
            addressMap[originalAddress] = newAddress
            if (optimizations.containsKey(originalAddress)) {
                val opt = optimizations[originalAddress]!!
                newAddress += 1 // Optimized to 1 instruction
                originalAddress += opt.third // Skip original instructions
            } else {
                newAddress++
                originalAddress++
            }
        }
        
        // Second pass: build optimized bytecode with recalculated jump addresses
        val builder = InstructionBuilder()
        originalAddress = 0
        
        while (originalAddress * 4 < instructions.size) {
            val byteIndex = originalAddress * 4
            if (byteIndex + 3 >= instructions.size) {
                // Incomplete instruction - copy as is
                for (i in byteIndex until instructions.size) {
                    builder.emitRawByte(instructions[i])
                }
                break
            }
            
            val opcode = instructions[byteIndex]
            val operand = readOperand(instructions, byteIndex + 1)
            
            if (optimizations.containsKey(originalAddress)) {
                // Pattern matched and optimized
                val opt = optimizations[originalAddress]!!
                builder.emit(opt.first, opt.second)
                originalAddress += opt.third
            } else {
                // Check if this is a jump instruction - need to recalculate address
                if (opcode == Opcodes.JUMP || opcode == Opcodes.JUMP_IF_FALSE || opcode == Opcodes.JUMP_IF_TRUE) {
                    // Recalculate jump address
                    val signedOperand = if (operand and 0x800000 != 0) {
                        operand or 0xFF000000.toInt() // Sign extension
                    } else {
                        operand
                    }
                    val targetOriginalAddress = originalAddress + signedOperand
                    val targetNewAddress = addressMap[targetOriginalAddress] ?: targetOriginalAddress
                    val newOperand = targetNewAddress - addressMap[originalAddress]!!
                    builder.emit(opcode, newOperand)
                } else {
                    // No optimization - copy instruction as is
                    builder.emit(opcode, operand)
                }
                originalAddress++
            }
        }
        
        return builder.build()
    }
    
    /**
     * Tries to optimize a pattern starting at the given address.
     * Returns (newOpcode, newOperand, skipCount) if pattern matched, null otherwise.
     */
    private fun tryOptimizePattern(
        instructions: ByteArray,
        startAddress: Int,
        opcode: Byte,
        operand: Int,
        module: com.compiler.bytecode.BytecodeModule?
    ): Triple<Byte, Int, Int>? {
        val startByteIndex = startAddress * 4
        
        // Pattern 1: LOAD_LOCAL #X, PUSH_INT #constIndex, ADD_INT, STORE_LOCAL #X -> INC_LOCAL #X
        // (if constIndex points to constant 1)
        if (opcode == Opcodes.LOAD_LOCAL) {
            val localIndex = operand
            
            // Check if we have at least 4 instructions total (16 bytes)
            if (startByteIndex + 15 < instructions.size) {
                val opcode1 = instructions[startByteIndex + 4]
                val operand1 = readOperand(instructions, startByteIndex + 5)
                val opcode2 = instructions[startByteIndex + 8]
                val opcode3 = instructions[startByteIndex + 12]
                val operand3 = readOperand(instructions, startByteIndex + 13)
                
                // Check for: PUSH_INT #constIndex, ADD_INT, STORE_LOCAL #X
                // Verify that constIndex points to constant 1
                val isConstantOne = if (module != null && operand1 >= 0 && operand1 < module.intConstants.size) {
                    module.intConstants[operand1] == 1L
                } else {
                    // If module not provided, assume index 0 is constant 1 (common case)
                    operand1 == 0
                }
                
                if (opcode1 == Opcodes.PUSH_INT && isConstantOne &&
                    opcode2 == Opcodes.ADD_INT &&
                    opcode3 == Opcodes.STORE_LOCAL && operand3 == localIndex) {
                    return Triple(Opcodes.INC_LOCAL, localIndex, 4)
                }
            }
            
            // Pattern 2: LOAD_LOCAL #X, PUSH_INT #constIndex, SUB_INT, STORE_LOCAL #X -> DEC_LOCAL #X
            // (if constIndex points to constant 1)
            if (startByteIndex + 15 < instructions.size) {
                val opcode1 = instructions[startByteIndex + 4]
                val operand1 = readOperand(instructions, startByteIndex + 5)
                val opcode2 = instructions[startByteIndex + 8]
                val opcode3 = instructions[startByteIndex + 12]
                val operand3 = readOperand(instructions, startByteIndex + 13)
                
                // Check for: PUSH_INT #constIndex, SUB_INT, STORE_LOCAL #X
                // Verify that constIndex points to constant 1
                val isConstantOne = if (module != null && operand1 >= 0 && operand1 < module.intConstants.size) {
                    module.intConstants[operand1] == 1L
                } else {
                    // If module not provided, assume index 0 is constant 1 (common case)
                    operand1 == 0
                }
                
                if (opcode1 == Opcodes.PUSH_INT && isConstantOne &&
                    opcode2 == Opcodes.SUB_INT &&
                    opcode3 == Opcodes.STORE_LOCAL && operand3 == localIndex) {
                    return Triple(Opcodes.DEC_LOCAL, localIndex, 4)
                }
            }
        }
        
        return null
    }
    
    /**
     * Reads a 3-byte operand from the byte array (big-endian).
     */
    private fun readOperand(bytes: ByteArray, startIndex: Int): Int {
        if (startIndex + 2 >= bytes.size) return 0
        return ((bytes[startIndex].toInt() and 0xFF) shl 16) or
               ((bytes[startIndex + 1].toInt() and 0xFF) shl 8) or
               (bytes[startIndex + 2].toInt() and 0xFF)
    }
}

