package com.compiler.semantic

import com.compiler.lexer.Lexer
import com.compiler.parser.Parser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

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
        assertNull(result.error, "Expected no semantic errors, got: ${result.error}")
    }

    @Test
    fun `undefined variable produces error`() {
        val source = """
            func main(): void {
                let x: int = y + 1;
            }
        """.trimIndent()

        val exception = assertThrows<SemanticException> {
            analyze(source)
        }
        assertTrue(
            exception.message?.contains("Undefined variable 'y'") == true,
            "Expected undefined variable error, got: ${exception.message}"
        )
    }

    @Test
    fun `type mismatch in var declaration produces error`() {
        val source = """
            func main(): void {
                let x: int = 3.14;
            }
        """.trimIndent()

        val exception = assertThrows<SemanticException> {
            analyze(source)
        }
        assertTrue(
            exception.message?.contains("Type mismatch") == true,
            "Expected type mismatch error, got: ${exception.message}"
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

        val exception = assertThrows<SemanticException> {
            analyze(source)
        }
        assertTrue(
            exception.message?.contains("Type mismatch for argument 1 of function 'foo'") == true,
            "Expected argument type mismatch error, got: ${exception.message}"
        )
    }

    @Test
    fun `missing return value in non-void function produces error`() {
        val source = """
            func foo(): int {
                return;
            }
        """.trimIndent()

        val exception = assertThrows<SemanticException> {
            analyze(source)
        }
        assertTrue(
            exception.message?.contains("Missing return value in function 'foo'") == true,
            "Expected missing return value error, got: ${exception.message}"
        )
    }

    @Test
    fun `returning value from void function produces error`() {
        val source = """
            func foo(): void {
                return 42;
            }
        """.trimIndent()

        val exception = assertThrows<SemanticException> {
            analyze(source)
        }
        assertTrue(
            exception.message?.contains("void type must not return a value") == true,
            "Expected error about returning value from void function, got: ${exception.message}"
        )
    }

    @Test
    fun `array literal with mixed element types produces error`() {
        val source = """
            func main(): void {
                let arr: int[] = [1, 2.0, 3];
            }
        """.trimIndent()

        val exception = assertThrows<SemanticException> {
            analyze(source)
        }
        assertTrue(
            exception.message?.contains("Array literal elements must have the same type") == true,
            "Expected array literal element type error, got: ${exception.message}"
        )
    }
}
