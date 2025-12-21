package com.compiler.vm

import com.compiler.bytecode.*
import com.compiler.parser.ast.TypeNode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.io.PrintStream

class VirtualMachineTest {

    // ========== Helper Functions ==========

    private fun createModule(
        intConstants: List<Long> = emptyList(),
        floatConstants: List<Double> = emptyList(),
        functions: List<CompiledFunction>
    ): BytecodeModule {
        return BytecodeModule(
            intConstants = intConstants,
            floatConstants = floatConstants,
            functions = functions,
            entryPoint = "main"
        )
    }

    private fun createMainFunction(instructions: ByteArray, localsCount: Int = 0): CompiledFunction {
        return CompiledFunction(
            name = "main",
            parameters = emptyList(),
            returnType = TypeNode.VoidType,
            localsCount = localsCount,
            instructions = instructions
        )
    }

    private fun createFunction(
        name: String,
        instructions: ByteArray,
        parameters: List<ParameterInfo> = emptyList(),
        returnType: TypeNode = TypeNode.VoidType,
        localsCount: Int = 0
    ): CompiledFunction {
        return CompiledFunction(
            name = name,
            parameters = parameters,
            returnType = returnType,
            localsCount = localsCount,
            instructions = instructions
        )
    }

    // ========== Constant Tests ==========

