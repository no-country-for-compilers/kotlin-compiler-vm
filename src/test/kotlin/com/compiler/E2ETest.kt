import com.compiler.lexer.Lexer
import com.compiler.parser.Parser
import com.compiler.parser.ast.optimizations.ConstantFolder
import com.compiler.parser.ast.optimizations.DeadCodeEliminator
import com.compiler.semantic.DefaultSemanticAnalyzer
import com.compiler.bytecode.BytecodeGenerator
import com.compiler.vm.VirtualMachine
import com.compiler.vm.VMResult
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
        var program = parser.parse()
        assertTrue(program.statements.isNotEmpty(), "Program should contain statements")

        program = ConstantFolder.apply(program)
        program = DeadCodeEliminator.apply(program)
        
        val analyzer = DefaultSemanticAnalyzer()
        val semanticResult = analyzer.analyze(program)
        assertNull(semanticResult.error, "Semantic analysis should succeed: ${semanticResult.error?.message}")
        
        val generator = BytecodeGenerator(program, semanticResult.globalScope)
        val module = generator.generate()
        
        assertNotNull(module, "Should generate bytecode module")
        
        return module
    }

    @Test
    fun `factorial program - full compilation pipeline`() {
        val module = compileFile("src/test/resources/factorial.lang")
        
        // Execute bytecode
        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result, "VM execution should succeed")
    }

    @Test
    fun `merge sort program - full compilation pipeline`() {
        val module = compileFile("src/test/resources/merge_sort.lang")
        
        // Execute bytecode
        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result, "VM execution should succeed")
    }

    @Test
    fun `sieve program - full compilation pipeline`() {
        val module = compileFile("src/test/resources/prime.lang")
        
        // Execute bytecode
        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result, "VM execution should succeed")
    }

    @Test
    fun `arithmetic program - full compilation pipeline`() {
        val module = compileFile("src/test/resources/simple.lang")
        
        // Execute bytecode
        val vm = VirtualMachine(module)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result, "VM execution should succeed")
    }
}
