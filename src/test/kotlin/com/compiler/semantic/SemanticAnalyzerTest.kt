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

    // ========== Tests for built-in print function ==========

    @Test
    fun `print with primitive types is valid`() {
        val source = """
            func main(): void {
                print(42);
                print(3.14);
                print(true);
            }
        """.trimIndent()

        val result = analyze(source)
        assertNull(result.error, "Expected no semantic errors for print with primitive types")
    }

    @Test
    fun `printArray with array types is valid`() {
        val source = """
            func main(): void {
                let intArr: int[] = int[5];
                let floatArr: float[] = float[3];
                let boolArr: bool[] = bool[2];
                printArray(intArr);
                printArray(floatArr);
                printArray(boolArr);
            }
        """.trimIndent()

        val result = analyze(source)
        assertNull(result.error, "Expected no semantic errors for printArray with array types")
    }

    @Test
    fun `print without arguments produces error`() {
        val source = """
            func main(): void {
                print();
            }
        """.trimIndent()

        val exception = assertThrows<SemanticException> {
            analyze(source)
        }
        assertTrue(
            exception.message?.contains("expects 1 arguments but got 0") == true,
            "Expected error about missing arguments, got: ${exception.message}"
        )
    }

    @Test
    fun `print with too many arguments produces error`() {
        val source = """
            func main(): void {
                print(42, 3.14);
            }
        """.trimIndent()

        val exception = assertThrows<SemanticException> {
            analyze(source)
        }
        assertTrue(
            exception.message?.contains("expects 1 arguments but got 2") == true,
            "Expected error about too many arguments, got: ${exception.message}"
        )
    }

    @Test
    fun `print and printArray are available in global scope`() {
        val source = """
            func main(): void {
                print(42);
                let arr: int[] = int[5];
                printArray(arr);
            }
        """.trimIndent()

        val result = analyze(source)
        assertNull(result.error, "Expected print and printArray to be available in global scope")
        
        val printFunction = result.globalScope.resolveFunction("print")
        assertNotNull(printFunction, "Expected print function to be in global scope")
        
        val printArrayFunction = result.globalScope.resolveFunction("printArray")
        assertNotNull(printArrayFunction, "Expected printArray function to be in global scope")
    }
}
