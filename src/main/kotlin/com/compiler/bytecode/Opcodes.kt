package com.compiler.bytecode

/**
 * Operation codes (opcodes) for the virtual machine bytecode.
 * Each instruction has a fixed size of 4 bytes: [OPCODE: 1 byte] [OPERAND: 3 bytes]
 */
object Opcodes {
    // Constants (0x00-0x0F)
    const val PUSH_INT = 0x01.toByte()
    const val PUSH_FLOAT = 0x02.toByte()
    const val PUSH_BOOL = 0x03.toByte()
    const val POP = 0x04.toByte()

    // Local variables (0x10-0x1F)
    const val LOAD_LOCAL = 0x10.toByte()
    const val STORE_LOCAL = 0x11.toByte()

    // Arithmetic operations - int (0x20-0x2F)
    const val ADD_INT = 0x20.toByte()
    const val SUB_INT = 0x21.toByte()
    const val MUL_INT = 0x22.toByte()
    const val DIV_INT = 0x23.toByte()
    const val MOD_INT = 0x24.toByte()
    const val NEG_INT = 0x25.toByte()

    // Arithmetic operations - float (0x30-0x3F)
    const val ADD_FLOAT = 0x30.toByte()
    const val SUB_FLOAT = 0x31.toByte()
    const val MUL_FLOAT = 0x32.toByte()
    const val DIV_FLOAT = 0x33.toByte()
    const val NEG_FLOAT = 0x35.toByte()

    // Comparison operations - int (0x40-0x4F)
    const val EQ_INT = 0x40.toByte()
    const val NE_INT = 0x41.toByte()
    const val LT_INT = 0x42.toByte()
    const val LE_INT = 0x43.toByte()
    const val GT_INT = 0x44.toByte()
    const val GE_INT = 0x45.toByte()

    // Comparison operations - float (0x50-0x5F)
    const val EQ_FLOAT = 0x50.toByte()
    const val NE_FLOAT = 0x51.toByte()
    const val LT_FLOAT = 0x52.toByte()
    const val LE_FLOAT = 0x53.toByte()
    const val GT_FLOAT = 0x54.toByte()
    const val GE_FLOAT = 0x55.toByte()

    // Logical operations (0x60-0x6F)
    const val AND = 0x60.toByte()
    const val OR = 0x61.toByte()
    const val NOT = 0x62.toByte()

    // Control flow (0x70-0x7F)
    const val JUMP = 0x70.toByte()
    const val JUMP_IF_FALSE = 0x71.toByte()
    const val JUMP_IF_TRUE = 0x72.toByte()

    // Functions (0x80-0x8F)
    const val CALL = 0x80.toByte()
    const val RETURN = 0x81.toByte()
    const val RETURN_VOID = 0x82.toByte()

    // Arrays (0x90-0x9F)
    const val NEW_ARRAY_INT = 0x90.toByte()
    const val NEW_ARRAY_FLOAT = 0x91.toByte()
    const val NEW_ARRAY_BOOL = 0x94.toByte()
    const val ARRAY_LOAD = 0x92.toByte()
    const val ARRAY_STORE = 0x93.toByte()

    // Built-in functions (0xA0-0xAF)
    const val PRINT = 0xA0.toByte()
    const val PRINT_ARRAY = 0xA1.toByte()
}

