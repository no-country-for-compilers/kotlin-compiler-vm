package com.compiler.bytecode

import com.compiler.lexer.Lexer
import com.compiler.parser.Parser
import com.compiler.semantic.DefaultSemanticAnalyzer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class BytecodeGeneratorTest {
    private fun compile(source: String): BytecodeModule {
        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val program = parser.parse()
        
        val analyzer = DefaultSemanticAnalyzer()
        val result = analyzer.analyze(program)
        
        if (result.error != null) {
            throw IllegalStateException("Semantic error: ${result.error.message}")
        }
        
        val generator = BytecodeGenerator(program, result.globalScope)
        return generator.generate()
    }
    
    @Test
    fun `generates bytecode for simple function with literal`() {
        val source = """
            func main(): void {
                let x: int = 42;
            }
        """.trimIndent()
        
        val module = compile(source)
        
        assertEquals(1, module.functions.size)
        val main = module.functions[0]
        assertEquals("main", main.name)
        assertEquals(1, main.localsCount) // x
        
        val instructions = main.instructions
        assertTrue(instructions.contains(Opcodes.PUSH_INT), "Should contain PUSH_INT for literal")
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL for variable")
        assertEquals(0, instructions.size % 4, "Instructions should be in 4-byte format")
        
        // Check constant pool
        assertTrue(module.intConstants.contains(42L))
    }
    
    @Test
    fun `generates bytecode for arithmetic operations`() {
        val source = """
            func main(): void {
                let x: int = 5 + 3;
                let y: int = 10 - 2;
                let z: int = 4 * 6;
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        val instructions = main.instructions
        
        // Check arithmetic instructions
        assertTrue(instructions.contains(Opcodes.ADD_INT), "Should contain ADD_INT")
        assertTrue(instructions.contains(Opcodes.SUB_INT), "Should contain SUB_INT")
        assertTrue(instructions.contains(Opcodes.MUL_INT), "Should contain MUL_INT")
        assertTrue(instructions.contains(Opcodes.PUSH_INT), "Should contain PUSH_INT for literals")
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL")
        assertEquals(0, instructions.size % 4, "Instructions should be in 4-byte format")
        
        // Check constants are in pool
        assertTrue(module.intConstants.contains(5L))
        assertTrue(module.intConstants.contains(3L))
        assertTrue(module.intConstants.contains(10L))
        assertTrue(module.intConstants.contains(2L))
        assertTrue(module.intConstants.contains(4L))
        assertTrue(module.intConstants.contains(6L))
    }
    
    @Test
    fun `generates bytecode for float operations`() {
        val source = """
            func main(): void {
                let x: float = 3.14 + 2.5;
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        val instructions = main.instructions
        
        assertTrue(instructions.contains(Opcodes.PUSH_FLOAT), "Should contain PUSH_FLOAT")
        assertTrue(instructions.contains(Opcodes.ADD_FLOAT), "Should contain ADD_FLOAT")
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL")
        
        assertTrue(module.floatConstants.contains(3.14))
        assertTrue(module.floatConstants.contains(2.5))
    }
    
    @Test
    fun `generates bytecode for boolean literals`() {
        val source = """
            func main(): void {
                let x: bool = true;
                let y: bool = false;
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        assertEquals(2, main.localsCount) // x and y
        
        val instructions = main.instructions
        assertTrue(instructions.contains(Opcodes.PUSH_BOOL), "Should contain PUSH_BOOL")
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL")
        assertEquals(0, instructions.size % 4, "Instructions should be in 4-byte format")
    }
    
    @Test
    fun `generates bytecode for comparison operations`() {
        val source = """
            func main(): void {
                let x: bool = 5 > 3;
                let y: bool = 10 < 20;
                let z: bool = 7 == 7;
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        assertEquals(3, main.localsCount)
        
        val instructions = main.instructions
        assertTrue(instructions.contains(Opcodes.GT_INT) || instructions.contains(Opcodes.LT_INT) || instructions.contains(Opcodes.EQ_INT), 
            "Should contain comparison instructions")
        assertTrue(instructions.contains(Opcodes.PUSH_INT), "Should contain PUSH_INT for literals")
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL")
    }
    
    @Test
    fun `generates bytecode for logical operations`() {
        val source = """
            func main(): void {
                let x: bool = true && false;
                let y: bool = true || false;
                let z: bool = !true;
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        assertEquals(3, main.localsCount)
        
        val instructions = main.instructions
        assertTrue(instructions.contains(Opcodes.AND), "Should contain AND instruction")
        assertTrue(instructions.contains(Opcodes.OR), "Should contain OR instruction")
        assertTrue(instructions.contains(Opcodes.NOT), "Should contain NOT instruction")
        assertTrue(instructions.contains(Opcodes.PUSH_BOOL), "Should contain PUSH_BOOL")
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL")
    }
    
    @Test
    fun `generates bytecode for if statement`() {
        val source = """
            func main(): void {
                if (true) {
                    let x: int = 1;
                } else {
                    let x: int = 2;
                }
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        val instructions = main.instructions
        
        assertTrue(instructions.contains(Opcodes.JUMP_IF_FALSE), "Should contain JUMP_IF_FALSE for if condition")
        assertTrue(instructions.contains(Opcodes.JUMP), "Should contain JUMP for else branch")
        assertTrue(instructions.contains(Opcodes.PUSH_INT), "Should contain PUSH_INT for literals")
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL")
        assertTrue(instructions.contains(Opcodes.PUSH_BOOL), "Should contain PUSH_BOOL for condition")
    }
    
    @Test
    fun `generates bytecode for for loop`() {
        val source = """
            func main(): void {
                for (let i: int = 0; i < 10; i = i + 1) {
                    let x: int = i;
                }
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        val instructions = main.instructions
        
        // Should have JUMP instructions for loop
        assertTrue(instructions.contains(Opcodes.JUMP), "Should contain JUMP instruction for loop")
        assertTrue(instructions.contains(Opcodes.JUMP_IF_FALSE), "Should contain JUMP_IF_FALSE for condition")
        
        // Should have STORE_LOCAL for loop variable i
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL for loop variable")
        
        // Should have LOAD_LOCAL for accessing i
        assertTrue(instructions.contains(Opcodes.LOAD_LOCAL), "Should contain LOAD_LOCAL for accessing loop variable")
        
        // Should have comparison instruction (LT_INT)
        assertTrue(instructions.contains(Opcodes.LT_INT), "Should contain LT_INT for condition")
        
        // Check constants
        assertTrue(module.intConstants.contains(0L), "Should have constant 0")
        assertTrue(module.intConstants.contains(10L), "Should have constant 10")
        assertTrue(module.intConstants.contains(1L), "Should have constant 1")
        
        // Check locals: i (parameter of for loop) and x (local in loop body)
        assertTrue(main.localsCount >= 1, "Should have at least loop variable i")
    }
    
    @Test
    fun `generates bytecode for function call`() {
        val source = """
            func factorial(n: int): int {
                return n;
            }
            
            func main(): void {
                let x: int = factorial(5);
            }
        """.trimIndent()
        
        val module = compile(source)
        
        assertEquals(2, module.functions.size)
        assertEquals("factorial", module.functions[0].name)
        assertEquals("main", module.functions[1].name)
        
        // Check function indices
        val factorialIndex = module.functions.indexOfFirst { it.name == "factorial" }
        val mainIndex = module.functions.indexOfFirst { it.name == "main" }
        assertNotEquals(-1, factorialIndex)
        assertNotEquals(-1, mainIndex)
    }
    
    @Test
    fun `generates bytecode for recursive function`() {
        val source = """
            func factorial(n: int): int {
                if (n <= 1) {
                    return 1;
                } else {
                    return n * factorial(n - 1);
                }
            }
            
            func main(): void {
                let x: int = factorial(5);
            }
        """.trimIndent()
        
        val module = compile(source)
        
        assertEquals(2, module.functions.size)
        val factorial = module.functions.find { it.name == "factorial" }
        assertNotNull(factorial)
        
        val factorialInstructions = factorial!!.instructions
        assertTrue(factorialInstructions.contains(Opcodes.CALL), "Should contain CALL for recursive call")
        assertTrue(factorialInstructions.contains(Opcodes.RETURN), "Should contain RETURN")
        assertTrue(factorialInstructions.contains(Opcodes.JUMP_IF_FALSE), "Should contain JUMP_IF_FALSE for if")
        assertTrue(factorialInstructions.contains(Opcodes.MUL_INT), "Should contain MUL_INT")
        assertTrue(module.intConstants.contains(1L), "Should have constant 1")
    }
    
    @Test
    fun `generates bytecode for array creation`() {
        val source = """
            func main(): void {
                let arr: int[] = int[10];
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        val instructions = main.instructions
        
        assertTrue(instructions.contains(Opcodes.NEW_ARRAY_INT), "Should contain NEW_ARRAY_INT")
        assertTrue(instructions.contains(Opcodes.PUSH_INT), "Should contain PUSH_INT for array size")
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL")
        assertTrue(module.intConstants.contains(10L), "Should have constant 10")
    }
    
    @Test
    fun `generates bytecode for array access`() {
        val source = """
            func main(): void {
                let arr: int[] = int[5];
                let x: int = arr[0];
                arr[1] = 42;
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        val instructions = main.instructions
        
        assertTrue(instructions.contains(Opcodes.NEW_ARRAY_INT), "Should contain NEW_ARRAY_INT")
        assertTrue(instructions.contains(Opcodes.ARRAY_LOAD), "Should contain ARRAY_LOAD")
        assertTrue(instructions.contains(Opcodes.ARRAY_STORE), "Should contain ARRAY_STORE")
        assertTrue(instructions.contains(Opcodes.PUSH_INT), "Should contain PUSH_INT for indices and values")
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL")
        assertTrue(module.intConstants.contains(5L), "Should have constant 5")
        assertTrue(module.intConstants.contains(0L), "Should have constant 0")
        assertTrue(module.intConstants.contains(42L), "Should have constant 42")
    }
    
    @Test
    fun `generates bytecode for float array`() {
        val source = """
            func main(): void {
                let arr: float[] = float[10];
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        val instructions = main.instructions
        
        assertTrue(instructions.contains(Opcodes.NEW_ARRAY_FLOAT), "Should contain NEW_ARRAY_FLOAT")
        assertTrue(instructions.contains(Opcodes.PUSH_INT), "Should contain PUSH_INT for array size")
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL")
        assertTrue(module.intConstants.contains(10L), "Should have constant 10")
    }
    
    @Test
    fun `generates bytecode for built-in print function`() {
        val source = """
            func main(): void {
                print(42);
                print(3.14);
                print(true);
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        val instructions = main.instructions
        
        assertTrue(instructions.contains(Opcodes.PRINT), "Should contain PRINT instruction")
        assertTrue(instructions.contains(Opcodes.PUSH_INT), "Should contain PUSH_INT for int")
        assertTrue(instructions.contains(Opcodes.PUSH_FLOAT), "Should contain PUSH_FLOAT for float")
        assertTrue(instructions.contains(Opcodes.PUSH_BOOL), "Should contain PUSH_BOOL for bool")
        assertTrue(module.intConstants.contains(42L), "Should have constant 42")
        assertTrue(module.floatConstants.contains(3.14), "Should have constant 3.14")
    }
    
    @Test
    fun `generates bytecode for built-in printArray function`() {
        val source = """
            func main(): void {
                let arr: int[] = int[5];
                printArray(arr);
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        val instructions = main.instructions
        
        assertTrue(instructions.contains(Opcodes.PRINT_ARRAY), "Should contain PRINT_ARRAY instruction")
        assertTrue(instructions.contains(Opcodes.NEW_ARRAY_INT), "Should contain NEW_ARRAY_INT")
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL")
        assertTrue(module.intConstants.contains(5L), "Should have constant 5")
    }
    
    @Test
    fun `deduplicates constants in pool`() {
        val source = """
            func main(): void {
                let x: int = 5;
                let y: int = 5;
                let z: int = 5;
            }
        """.trimIndent()
        
        val module = compile(source)
        
        // Constant 5 should appear only once in pool
        assertEquals(1, module.intConstants.count { it == 5L })
        assertTrue(module.intConstants.contains(5L))
    }
    
    @Test
    fun `generates correct function indices`() {
        val source = """
            func foo(): void { }
            func bar(): void { }
            func baz(): void { }
            
            func main(): void {
                foo();
                bar();
                baz();
            }
        """.trimIndent()
        
        val module = compile(source)
        
        assertEquals(4, module.functions.size)
        
        // All functions should be present
        val functionNames = module.functions.map { it.name }.toSet()
        assertTrue(functionNames.contains("foo"))
        assertTrue(functionNames.contains("bar"))
        assertTrue(functionNames.contains("baz"))
        assertTrue(functionNames.contains("main"))
    }
    
    @Test
    fun `generates bytecode for unary minus`() {
        val source = """
            func main(): void {
                let x: int = -5;
                let y: float = -3.14;
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        val instructions = main.instructions
        
        assertTrue(instructions.contains(Opcodes.NEG_INT), "Should contain NEG_INT for int")
        assertTrue(instructions.contains(Opcodes.NEG_FLOAT), "Should contain NEG_FLOAT for float")
        assertTrue(instructions.contains(Opcodes.PUSH_INT), "Should contain PUSH_INT")
        assertTrue(instructions.contains(Opcodes.PUSH_FLOAT), "Should contain PUSH_FLOAT")
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL")
        assertTrue(module.intConstants.contains(5L))
        assertTrue(module.floatConstants.contains(3.14))
    }
    
    @Test
    fun `generates bytecode for modulo operation`() {
        val source = """
            func main(): void {
                let x: int = 10 % 3;
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        val instructions = main.instructions
        
        assertTrue(instructions.contains(Opcodes.MOD_INT), "Should contain MOD_INT")
        assertTrue(instructions.contains(Opcodes.PUSH_INT), "Should contain PUSH_INT")
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL")
        assertTrue(module.intConstants.contains(10L), "Should have constant 10")
        assertTrue(module.intConstants.contains(3L), "Should have constant 3")
    }
    
    @Test
    fun `generates bytecode for return statement with value`() {
        val source = """
            func getValue(): int {
                return 42;
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val func = module.functions[0]
        val instructions = func.instructions
        
        assertTrue(instructions.contains(Opcodes.RETURN), "Should contain RETURN")
        assertTrue(instructions.contains(Opcodes.PUSH_INT), "Should contain PUSH_INT for return value")
        assertTrue(module.intConstants.contains(42L))
    }
    
    @Test
    fun `generates bytecode for return statement without value`() {
        val source = """
            func doNothing(): void {
                return;
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val func = module.functions[0]
        val instructions = func.instructions
        
        assertTrue(instructions.contains(Opcodes.RETURN_VOID), "Should contain RETURN_VOID")
    }
    
    @Test
    fun `generates bytecode for nested blocks`() {
        val source = """
            func main(): void {
                {
                    let x: int = 1;
                    {
                        let y: int = 2;
                    }
                }
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        val instructions = main.instructions
        
        assertTrue(instructions.contains(Opcodes.PUSH_INT), "Should contain PUSH_INT for literals")
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL")
        assertTrue(module.intConstants.contains(1L), "Should have constant 1")
        assertTrue(module.intConstants.contains(2L), "Should have constant 2")
        assertEquals(2, main.localsCount) // x and y
    }
    
    @Test
    fun `generates bytecode for assignment to variable`() {
        val source = """
            func main(): void {
                let x: int = 5;
                x = 10;
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        assertEquals(1, main.localsCount) // only x
        
        val instructions = main.instructions
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL for assignment")
        assertTrue(instructions.contains(Opcodes.PUSH_INT), "Should contain PUSH_INT for literals")
        assertTrue(module.intConstants.contains(5L), "Should have constant 5")
        assertTrue(module.intConstants.contains(10L), "Should have constant 10")
    }
    
    @Test
    fun `generates bytecode for assignment to array element`() {
        val source = """
            func main(): void {
                let arr: int[] = int[5];
                arr[0] = 42;
                arr[1] = 100;
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        val instructions = main.instructions
        
        assertTrue(instructions.contains(Opcodes.NEW_ARRAY_INT), "Should contain NEW_ARRAY_INT")
        assertTrue(instructions.contains(Opcodes.ARRAY_STORE), "Should contain ARRAY_STORE")
        assertTrue(instructions.contains(Opcodes.PUSH_INT), "Should contain PUSH_INT for indices and values")
        assertTrue(module.intConstants.contains(5L), "Should have constant 5")
        assertTrue(module.intConstants.contains(0L), "Should have constant 0")
        assertTrue(module.intConstants.contains(42L), "Should have constant 42")
        assertTrue(module.intConstants.contains(1L), "Should have constant 1")
        assertTrue(module.intConstants.contains(100L), "Should have constant 100")
    }
    
    @Test
    fun `generates correct entry point`() {
        val source = """
            func main(): void {
                let x: int = 1;
            }
        """.trimIndent()
        
        val module = compile(source)
        
        assertEquals("main", module.entryPoint)
    }
    
    @Test
    fun `generates bytecode for complex expression`() {
        val source = """
            func main(): void {
                let x: int = (5 + 3) * 2;
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        val instructions = main.instructions
        
        assertTrue(instructions.contains(Opcodes.ADD_INT), "Should contain ADD_INT")
        assertTrue(instructions.contains(Opcodes.MUL_INT), "Should contain MUL_INT")
        assertTrue(instructions.contains(Opcodes.PUSH_INT), "Should contain PUSH_INT")
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL")
        assertTrue(module.intConstants.contains(5L))
        assertTrue(module.intConstants.contains(3L))
        assertTrue(module.intConstants.contains(2L))
    }
    
    @Test
    fun `generates bytecode for for loop without initializer`() {
        val source = """
            func main(): void {
                let i: int = 0;
                for (; i < 10; i = i + 1) {
                    let x: int = i;
                }
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        val instructions = main.instructions
        
        assertTrue(instructions.contains(Opcodes.JUMP), "Should contain JUMP for loop")
        assertTrue(instructions.contains(Opcodes.JUMP_IF_FALSE), "Should contain JUMP_IF_FALSE for condition")
        assertTrue(instructions.contains(Opcodes.LOAD_LOCAL), "Should contain LOAD_LOCAL for loop variable")
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL")
        assertTrue(module.intConstants.contains(0L), "Should have constant 0")
        assertTrue(module.intConstants.contains(10L), "Should have constant 10")
        assertTrue(module.intConstants.contains(1L), "Should have constant 1")
    }
    
    @Test
    fun `generates bytecode for for loop without condition`() {
        val source = """
            func main(): void {
                for (let i: int = 0; ; i = i + 1) {
                    let x: int = i;
                }
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        val instructions = main.instructions
        
        assertTrue(instructions.contains(Opcodes.JUMP), "Should contain JUMP for infinite loop")
        assertTrue(instructions.contains(Opcodes.LOAD_LOCAL), "Should contain LOAD_LOCAL for loop variable")
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL")
        assertTrue(module.intConstants.contains(0L), "Should have constant 0")
        assertTrue(module.intConstants.contains(1L), "Should have constant 1")
    }
    
    @Test
    fun `generates bytecode for for loop without increment`() {
        val source = """
            func main(): void {
                for (let i: int = 0; i < 10; ) {
                    let x: int = i;
                }
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        val instructions = main.instructions
        
        assertTrue(instructions.contains(Opcodes.JUMP), "Should contain JUMP for loop")
        assertTrue(instructions.contains(Opcodes.JUMP_IF_FALSE), "Should contain JUMP_IF_FALSE for condition")
        assertTrue(instructions.contains(Opcodes.LOAD_LOCAL), "Should contain LOAD_LOCAL for loop variable")
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL")
        assertTrue(instructions.contains(Opcodes.LT_INT), "Should contain LT_INT for condition")
        assertTrue(module.intConstants.contains(0L), "Should have constant 0")
        assertTrue(module.intConstants.contains(10L), "Should have constant 10")
    }
    
    @Test
    fun `generates bytecode for multiple functions`() {
        val source = """
            func add(a: int, b: int): int {
                return a + b;
            }
            
            func multiply(a: int, b: int): int {
                return a * b;
            }
            
            func main(): void {
                let x: int = add(1, 2);
                let y: int = multiply(3, 4);
            }
        """.trimIndent()
        
        val module = compile(source)
        
        assertEquals(3, module.functions.size)
        
        val add = module.functions.find { it.name == "add" }
        assertNotNull(add)
        assertEquals(2, add!!.parameters.size)
        assertEquals(2, add.localsCount) // parameters only
        
        val multiply = module.functions.find { it.name == "multiply" }
        assertNotNull(multiply)
        assertEquals(2, multiply!!.parameters.size)
    }
    
    @Test
    fun `generates bytecode with correct parameter info`() {
        val source = """
            func test(x: int, y: float, z: bool): void {
                let a: int = x;
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val func = module.functions[0]
        assertEquals(3, func.parameters.size)
        assertEquals("x", func.parameters[0].name)
        assertEquals("y", func.parameters[1].name)
        assertEquals("z", func.parameters[2].name)
        assertEquals(4, func.localsCount) // 3 parameters + 1 local variable
    }
    
    @Test
    fun `generates PUSH_INT instruction for int literal`() {
        val source = """
            func main(): void {
                let x: int = 42;
            }
        """.trimIndent()
        
        val module = compile(source)
        val main = module.functions[0]
        val instructions = main.instructions
        
        // Should contain PUSH_INT (0x01)
        assertTrue(instructions.contains(Opcodes.PUSH_INT), "Should contain PUSH_INT instruction")
    }
    
    @Test
    fun `generates STORE_LOCAL instruction for variable declaration`() {
        val source = """
            func main(): void {
                let x: int = 5;
            }
        """.trimIndent()
        
        val module = compile(source)
        val main = module.functions[0]
        val instructions = main.instructions
        
        // Should contain STORE_LOCAL (0x11)
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL instruction")
    }
    
    @Test
    fun `generates ADD_INT instruction for addition`() {
        val source = """
            func main(): void {
                let x: int = 5 + 3;
            }
        """.trimIndent()
        
        val module = compile(source)
        val main = module.functions[0]
        val instructions = main.instructions
        
        // Should contain ADD_INT (0x20)
        assertTrue(instructions.contains(Opcodes.ADD_INT), "Should contain ADD_INT instruction")
    }
    
    @Test
    fun `generates ADD_FLOAT instruction for float addition`() {
        val source = """
            func main(): void {
                let x: float = 3.14 + 2.5;
            }
        """.trimIndent()
        
        val module = compile(source)
        val main = module.functions[0]
        val instructions = main.instructions
        
        // Should contain ADD_FLOAT (0x30)
        assertTrue(instructions.contains(Opcodes.ADD_FLOAT), "Should contain ADD_FLOAT instruction")
    }
    
    @Test
    fun `generates LOAD_LOCAL instruction for variable access`() {
        val source = """
            func main(): void {
                let x: int = 5;
                let y: int = x;
            }
        """.trimIndent()
        
        val module = compile(source)
        val main = module.functions[0]
        val instructions = main.instructions
        
        // Should contain LOAD_LOCAL (0x10)
        assertTrue(instructions.contains(Opcodes.LOAD_LOCAL), "Should contain LOAD_LOCAL instruction")
    }
    
    @Test
    fun `generates JUMP_IF_FALSE instruction for if statement`() {
        val source = """
            func main(): void {
                if (true) {
                    let x: int = 1;
                }
            }
        """.trimIndent()
        
        val module = compile(source)
        val main = module.functions[0]
        val instructions = main.instructions
        
        // Should contain JUMP_IF_FALSE (0x71)
        assertTrue(instructions.contains(Opcodes.JUMP_IF_FALSE), "Should contain JUMP_IF_FALSE instruction")
    }
    
    @Test
    fun `generates JUMP instruction for loops`() {
        val source = """
            func main(): void {
                for (let i: int = 0; i < 10; i = i + 1) {
                    let x: int = i;
                }
            }
        """.trimIndent()
        
        val module = compile(source)
        val main = module.functions[0]
        val instructions = main.instructions
        
        // Should contain JUMP (0x70)
        assertTrue(instructions.contains(Opcodes.JUMP), "Should contain JUMP instruction")
    }
    
    @Test
    fun `generates CALL instruction for function call`() {
        val source = """
            func foo(): int {
                return 5;
            }
            
            func main(): void {
                let x: int = foo();
            }
        """.trimIndent()
        
        val module = compile(source)
        val main = module.functions.find { it.name == "main" }
        assertNotNull(main)
        val instructions = main!!.instructions
        
        // Should contain CALL (0x80)
        assertTrue(instructions.contains(Opcodes.CALL), "Should contain CALL instruction")
    }
    
    @Test
    fun `generates RETURN instruction for function with return value`() {
        val source = """
            func getValue(): int {
                return 42;
            }
        """.trimIndent()
        
        val module = compile(source)
        val func = module.functions[0]
        val instructions = func.instructions
        
        // Should contain RETURN (0x81)
        assertTrue(instructions.contains(Opcodes.RETURN), "Should contain RETURN instruction")
    }
    
    @Test
    fun `generates RETURN_VOID instruction for void function`() {
        val source = """
            func doNothing(): void {
                return;
            }
        """.trimIndent()
        
        val module = compile(source)
        val func = module.functions[0]
        val instructions = func.instructions
        
        // Should contain RETURN_VOID (0x82)
        assertTrue(instructions.contains(Opcodes.RETURN_VOID), "Should contain RETURN_VOID instruction")
    }
    
    @Test
    fun `generates NEW_ARRAY_INT instruction for int array`() {
        val source = """
            func main(): void {
                let arr: int[] = int[10];
            }
        """.trimIndent()
        
        val module = compile(source)
        val main = module.functions[0]
        val instructions = main.instructions
        
        // Should contain NEW_ARRAY_INT (0x90)
        assertTrue(instructions.contains(Opcodes.NEW_ARRAY_INT), "Should contain NEW_ARRAY_INT instruction")
    }
    
    @Test
    fun `generates ARRAY_LOAD instruction for array access`() {
        val source = """
            func main(): void {
                let arr: int[] = int[5];
                let x: int = arr[0];
            }
        """.trimIndent()
        
        val module = compile(source)
        val main = module.functions[0]
        val instructions = main.instructions
        
        // Should contain ARRAY_LOAD (0x92)
        assertTrue(instructions.contains(Opcodes.ARRAY_LOAD), "Should contain ARRAY_LOAD instruction")
    }
    
    @Test
    fun `generates ARRAY_STORE instruction for array assignment`() {
        val source = """
            func main(): void {
                let arr: int[] = int[5];
                arr[0] = 42;
            }
        """.trimIndent()
        
        val module = compile(source)
        val main = module.functions[0]
        val instructions = main.instructions
        
        // Should contain ARRAY_STORE (0x93)
        assertTrue(instructions.contains(Opcodes.ARRAY_STORE), "Should contain ARRAY_STORE instruction")
    }
    
    @Test
    fun `generates PRINT instruction for built-in print`() {
        val source = """
            func main(): void {
                print(42);
            }
        """.trimIndent()
        
        val module = compile(source)
        val main = module.functions[0]
        val instructions = main.instructions
        
        // Should contain PRINT (0xA0)
        assertTrue(instructions.contains(Opcodes.PRINT), "Should contain PRINT instruction")
    }
    
    @Test
    fun `generates PRINT_ARRAY instruction for built-in printArray`() {
        val source = """
            func main(): void {
                let arr: int[] = int[5];
                printArray(arr);
            }
        """.trimIndent()
        
        val module = compile(source)
        val main = module.functions[0]
        val instructions = main.instructions
        
        // Should contain PRINT_ARRAY (0xA1)
        assertTrue(instructions.contains(Opcodes.PRINT_ARRAY), "Should contain PRINT_ARRAY instruction")
    }
    
    @Test
    fun `generates correct instruction sequence for simple expression`() {
        val source = """
            func main(): void {
                let x: int = 5 + 3;
            }
        """.trimIndent()
        
        val module = compile(source)
        val main = module.functions[0]
        val instructions = main.instructions
        
        // Expected sequence: PUSH_INT, PUSH_INT, ADD_INT, STORE_LOCAL
        // Check that all required instructions are present
        assertTrue(instructions.contains(Opcodes.PUSH_INT))
        assertTrue(instructions.contains(Opcodes.ADD_INT))
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL))
    }
    
    @Test
    fun `generates instructions with correct 4-byte format`() {
        val source = """
            func main(): void {
                let x: int = 5;
            }
        """.trimIndent()
        
        val module = compile(source)
        val main = module.functions[0]
        val instructions = main.instructions
        
        // Each instruction should be 4 bytes
        // Total size should be divisible by 4
        assertEquals(0, instructions.size % 4, "Instructions should be in 4-byte format")
    }
    
    @Test
    fun `handles empty function body`() {
        val source = """
            func main(): void {
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        assertEquals("main", main.name)
        
        val instructions = main.instructions
        // Empty void function should have RETURN_VOID
        assertTrue(instructions.contains(Opcodes.RETURN_VOID), "Should contain RETURN_VOID for empty void function")
    }
    
    @Test
    fun `generates bytecode for all comparison operators`() {
        val source = """
            func main(): void {
                let a: bool = 5 < 10;
                let b: bool = 5 > 3;
                let c: bool = 5 <= 5;
                let d: bool = 5 >= 5;
                let e: bool = 5 == 5;
                let f: bool = 5 != 3;
            }
        """.trimIndent()
        
        val module = compile(source)
        
        val main = module.functions[0]
        assertEquals(6, main.localsCount)
        
        val instructions = main.instructions
        assertTrue(instructions.contains(Opcodes.LT_INT), "Should contain LT_INT")
        assertTrue(instructions.contains(Opcodes.GT_INT), "Should contain GT_INT")
        assertTrue(instructions.contains(Opcodes.LE_INT), "Should contain LE_INT")
        assertTrue(instructions.contains(Opcodes.GE_INT), "Should contain GE_INT")
        assertTrue(instructions.contains(Opcodes.EQ_INT), "Should contain EQ_INT")
        assertTrue(instructions.contains(Opcodes.NE_INT), "Should contain NE_INT")
        assertTrue(instructions.contains(Opcodes.PUSH_INT), "Should contain PUSH_INT")
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL")
    }
    
    @Test
    fun `generates bytecode for nested function calls`() {
        val source = """
            func add(a: int, b: int): int {
                return a + b;
            }
            
            func main(): void {
                let x: int = add(add(1, 2), add(3, 4));
            }
        """.trimIndent()
        
        val module = compile(source)
        
        assertEquals(2, module.functions.size)
        val main = module.functions.find { it.name == "main" }
        assertNotNull(main)
        
        val mainInstructions = main!!.instructions
        assertTrue(mainInstructions.contains(Opcodes.CALL), "Should contain CALL for nested function calls")
        assertTrue(mainInstructions.contains(Opcodes.PUSH_INT), "Should contain PUSH_INT for arguments")
        assertTrue(mainInstructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL")
        
        val add = module.functions.find { it.name == "add" }
        assertNotNull(add)
        val addInstructions = add!!.instructions
        assertTrue(addInstructions.contains(Opcodes.ADD_INT), "Should contain ADD_INT")
        assertTrue(addInstructions.contains(Opcodes.RETURN), "Should contain RETURN")
        
        assertTrue(module.intConstants.contains(1L), "Should have constant 1")
        assertTrue(module.intConstants.contains(2L), "Should have constant 2")
        assertTrue(module.intConstants.contains(3L), "Should have constant 3")
        assertTrue(module.intConstants.contains(4L), "Should have constant 4")
    }
    
    private fun loadResource(name: String): String {
        return javaClass.classLoader.getResource(name)?.readText()
            ?: throw IllegalStateException("Resource not found: $name")
    }
    
    @Test
    fun `generates bytecode for simple example`() {
        val source = loadResource("simple.lang")
        val module = compile(source)
        
        assertEquals(1, module.functions.size)
        val main = module.functions[0]
        assertEquals("main", main.name)
        
        val instructions = main.instructions
        // Should have variable declarations
        assertTrue(instructions.contains(Opcodes.PUSH_INT), "Should contain PUSH_INT")
        assertTrue(instructions.contains(Opcodes.STORE_LOCAL), "Should contain STORE_LOCAL")
        // Should have arithmetic operations
        assertTrue(instructions.contains(Opcodes.ADD_INT), "Should contain ADD_INT for sum")
        assertTrue(instructions.contains(Opcodes.MUL_INT), "Should contain MUL_INT for product")
        // Should have comparison
        assertTrue(instructions.contains(Opcodes.GT_INT), "Should contain GT_INT for comparison")
        // Should have print calls
        assertTrue(instructions.contains(Opcodes.PRINT), "Should contain PRINT for output")
        
        // Check constants
        assertTrue(module.intConstants.contains(42L), "Should have constant 42")
        assertTrue(module.intConstants.contains(10L), "Should have constant 10")
        
        // Check locals count (x, y, sum, product, flag)
        assertEquals(5, main.localsCount)
        
        // Should end with RETURN_VOID
        assertTrue(instructions.contains(Opcodes.RETURN_VOID), "Should contain RETURN_VOID")
    }
    
    @Test
    fun `generates bytecode for factorial example`() {
        val source = loadResource("factorial.lang")
        val module = compile(source)
        
        assertEquals(2, module.functions.size)
        
        // Find factorial function
        val factorial = module.functions.find { it.name == "factorial" }
            ?: fail("Should have factorial function")
        val main = module.functions.find { it.name == "main" }
            ?: fail("Should have main function")
        
        // Check factorial function
        val factInstructions = factorial.instructions
        assertTrue(factInstructions.contains(Opcodes.RETURN), "Should contain RETURN with value")
        assertTrue(factInstructions.contains(Opcodes.CALL), "Should contain CALL for recursion")
        assertTrue(factInstructions.contains(Opcodes.LE_INT), "Should contain LE_INT for condition")
        assertTrue(factInstructions.contains(Opcodes.MUL_INT), "Should contain MUL_INT for multiplication")
        assertTrue(factInstructions.contains(Opcodes.SUB_INT), "Should contain SUB_INT for n - 1")
        
        // Check constants
        assertTrue(module.intConstants.contains(1L), "Should have constant 1")
        assertTrue(module.intConstants.contains(20L), "Should have constant 20")
        
        // Check main function
        val mainInstructions = main.instructions
        assertTrue(mainInstructions.contains(Opcodes.CALL), "Should contain CALL to factorial")
        assertTrue(mainInstructions.contains(Opcodes.STORE_LOCAL), "Should store result")
        assertTrue(mainInstructions.contains(Opcodes.PRINT), "Should contain PRINT for output")
    }
    
    @Test
    fun `generates bytecode for prime example`() {
        val source = loadResource("prime.lang")
        val module = compile(source)
        
        assertEquals(2, module.functions.size)
        
        val sieve = module.functions.find { it.name == "sieve" }
            ?: fail("Should have sieve function")
        val main = module.functions.find { it.name == "main" }
            ?: fail("Should have main function")
        
        val instructions = sieve.instructions
        
        // Should have array operations
        assertTrue(instructions.contains(Opcodes.NEW_ARRAY_INT), "Should contain NEW_ARRAY_INT for int arrays")
        assertTrue(instructions.contains(Opcodes.NEW_ARRAY_BOOL), "Should contain NEW_ARRAY_BOOL for bool arrays")
        assertTrue(instructions.contains(Opcodes.ARRAY_STORE), "Should contain ARRAY_STORE")
        assertTrue(instructions.contains(Opcodes.ARRAY_LOAD), "Should contain ARRAY_LOAD")
        
        // Should have loop operations
        assertTrue(instructions.contains(Opcodes.JUMP), "Should contain JUMP for loops")
        assertTrue(instructions.contains(Opcodes.JUMP_IF_FALSE), "Should contain JUMP_IF_FALSE")
        
        // Should have arithmetic operations
        assertTrue(instructions.contains(Opcodes.ADD_INT), "Should contain ADD_INT")
        assertTrue(instructions.contains(Opcodes.MUL_INT), "Should contain MUL_INT")
        assertTrue(instructions.contains(Opcodes.LE_INT), "Should contain LE_INT for comparisons")
        
        // Should have return
        assertTrue(instructions.contains(Opcodes.RETURN), "Should contain RETURN with array")
        
        // Check main function
        val mainInstructions = main.instructions
        assertTrue(mainInstructions.contains(Opcodes.CALL), "Should contain CALL to sieve")
        assertTrue(mainInstructions.contains(Opcodes.STORE_LOCAL), "Should store result")
        assertTrue(mainInstructions.contains(Opcodes.PRINT_ARRAY), "Should contain PRINT_ARRAY for output")
        
        // Check constants
        assertTrue(module.intConstants.contains(0L), "Should have constant 0")
        assertTrue(module.intConstants.contains(1L), "Should have constant 1")
        assertTrue(module.intConstants.contains(2L), "Should have constant 2")
        assertTrue(module.intConstants.contains(30L), "Should have constant 30")
    }
    
    @Test
    fun `generates bytecode for merge_sort example`() {
        val source = loadResource("merge_sort.lang")
        val module = compile(source)
        
        assertEquals(3, module.functions.size)
        
        val mergeSort = module.functions.find { it.name == "mergeSort" }
            ?: fail("Should have mergeSort function")
        val merge = module.functions.find { it.name == "merge" }
            ?: fail("Should have merge function")
        val main = module.functions.find { it.name == "main" }
            ?: fail("Should have main function")
        
        // Check mergeSort function
        val mergeSortInstructions = mergeSort.instructions
        assertTrue(mergeSortInstructions.contains(Opcodes.CALL), "Should contain CALL for recursion")
        assertTrue(mergeSortInstructions.contains(Opcodes.NEW_ARRAY_INT), "Should contain NEW_ARRAY_INT")
        assertTrue(mergeSortInstructions.contains(Opcodes.ARRAY_STORE), "Should contain ARRAY_STORE")
        assertTrue(mergeSortInstructions.contains(Opcodes.ARRAY_LOAD), "Should contain ARRAY_LOAD")
        assertTrue(mergeSortInstructions.contains(Opcodes.RETURN), "Should contain RETURN")
        assertTrue(mergeSortInstructions.contains(Opcodes.DIV_INT), "Should contain DIV_INT for mid calculation")
        assertTrue(mergeSortInstructions.contains(Opcodes.SUB_INT), "Should contain SUB_INT for size - mid")
        assertTrue(mergeSortInstructions.contains(Opcodes.ADD_INT), "Should contain ADD_INT for mid + i")
        
        // Check merge function
        val mergeInstructions = merge.instructions
        assertTrue(mergeInstructions.contains(Opcodes.NEW_ARRAY_INT), "Should contain NEW_ARRAY_INT")
        assertTrue(mergeInstructions.contains(Opcodes.ARRAY_STORE), "Should contain ARRAY_STORE")
        assertTrue(mergeInstructions.contains(Opcodes.ARRAY_LOAD), "Should contain ARRAY_LOAD")
        assertTrue(mergeInstructions.contains(Opcodes.LE_INT), "Should contain LE_INT for comparison")
        assertTrue(mergeInstructions.contains(Opcodes.RETURN), "Should contain RETURN")
        
        // Check main function
        val mainInstructions = main.instructions
        assertTrue(mainInstructions.contains(Opcodes.NEW_ARRAY_INT), "Should contain NEW_ARRAY_INT for array initialization")
        assertTrue(mainInstructions.contains(Opcodes.ARRAY_STORE), "Should contain ARRAY_STORE for array initialization")
        assertTrue(mainInstructions.contains(Opcodes.CALL), "Should contain CALL to mergeSort")
        assertTrue(mainInstructions.contains(Opcodes.STORE_LOCAL), "Should store result")
        assertTrue(mainInstructions.contains(Opcodes.PRINT_ARRAY), "Should contain PRINT_ARRAY for output")
        assertTrue(mainInstructions.contains(Opcodes.JUMP), "Should contain JUMP for loop")
        assertTrue(mainInstructions.contains(Opcodes.JUMP_IF_FALSE), "Should contain JUMP_IF_FALSE for loop condition")
        assertTrue(mainInstructions.contains(Opcodes.SUB_INT), "Should contain SUB_INT for 99999 - i")
        
        // Check constants
        assertTrue(module.intConstants.contains(0L), "Should have constant 0")
        assertTrue(module.intConstants.contains(1L), "Should have constant 1")
        assertTrue(module.intConstants.contains(2L), "Should have constant 2")
        assertTrue(module.intConstants.contains(10000L), "Should have constant 10000")
        assertTrue(module.intConstants.contains(99999L), "Should have constant 99999")
    }
}