    @Test
    fun `push int constant`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_INT, 0) // constant index
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(
            intConstants = listOf(42L),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `push float constant`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_FLOAT, 0) // constant index
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(
            floatConstants = listOf(3.14),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `push bool true`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_BOOL, 1) // true
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(functions = listOf(createMainFunction(builder.build())))
        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `push bool false`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_BOOL, 0) // false
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(functions = listOf(createMainFunction(builder.build())))
        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `invalid constant index returns error`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_INT, 999) // invalid index

        val module = createModule(
            intConstants = listOf(42L),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.INVALID_CONSTANT_INDEX, result)
    }

    // ========== Local Variable Tests ==========

    @Test
    fun `store and load local`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_INT, 0) // constant 100
        builder.emit(Opcodes.STORE_LOCAL, 0) // store to local[0]
        builder.emit(Opcodes.LOAD_LOCAL, 0) // load from local[0]
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(
            intConstants = listOf(100L),
            functions = listOf(createMainFunction(builder.build(), localsCount = 1))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `invalid local index returns error`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_INT, 0)
        builder.emit(Opcodes.STORE_LOCAL, 999) // invalid index

        val module = createModule(
            intConstants = listOf(100L),
            functions = listOf(createMainFunction(builder.build(), localsCount = 1))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.INVALID_LOCAL_INDEX, result)
    }

    // ========== Integer Arithmetic Tests ==========

    @Test
    fun `add int`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_INT, 0) // 10
        builder.emit(Opcodes.PUSH_INT, 1) // 20
        builder.emit(Opcodes.ADD_INT)
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(
            intConstants = listOf(10L, 20L),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `sub int`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_INT, 0) // 20
        builder.emit(Opcodes.PUSH_INT, 1) // 10
        builder.emit(Opcodes.SUB_INT)
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(
            intConstants = listOf(20L, 10L),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `mul int`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_INT, 0) // 5
        builder.emit(Opcodes.PUSH_INT, 1) // 6
        builder.emit(Opcodes.MUL_INT)
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(
            intConstants = listOf(5L, 6L),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `div int`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_INT, 0) // 20
        builder.emit(Opcodes.PUSH_INT, 1) // 4
        builder.emit(Opcodes.DIV_INT)
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(
            intConstants = listOf(20L, 4L),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `division by zero returns error`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_INT, 0) // 10
        builder.emit(Opcodes.PUSH_INT, 1) // 0
        builder.emit(Opcodes.DIV_INT)

        val module = createModule(
            intConstants = listOf(10L, 0L),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.DIVISION_BY_ZERO, result)
    }

    @Test
    fun `mod int`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_INT, 0) // 17
        builder.emit(Opcodes.PUSH_INT, 1) // 5
        builder.emit(Opcodes.MOD_INT)
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(
            intConstants = listOf(17L, 5L),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `neg int`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_INT, 0) // 42
        builder.emit(Opcodes.NEG_INT)
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(
            intConstants = listOf(42L),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `stack underflow on arithmetic`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_INT, 0) // only one value
        builder.emit(Opcodes.ADD_INT) // need two

        val module = createModule(
            intConstants = listOf(10L),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.STACK_UNDERFLOW, result)
    }

    // ========== Float Arithmetic Tests ==========

    @Test
    fun `add float`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_FLOAT, 0) // 1.5
        builder.emit(Opcodes.PUSH_FLOAT, 1) // 2.5
        builder.emit(Opcodes.ADD_FLOAT)
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(
            floatConstants = listOf(1.5, 2.5),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `div float`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_FLOAT, 0) // 10.0
        builder.emit(Opcodes.PUSH_FLOAT, 1) // 2.0
        builder.emit(Opcodes.DIV_FLOAT)
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(
            floatConstants = listOf(10.0, 2.0),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `neg float`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_FLOAT, 0) // 3.14
        builder.emit(Opcodes.NEG_FLOAT)
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(
            floatConstants = listOf(3.14),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    // ========== Integer Comparison Tests ==========

    @Test
    fun `eq int`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_INT, 0) // 10
        builder.emit(Opcodes.PUSH_INT, 0) // 10
        builder.emit(Opcodes.EQ_INT)
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(
            intConstants = listOf(10L),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `lt int`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_INT, 0) // 5
        builder.emit(Opcodes.PUSH_INT, 1) // 10
        builder.emit(Opcodes.LT_INT)
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(
            intConstants = listOf(5L, 10L),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `gt int`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_INT, 0) // 10
        builder.emit(Opcodes.PUSH_INT, 1) // 5
        builder.emit(Opcodes.GT_INT)
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(
            intConstants = listOf(10L, 5L),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    // ========== Logical Operation Tests ==========

    @Test
    fun `and operation`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_BOOL, 1) // true
        builder.emit(Opcodes.PUSH_BOOL, 1) // true
        builder.emit(Opcodes.AND)
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(functions = listOf(createMainFunction(builder.build())))
        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `or operation`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_BOOL, 0) // false
        builder.emit(Opcodes.PUSH_BOOL, 1) // true
        builder.emit(Opcodes.OR)
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(functions = listOf(createMainFunction(builder.build())))
        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `not operation`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_BOOL, 1) // true
        builder.emit(Opcodes.NOT)
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(functions = listOf(createMainFunction(builder.build())))
        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    // ========== Control Flow Tests ==========

    @Test
    fun `jump forward`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.JUMP, 1) // skip next instruction
        builder.emit(Opcodes.PUSH_INT, 0) // this instruction will be skipped
        builder.emit(Opcodes.RETURN_VOID) // jump here

        val module = createModule(
            intConstants = listOf(42L),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `jump if false - condition false`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_BOOL, 0) // false
        builder.emit(Opcodes.JUMP_IF_FALSE, 1) // jump if false
        builder.emit(Opcodes.PUSH_INT, 0) // skip this
        builder.emit(Opcodes.RETURN_VOID) // jump here

        val module = createModule(
            intConstants = listOf(42L),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `jump if false - condition true`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_BOOL, 1) // true
        builder.emit(Opcodes.JUMP_IF_FALSE, 1) // don't jump
        builder.emit(Opcodes.PUSH_INT, 0) // execute this
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(
            intConstants = listOf(42L),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `jump if true - condition true`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_BOOL, 1) // true
        builder.emit(Opcodes.JUMP_IF_TRUE, 1) // jump if true
        builder.emit(Opcodes.PUSH_INT, 0) // skip this
        builder.emit(Opcodes.RETURN_VOID) // jump here

        val module = createModule(
            intConstants = listOf(42L),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    // ========== Function Tests ==========

    @Test
    fun `call function and return`() {
        // Function add: takes two int, returns int
        val addBuilder = InstructionBuilder()
        addBuilder.emit(Opcodes.LOAD_LOCAL, 0) // first parameter
        addBuilder.emit(Opcodes.LOAD_LOCAL, 1) // second parameter
        addBuilder.emit(Opcodes.ADD_INT)
        addBuilder.emit(Opcodes.RETURN) // return result

        val addFunction = createFunction(
            name = "add",
            parameters = listOf(
                ParameterInfo("a", TypeNode.IntType),
                ParameterInfo("b", TypeNode.IntType)
            ),
            returnType = TypeNode.IntType,
            localsCount = 2,
            instructions = addBuilder.build()
        )

        // main: calls add(10, 20)
        val mainBuilder = InstructionBuilder()
        mainBuilder.emit(Opcodes.PUSH_INT, 0) // 10
        mainBuilder.emit(Opcodes.PUSH_INT, 1) // 20
        mainBuilder.emit(Opcodes.CALL, 1) // call function at index 1 (add)
        mainBuilder.emit(Opcodes.RETURN_VOID)

        val mainFunction = createMainFunction(mainBuilder.build())

        val module = createModule(
            intConstants = listOf(10L, 20L),
            functions = listOf(mainFunction, addFunction)
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `return void`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(functions = listOf(createMainFunction(builder.build())))
        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `invalid function index returns error`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.CALL, 999) // invalid index

        val module = createModule(functions = listOf(createMainFunction(builder.build())))
        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.INVALID_FUNCTION_INDEX, result)
    }

    // ========== Array Tests ==========

    @Test
    fun `new int array`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_INT, 0) // size 5
        builder.emit(Opcodes.NEW_ARRAY_INT)
        builder.emit(Opcodes.POP) // remove reference from stack
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(
            intConstants = listOf(5L),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `new float array`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_INT, 0) // size 3
        builder.emit(Opcodes.NEW_ARRAY_FLOAT)
        builder.emit(Opcodes.POP)
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(
            intConstants = listOf(3L),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `new bool array`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_INT, 0) // size 2
        builder.emit(Opcodes.NEW_ARRAY_BOOL)
        builder.emit(Opcodes.POP)
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(
            intConstants = listOf(2L),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `negative array size returns error`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.PUSH_INT, 0) // -1
        builder.emit(Opcodes.NEW_ARRAY_INT)

        val module = createModule(
            intConstants = listOf(-1L),
            functions = listOf(createMainFunction(builder.build()))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.ARRAY_INDEX_OUT_OF_BOUNDS, result)
    }

    @Test
    fun `array load and store int`() {
        val builder = InstructionBuilder()
        // Create array of size 3
        builder.emit(Opcodes.PUSH_INT, 0) // size 3
        builder.emit(Opcodes.NEW_ARRAY_INT)
        builder.emit(Opcodes.STORE_LOCAL, 0) // store to local[0]
        
        // Write value to index 1
        builder.emit(Opcodes.LOAD_LOCAL, 0) // array
        builder.emit(Opcodes.PUSH_INT, 1) // index 1
        builder.emit(Opcodes.PUSH_INT, 2) // value 42
        builder.emit(Opcodes.ARRAY_STORE)
        
        // Read value from index 1
        builder.emit(Opcodes.LOAD_LOCAL, 0) // array
        builder.emit(Opcodes.PUSH_INT, 1) // index 1
        builder.emit(Opcodes.ARRAY_LOAD)
        builder.emit(Opcodes.POP) // remove result
        
        builder.emit(Opcodes.RETURN_VOID)

        val module = createModule(
            intConstants = listOf(3L, 1L, 42L),
            functions = listOf(createMainFunction(builder.build(), localsCount = 1))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    // ========== Built-in Function Tests ==========

    @Test
    fun `print int`() {
        val output = ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(PrintStream(output))

        try {
            val builder = InstructionBuilder()
            builder.emit(Opcodes.PUSH_INT, 0) // 42
            builder.emit(Opcodes.PRINT)
            builder.emit(Opcodes.RETURN_VOID)

            val module = createModule(
                intConstants = listOf(42L),
                functions = listOf(createMainFunction(builder.build()))
            )

            val vm = VirtualMachine(module)
            val result = vm.execute()
            assertEquals(VMResult.SUCCESS, result)
            assertEquals("42", output.toString().trim())
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `print float`() {
        val output = ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(PrintStream(output))

        try {
            val builder = InstructionBuilder()
            builder.emit(Opcodes.PUSH_FLOAT, 0) // 3.14
            builder.emit(Opcodes.PRINT)
            builder.emit(Opcodes.RETURN_VOID)

            val module = createModule(
                floatConstants = listOf(3.14),
                functions = listOf(createMainFunction(builder.build()))
            )

            val vm = VirtualMachine(module)
            val result = vm.execute()
            assertEquals(VMResult.SUCCESS, result)
            assertTrue(output.toString().contains("3.14"))
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `print bool`() {
        val output = ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(PrintStream(output))

        try {
            val builder = InstructionBuilder()
            builder.emit(Opcodes.PUSH_BOOL, 1) // true
            builder.emit(Opcodes.PRINT)
            builder.emit(Opcodes.RETURN_VOID)

            val module = createModule(functions = listOf(createMainFunction(builder.build())))
            val vm = VirtualMachine(module)
            val result = vm.execute()
            assertEquals(VMResult.SUCCESS, result)
            assertEquals("true", output.toString().trim())
        } finally {
            System.setOut(originalOut)
        }
    }

    @Test
    fun `print array`() {
        val output = ByteArrayOutputStream()
        val originalOut = System.out
        System.setOut(PrintStream(output))

        try {
            val builder = InstructionBuilder()
            // Create array [10, 20, 30]
            builder.emit(Opcodes.PUSH_INT, 0) // size 3
            builder.emit(Opcodes.NEW_ARRAY_INT)
            builder.emit(Opcodes.STORE_LOCAL, 0)
            
            // Write values
            builder.emit(Opcodes.LOAD_LOCAL, 0)
            builder.emit(Opcodes.PUSH_INT, 1) // index 0
            builder.emit(Opcodes.PUSH_INT, 2) // value 10
            builder.emit(Opcodes.ARRAY_STORE)
            
            builder.emit(Opcodes.LOAD_LOCAL, 0)
            builder.emit(Opcodes.PUSH_INT, 3) // index 1
            builder.emit(Opcodes.PUSH_INT, 4) // value 20
            builder.emit(Opcodes.ARRAY_STORE)
            
            builder.emit(Opcodes.LOAD_LOCAL, 0)
            builder.emit(Opcodes.PUSH_INT, 5) // index 2
            builder.emit(Opcodes.PUSH_INT, 6) // value 30
            builder.emit(Opcodes.ARRAY_STORE)
            
            // Print array
            builder.emit(Opcodes.LOAD_LOCAL, 0)
            builder.emit(Opcodes.PRINT_ARRAY)
            builder.emit(Opcodes.RETURN_VOID)

            val module = createModule(
                intConstants = listOf(3L, 0L, 10L, 1L, 20L, 2L, 30L),
                functions = listOf(createMainFunction(builder.build(), localsCount = 1))
            )

            val vm = VirtualMachine(module)
            val result = vm.execute()
            assertEquals(VMResult.SUCCESS, result)
            val outputStr = output.toString().trim()
            assertTrue(outputStr.startsWith("[") && outputStr.endsWith("]"))
        } finally {
            System.setOut(originalOut)
        }
    }

    // ========== Error Tests ==========

    @Test
    fun `invalid opcode returns error`() {
        val builder = InstructionBuilder()
        builder.emit(0xFF.toByte(), 0) // invalid opcode

        val module = createModule(functions = listOf(createMainFunction(builder.build())))
        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.INVALID_OPCODE, result)
    }

    @Test
    fun `stack underflow on return`() {
        val builder = InstructionBuilder()
        builder.emit(Opcodes.RETURN) // no value on stack

        val module = createModule(functions = listOf(createMainFunction(builder.build())))
        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.STACK_UNDERFLOW, result)
    }

    // ========== Complex Tests ==========

    @Test
    fun `factorial calculation`() {
        // Function factorial(n): if n <= 1 then 1 else n * factorial(n-1)
        val factBuilder = InstructionBuilder()
        
        // Create labels for if-else
        val recursiveCaseLabel = factBuilder.createLabel("recursive_case")
        val afterIfLabel = factBuilder.createLabel("after_if")
        
        // Load parameter n
        factBuilder.emit(Opcodes.LOAD_LOCAL, 0)       // instruction 0
        factBuilder.emit(Opcodes.PUSH_INT, 0)         // instruction 1 (constant 1)
        factBuilder.emit(Opcodes.LE_INT)              // instruction 2 (n <= 1)
        factBuilder.emitJump(Opcodes.JUMP_IF_FALSE, recursiveCaseLabel) // instruction 3
        
        // Base case: return 1
        factBuilder.emit(Opcodes.PUSH_INT, 0)         // instruction 4
        factBuilder.emit(Opcodes.RETURN)              // instruction 5
        
        // Recursive case: n * factorial(n-1)
        factBuilder.defineLabel(recursiveCaseLabel.name) // instruction 6
        factBuilder.emit(Opcodes.LOAD_LOCAL, 0)       // instruction 6 (n for multiplication)
        factBuilder.emit(Opcodes.LOAD_LOCAL, 0)       // instruction 7 (n for subtraction)
        factBuilder.emit(Opcodes.PUSH_INT, 0)         // instruction 8 (constant 1)
        factBuilder.emit(Opcodes.SUB_INT)             // instruction 9 (n - 1)
        factBuilder.emit(Opcodes.CALL, 1)             // instruction 10 (factorial(n-1))
        factBuilder.emit(Opcodes.MUL_INT)             // instruction 11 (n * factorial(n-1))
        factBuilder.emit(Opcodes.RETURN)              // instruction 12
        
        factBuilder.defineLabel(afterIfLabel.name) // This label is never reached, but defined for consistency

        val factFunction = createFunction(
            name = "factorial",
            parameters = listOf(ParameterInfo("n", TypeNode.IntType)),
            returnType = TypeNode.IntType,
            localsCount = 1,
            instructions = factBuilder.build()
        )

        // main: factorial(5)
        val mainBuilder = InstructionBuilder()
        mainBuilder.emit(Opcodes.PUSH_INT, 1) // 5 (constant index 1)
        mainBuilder.emit(Opcodes.CALL, 1) // factorial (function index 1)
        mainBuilder.emit(Opcodes.POP) // remove result from stack
        mainBuilder.emit(Opcodes.RETURN_VOID)

        val mainFunction = createMainFunction(mainBuilder.build())

        val module = createModule(
            intConstants = listOf(1L, 5L),
            functions = listOf(mainFunction, factFunction) // main=0, factorial=1
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }

    @Test
    fun `simple loop`() {
        // main: for i in 0..2: print i
        val builder = InstructionBuilder()
        
        // Create labels for loop
        val loopStartLabel = builder.createLabel("loop_start")
        val exitLabel = builder.createLabel("exit")
        
        // i = 0
        builder.emit(Opcodes.PUSH_INT, 0) // instruction 0
        builder.emit(Opcodes.STORE_LOCAL, 0) // instruction 1
        
        // loop: if i > 2 then exit
        builder.defineLabel(loopStartLabel.name) // instruction 2
        builder.emit(Opcodes.LOAD_LOCAL, 0) // instruction 2: load i
        builder.emit(Opcodes.PUSH_INT, 1) // instruction 3: constant 2
        builder.emit(Opcodes.GT_INT) // instruction 4: i > 2
        builder.emitJump(Opcodes.JUMP_IF_TRUE, exitLabel) // instruction 5
        
        // print i
        builder.emit(Opcodes.LOAD_LOCAL, 0) // instruction 6
        builder.emit(Opcodes.PRINT) // instruction 7
        
        // i = i + 1
        builder.emit(Opcodes.LOAD_LOCAL, 0) // instruction 8
        builder.emit(Opcodes.PUSH_INT, 2) // instruction 9: constant 1
        builder.emit(Opcodes.ADD_INT) // instruction 10
        builder.emit(Opcodes.STORE_LOCAL, 0) // instruction 11
        
        // jump to loop start
        builder.emitJump(Opcodes.JUMP, loopStartLabel) // instruction 12
        
        // exit
        builder.defineLabel(exitLabel.name) // instruction 13
        builder.emit(Opcodes.RETURN_VOID) // instruction 13

        val module = createModule(
            intConstants = listOf(0L, 2L, 1L),
            functions = listOf(createMainFunction(builder.build(), localsCount = 1))
        )

        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result)
    }
}

