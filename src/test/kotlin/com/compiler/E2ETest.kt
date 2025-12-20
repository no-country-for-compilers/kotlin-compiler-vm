import com.compiler.lexer.Lexer
import com.compiler.parser.Parser
import com.compiler.semantic.DefaultSemanticAnalyzer
import com.compiler.bytecode.BytecodeGenerator
import com.compiler.bytecode.Opcodes
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.*
import org.junit.jupiter.api.Test

class E2ETest {

    private fun compileFile(path: String): com.compiler.bytecode.BytecodeModule {
        val source = Files.readString(Paths.get(path))
        
        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        assertTrue(tokens.isNotEmpty(), "Should produce tokens")
        
        val parser = Parser(tokens)
        val program = parser.parse()
        assertTrue(program.statements.isNotEmpty(), "Program should contain statements")
        
        val analyzer = DefaultSemanticAnalyzer()
        val semanticResult = analyzer.analyze(program)
        assertNull(semanticResult.error, "Semantic analysis should succeed: ${semanticResult.error?.message}")
        
        val generator = BytecodeGenerator(program, semanticResult.globalScope)
        val module = generator.generate()
        
        assertNotNull(module, "Should generate bytecode module")
        assertTrue(module.functions.isNotEmpty(), "Should have at least one function")
        
        val mainFunction = module.functions.find { it.name == "main" }
        assertNotNull(mainFunction, "Should have main function")
        assertEquals("main", module.entryPoint, "Entry point should be 'main'")
        
        return module
    }

    @Test
    fun `factorial program - full compilation pipeline`() {
        val module = compileFile("src/test/resources/factorial.lang")
        
        assertEquals(2, module.functions.size, "Should have factorial and main functions")
        
        val factorial = module.functions.find { it.name == "factorial" }
        assertNotNull(factorial, "Should have factorial function")
        assertEquals(1, factorial!!.parameters.size, "Factorial should have 1 parameter")
        assertTrue(factorial.instructions.isNotEmpty(), "Factorial should have instructions")
        
        val main = module.functions.find { it.name == "main" }
        assertNotNull(main, "Should have main function")
        assertTrue(main!!.instructions.isNotEmpty(), "Main should have instructions")
        
        val mainInstructions = main.instructions
        assertTrue(mainInstructions.contains(Opcodes.CALL), "Main should call factorial")
        assertTrue(mainInstructions.contains(Opcodes.PRINT), "Main should print result")
        
        assertTrue(module.intConstants.contains(20L), "Should have constant 20")
        assertTrue(module.intConstants.contains(1L), "Should have constant 1")
    }

    @Test
    fun `merge sort program - full compilation pipeline`() {
        val module = compileFile("src/test/resources/merge_sort.lang")
        
        assertEquals(3, module.functions.size, "Should have mergeSort, merge and main functions")
        
        val mergeSort = module.functions.find { it.name == "mergeSort" }
        assertNotNull(mergeSort, "Should have mergeSort function")
        assertTrue(mergeSort!!.instructions.isNotEmpty(), "mergeSort should have instructions")
        
        val merge = module.functions.find { it.name == "merge" }
        assertNotNull(merge, "Should have merge function")
        assertTrue(merge!!.instructions.isNotEmpty(), "merge should have instructions")
        
        val main = module.functions.find { it.name == "main" }
        assertNotNull(main, "Should have main function")
        assertTrue(main!!.instructions.isNotEmpty(), "Main should have instructions")
        
        val mainInstructions = main.instructions
        assertTrue(mainInstructions.contains(Opcodes.NEW_ARRAY_INT), "Main should create array")
        assertTrue(mainInstructions.contains(Opcodes.ARRAY_STORE), "Main should store array elements")
        assertTrue(mainInstructions.contains(Opcodes.CALL), "Main should call mergeSort")
        assertTrue(mainInstructions.contains(Opcodes.PRINT_ARRAY), "Main should print array")
        assertTrue(mainInstructions.contains(Opcodes.JUMP), "Main should have loop")
        
        assertTrue(module.intConstants.contains(10000L), "Should have constant 10000")
        assertTrue(module.intConstants.contains(99999L), "Should have constant 99999")
    }

    @Test
    fun `sieve program - full compilation pipeline`() {
        val module = compileFile("src/test/resources/prime.lang")
        
        assertEquals(2, module.functions.size, "Should have sieve and main functions")
        
        val sieve = module.functions.find { it.name == "sieve" }
        assertNotNull(sieve, "Should have sieve function")
        assertTrue(sieve!!.instructions.isNotEmpty(), "sieve should have instructions")
        
        val main = module.functions.find { it.name == "main" }
        assertNotNull(main, "Should have main function")
        assertTrue(main!!.instructions.isNotEmpty(), "Main should have instructions")
        
        val sieveInstructions = sieve.instructions
        assertTrue(sieveInstructions.contains(Opcodes.NEW_ARRAY_INT), "sieve should create int arrays")
        assertTrue(sieveInstructions.contains(Opcodes.NEW_ARRAY_BOOL), "sieve should create bool arrays")
        assertTrue(sieveInstructions.contains(Opcodes.ARRAY_STORE), "sieve should store array elements")
        assertTrue(sieveInstructions.contains(Opcodes.ARRAY_LOAD), "sieve should load array elements")
        assertTrue(sieveInstructions.contains(Opcodes.RETURN), "sieve should return array")
        
        val mainInstructions = main.instructions
        assertTrue(mainInstructions.contains(Opcodes.CALL), "Main should call sieve")
        assertTrue(mainInstructions.contains(Opcodes.PRINT_ARRAY), "Main should print array")
        
        assertTrue(module.intConstants.contains(30L), "Should have constant 30")
    }

    @Test
    fun `arithmetic program - full compilation pipeline`() {
        val module = compileFile("src/test/resources/simple.lang")
        
        assertEquals(1, module.functions.size, "Should have main function")
        
        val main = module.functions.find { it.name == "main" }
        assertNotNull(main, "Should have main function")
        assertTrue(main!!.instructions.isNotEmpty(), "Main should have instructions")
        
        val mainInstructions = main.instructions
        assertTrue(mainInstructions.contains(Opcodes.PUSH_INT), "Main should push integers")
        assertTrue(mainInstructions.contains(Opcodes.STORE_LOCAL), "Main should store local variables")
        assertTrue(mainInstructions.contains(Opcodes.ADD_INT), "Main should add integers")
        assertTrue(mainInstructions.contains(Opcodes.MUL_INT), "Main should multiply integers")
        assertTrue(mainInstructions.contains(Opcodes.GT_INT), "Main should compare integers")
        assertTrue(mainInstructions.contains(Opcodes.PRINT), "Main should print values")
        assertTrue(mainInstructions.contains(Opcodes.RETURN_VOID), "Main should return void")
        
        assertTrue(module.intConstants.contains(42L), "Should have constant 42")
        assertTrue(module.intConstants.contains(10L), "Should have constant 10")
        
        assertEquals(5, main.localsCount, "Should have 5 local variables (x, y, sum, product, flag)")
    }
}
