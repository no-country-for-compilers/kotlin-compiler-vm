package com.compiler.semantic

import com.compiler.lexer.Lexer
import com.compiler.parser.Parser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SemanticAnalyzerTest {
    private fun analyze(source: String): AnalysisResult {
        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        val parser = Parser(tokens)
        val program = parser.parse()

        val analyzer: SemanticAnalyzer = DefaultSemanticAnalyzer()
        return analyzer.analyze(program)
    }

    @Test
    fun `simple well-typed program has no semantic errors`() {
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

        val result = analyze(source)
        assertTrue(result.errors.isEmpty(), "Expected no semantic errors, got: ${result.errors}")
    }

    @Test
    fun `undefined variable produces error`() {
        val source = """
            func main(): void {
                let x: int = y + 1;
            }
        """.trimIndent()

        val result = analyze(source)
        assertTrue(
            result.errors.any { it.message.contains("Undefined variable 'y'") },
            "Expected undefined variable error, got: ${result.errors}"
        )
    }

    @Test
    fun `type mismatch in var declaration produces error`() {
        val source = """
            func main(): void {
                let x: int = 3.14;
            }
        """.trimIndent()

        val result = analyze(source)
        assertTrue(
            result.errors.any { it.message.contains("Type mismatch") },
            "Expected type mismatch error, got: ${result.errors}"
        )
    }

    @Test
    fun `function call with wrong argument type produces error`() {
        val source = """
            func foo(x: int): int {
                return x;
            }

            func main(): void {
                let y: int = foo(3.14);
            }
        """.trimIndent()

        val result = analyze(source)
        assertTrue(
            result.errors.any { it.message.contains("Type mismatch for argument 1 of function 'foo'") },
            "Expected argument type mismatch error, got: ${result.errors}"
        )
    }

    @Test
    fun `missing return value in non-void function produces error`() {
        val source = """
            func foo(): int {
                return;
            }
        """.trimIndent()

        val result = analyze(source)
        assertTrue(
            result.errors.any { it.message.contains("Missing return value in function 'foo'") },
            "Expected missing return value error, got: ${result.errors}"
        )
    }

    @Test
    fun `returning value from void function produces error`() {
        val source = """
            func foo(): void {
                return 42;
            }
        """.trimIndent()

        val result = analyze(source)
        assertTrue(
            result.errors.any { it.message.contains("void type must not return a value") },
            "Expected error about returning value from void function, got: ${result.errors}"
        )
    }

    @Test
    fun `array literal with mixed element types produces error`() {
        val source = """
            func main(): void {
                let arr: int[] = [1, 2.0, 3];
            }
        """.trimIndent()

        val result = analyze(source)
        assertTrue(
            result.errors.any { it.message.contains("Array literal elements must have the same type") },
            "Expected array literal element type error, got: ${result.errors}"
        )
    }
}
