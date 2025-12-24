import com.compiler.lexer.Lexer
import com.compiler.parser.Parser
import com.compiler.parser.ast.optimizations.ConstantFolder
import com.compiler.parser.ast.optimizations.DeadCodeEliminator
import com.compiler.semantic.DefaultSemanticAnalyzer
import com.compiler.bytecode.BytecodeGenerator
import com.compiler.vm.VirtualMachine
import com.compiler.vm.VMResult
import com.compiler.vm.jit.BytecodeOptimizerJIT
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
        assertNull(
                semanticResult.error,
                "Semantic analysis should succeed: ${semanticResult.error?.message}"
        )

        val generator = BytecodeGenerator(program, semanticResult.globalScope)
        val module = generator.generate()

        assertNotNull(module, "Should generate bytecode module")

        return module
    }

    @Test
    fun `factorial program - full compilation pipeline`() {
        val module = compileFile("src/test/resources/factorial.lang")
        val vm = VirtualMachine(module, null)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result, "VM execution should succeed")
    }

    @Test
    fun `merge sort program - full compilation pipeline`() {
        val module = compileFile("src/test/resources/merge_sort.lang")
        val vm = VirtualMachine(module, null)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result, "VM execution should succeed")
    }

    @Test
    fun `sieve program - full compilation pipeline`() {
        val module = compileFile("src/test/resources/prime.lang")
        val vm = VirtualMachine(module, null)
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result, "VM execution should succeed")
    }

    @Test
    fun `arithmetic program - full compilation pipeline`() {
        val module = compileFile("src/test/resources/simple.lang")
        val vm = VirtualMachine(module, BytecodeOptimizerJIT(module))
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result, "VM execution should succeed")
    }

    @Test
    fun `merge sort performance comparison - with and without JIT optimization`() {
        val module = compileFile("src/test/resources/merge_sort.lang")
        val iterations = 3
        
        // Warm up JVM
        VirtualMachine(module, null).execute()
        BytecodeOptimizerJIT(module, threshold = 10).use { jit ->
            VirtualMachine(module, jit).execute()
        }
        
        // Measure execution time without JIT
        val timesWithoutJIT = mutableListOf<Long>()
        for (i in 1..iterations) {
            val startTime = System.nanoTime()
            val vm = VirtualMachine(module, null)
            val result = vm.execute()
            val endTime = System.nanoTime()
            assertEquals(VMResult.SUCCESS, result, "VM execution should succeed")
            timesWithoutJIT.add(endTime - startTime)
        }
        
        // Measure execution time with JIT
        val timesWithJIT = mutableListOf<Long>()
        BytecodeOptimizerJIT(module, threshold = 10).use { jit ->
            for (i in 1..iterations) {
                val startTime = System.nanoTime()
                val vm = VirtualMachine(module, jit)
                val result = vm.execute()
                val endTime = System.nanoTime()
                assertEquals(VMResult.SUCCESS, result, "VM execution should succeed")
                timesWithJIT.add(endTime - startTime)
            }
            
        }
        
        // Calculate averages
        val avgWithoutJIT = timesWithoutJIT.average()
        val avgWithJIT = timesWithJIT.average()
        val speedup = avgWithoutJIT / avgWithJIT
        
        // Print results
        println("\n=== Performance Comparison (averaged over $iterations runs) ===")
        println("Without JIT: ${String.format("%.2f", avgWithoutJIT / 1_000_000.0)} ms")
        println("With JIT:   ${String.format("%.2f", avgWithJIT / 1_000_000.0)} ms")
        println("Speedup:    ${String.format("%.2f", speedup)}x")
        println()
        
        // Test should pass regardless of performance, but we can assert that JIT doesn't break execution
        assertTrue(avgWithJIT > 0, "JIT execution time should be positive")
        assertTrue(avgWithoutJIT > 0, "Non-JIT execution time should be positive")
    }

    @Test
    fun `heap cleanup test - verify heap is cleared after program completion`() {
        val module = compileFile("src/test/resources/heap_cleanup_test.lang")
        val vm = VirtualMachine(module, null)
        
        // Execute the program
        val result = vm.execute()
        assertEquals(VMResult.SUCCESS, result, "VM execution should succeed")
        
        // After execution, heap should be cleared (all objects freed)
        val heapObjectCount = vm.getHeapObjectCount()
        assertEquals(0, heapObjectCount, "Heap should be empty after program completion")
    }
}
