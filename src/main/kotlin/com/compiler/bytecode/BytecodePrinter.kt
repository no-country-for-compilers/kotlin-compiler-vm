package com.compiler.bytecode

import com.compiler.parser.ast.TypeNode

/**
 * Utility class for printing bytecode in human-readable format.
 */
object BytecodePrinter {
    
    private fun typeToString(type: TypeNode): String =
        when (type) {
            TypeNode.IntType -> "int"
            TypeNode.FloatType -> "float"
            TypeNode.BoolType -> "bool"
            TypeNode.VoidType -> "void"
            is TypeNode.ArrayType -> "${typeToString(type.elementType)}[]"
        }
    
    /**
     * Prints the entire bytecode module in a human-readable format.
     */
    fun printModule(module: BytecodeModule) {
        println("=== Bytecode Module ===")
        println("Entry Point: ${module.entryPoint}")
        println()
        
        // Print constant pools
        if (module.intConstants.isNotEmpty()) {
            println("Integer Constants:")
            module.intConstants.forEachIndexed { index, value ->
                println("  [$index] = $value")
            }
            println()
        }
        
        if (module.floatConstants.isNotEmpty()) {
            println("Float Constants:")
            module.floatConstants.forEachIndexed { index, value ->
                println("  [$index] = $value")
            }
            println()
        }
        
        // Print functions
        println("Functions (${module.functions.size}):")
        module.functions.forEachIndexed { index, function ->
            println()
            printFunction(function, index)
        }
    }
    
    /**
     * Prints a single function's bytecode in human-readable format.
     */
    fun printFunction(function: CompiledFunction, functionIndex: Int = -1) {
        val indexStr = if (functionIndex >= 0) "[$functionIndex] " else ""
        println("$indexStr${function.name}(")
        
        // Print parameters
        if (function.parameters.isNotEmpty()) {
            function.parameters.forEachIndexed { i, param ->
                val comma = if (i < function.parameters.size - 1) "," else ""
                println("    ${param.name}: ${typeToString(param.type)}${comma}")
            }
        }
        println("): ${typeToString(function.returnType)}")
        println("  Locals: ${function.localsCount}")
        println()
        
        // Disassemble instructions
        println("  Instructions:")
        disassembleInstructions(function.instructions).forEach { line ->
            println("    $line")
        }
    }
    
