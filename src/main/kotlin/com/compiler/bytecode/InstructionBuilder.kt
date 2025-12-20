package com.compiler.bytecode

/**
 * Helper class for building function bytecode.
 * Stores instructions as a list of bytes and provides methods for adding instructions.
 */
class InstructionBuilder {
    private val instructions = mutableListOf<Byte>()
    
    /**
     * Adds an instruction with opcode and operand.
     * Instruction format: [OPCODE: 1 byte] [OPERAND: 3 bytes]
     */
    fun emit(opcode: Byte, operand: Int = 0) {
        instructions.add(opcode)
        // Operand is written as 3 bytes (big-endian)
        instructions.add(((operand shr 16) and 0xFF).toByte())
        instructions.add(((operand shr 8) and 0xFF).toByte())
        instructions.add((operand and 0xFF).toByte())
    }
    
    /**
     * Returns the current address (index of the next instruction).
     * Address is specified in number of instructions (not bytes).
     */
    fun currentAddress(): Int = instructions.size / 4
    
    /**
     * Patches the operand of an instruction at the given address.
     * @param address instruction address (in number of instructions)
     * @param operand new operand value
     */
    fun patchOperand(address: Int, operand: Int) {
        val byteIndex = address * 4 + 1 // +1 to skip opcode
        if (byteIndex + 2 >= instructions.size) {
            throw IllegalArgumentException("Invalid address for patching: $address")
        }
        instructions[byteIndex] = ((operand shr 16) and 0xFF).toByte()
        instructions[byteIndex + 1] = ((operand shr 8) and 0xFF).toByte()
        instructions[byteIndex + 2] = (operand and 0xFF).toByte()
    }
    
    /**
     * Returns the opcode of the last instruction, or null if there are no instructions.
     */
    fun getLastOpcode(): Byte? {
        return if (instructions.size >= 4) {
            instructions[instructions.size - 4]
        } else {
            null
        }
    }
    
    /**
     * Returns the final byte array with instructions.
     */
    fun build(): ByteArray = instructions.toByteArray()
}