    /**
     * Disassembles bytecode instructions into human-readable format.
     * Returns a list of strings, each representing one instruction.
     */
    fun disassembleInstructions(instructions: ByteArray): List<String> {
        val result = mutableListOf<String>()
        var address = 0
        
        while (address * 4 < instructions.size) {
            val byteIndex = address * 4
            if (byteIndex + 3 >= instructions.size) {
                // Incomplete instruction
                result.add("${String.format("%04d", address)}: <incomplete instruction>")
                break
            }
            
            val opcode = instructions[byteIndex]
            val operand = readOperand(instructions, byteIndex + 1)
            
            val opcodeName = getOpcodeName(opcode)
            val operandStr = formatOperand(opcode, operand, address)
            
            result.add("${String.format("%04d", address)}: $opcodeName $operandStr")
            address++
        }
        
        return result
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
    
    /**
     * Gets the human-readable name of an opcode.
     */
    private fun getOpcodeName(opcode: Byte): String {
        return when (opcode.toInt() and 0xFF) {
            0x01 -> "PUSH_INT"
            0x02 -> "PUSH_FLOAT"
            0x03 -> "PUSH_BOOL"
            0x04 -> "POP"
            0x10 -> "LOAD_LOCAL"
            0x11 -> "STORE_LOCAL"
            0x20 -> "ADD_INT"
            0x21 -> "SUB_INT"
            0x22 -> "MUL_INT"
            0x23 -> "DIV_INT"
            0x24 -> "MOD_INT"
            0x25 -> "NEG_INT"
            0x30 -> "ADD_FLOAT"
            0x31 -> "SUB_FLOAT"
            0x32 -> "MUL_FLOAT"
            0x33 -> "DIV_FLOAT"
            0x35 -> "NEG_FLOAT"
            0x40 -> "EQ_INT"
            0x41 -> "NE_INT"
            0x42 -> "LT_INT"
            0x43 -> "LE_INT"
            0x44 -> "GT_INT"
            0x45 -> "GE_INT"
            0x50 -> "EQ_FLOAT"
            0x51 -> "NE_FLOAT"
            0x52 -> "LT_FLOAT"
            0x53 -> "LE_FLOAT"
            0x54 -> "GT_FLOAT"
            0x55 -> "GE_FLOAT"
            0x60 -> "AND"
            0x61 -> "OR"
            0x62 -> "NOT"
            0x70 -> "JUMP"
            0x71 -> "JUMP_IF_FALSE"
            0x72 -> "JUMP_IF_TRUE"
            0x80 -> "CALL"
            0x81 -> "RETURN"
            0x82 -> "RETURN_VOID"
            0x90 -> "NEW_ARRAY_INT"
            0x91 -> "NEW_ARRAY_FLOAT"
            0x94 -> "NEW_ARRAY_BOOL"
            0x92 -> "ARRAY_LOAD"
            0x93 -> "ARRAY_STORE"
            0xA0 -> "PRINT"
            0xA1 -> "PRINT_ARRAY"
            else -> "UNKNOWN(0x${String.format("%02X", opcode.toInt() and 0xFF)})"
        }
    }
    
    /**
     * Formats an operand for display based on the opcode.
     * @param currentAddress current instruction address (for calculating absolute jump targets)
     */
    private fun formatOperand(opcode: Byte, operand: Int, currentAddress: Int = 0): String {
        val opcodeInt = opcode.toInt() and 0xFF
        
        return when (opcodeInt) {
            0x03 -> { // PUSH_BOOL
                if (operand == 0) "false" else "true"
            }
            0x01, 0x02, 0x10, 0x11, 0x80, 0x90, 0x91, 0x94 -> {
                // Instructions that use indices
                "#$operand"
            }
            0x70, 0x71, 0x72 -> {
                // Jump instructions - convert relative offset to absolute address
                // VM uses: newPC = frame.pc + (signedOperand * 4) + 4, then frame.pc = newPC - 4
                // frame.pc is byte address, currentAddress is instruction number
                // currentByteAddr = currentAddress * 4
                // newByteAddr = currentByteAddr + (operand * 4) + 4
                // newInstructionAddr = (newByteAddr - 4) / 4 = currentAddress + operand
                val signedOperand = if (operand and 0x800000 != 0) {
                    operand or 0xFF000000.toInt() // Sign extension
                } else {
                    operand
                }
                val targetAddress = currentAddress + signedOperand
                "-> $targetAddress"
            }
            else -> {
                if (operand == 0) "" else "$operand"
            }
        }
    }
    
    /**
     * Returns a string representation of the entire module.
     */
    fun moduleToString(module: BytecodeModule): String {
        val sb = StringBuilder()
        sb.appendLine("=== Bytecode Module ===")
        sb.appendLine("Entry Point: ${module.entryPoint}")
        sb.appendLine()
        
        if (module.intConstants.isNotEmpty()) {
            sb.appendLine("Integer Constants:")
            module.intConstants.forEachIndexed { index, value ->
                sb.appendLine("  [$index] = $value")
            }
            sb.appendLine()
        }
        
        if (module.floatConstants.isNotEmpty()) {
            sb.appendLine("Float Constants:")
            module.floatConstants.forEachIndexed { index, value ->
                sb.appendLine("  [$index] = $value")
            }
            sb.appendLine()
        }
        
        sb.appendLine("Functions (${module.functions.size}):")
        module.functions.forEachIndexed { index, function ->
            sb.appendLine()
            sb.append(functionToString(function, index))
        }
        
        return sb.toString()
    }
    
    /**
     * Returns a string representation of a function.
     */
    fun functionToString(function: CompiledFunction, functionIndex: Int = -1): String {
        val sb = StringBuilder()
        val indexStr = if (functionIndex >= 0) "[$functionIndex] " else ""
        sb.appendLine("$indexStr${function.name}(")
        
        if (function.parameters.isNotEmpty()) {
            function.parameters.forEachIndexed { i, param ->
                val comma = if (i < function.parameters.size - 1) "," else ""
                sb.appendLine("    ${param.name}: ${param.type}${comma}")
            }
        }
        sb.appendLine("): ${function.returnType}")
        sb.appendLine("  Locals: ${function.localsCount}")
        sb.appendLine()
        
        sb.appendLine("  Instructions:")
        disassembleInstructions(function.instructions).forEach { line ->
            sb.appendLine("    $line")
        }
        
        return sb.toString()
    }
}

