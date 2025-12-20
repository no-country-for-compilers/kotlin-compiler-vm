package com.compiler

import com.compiler.lexer.Lexer
import com.compiler.lexer.LexerException
import com.compiler.parser.ParseException
import com.compiler.parser.Parser
import com.compiler.semantic.DefaultSemanticAnalyzer
import com.compiler.semantic.SemanticException
import com.compiler.bytecode.BytecodeGenerator
import java.io.File

object BytecodeService {

    fun compile(filePath: String): com.compiler.bytecode.BytecodeModule? {
        val file = File(filePath)

        if (!file.exists()) {
            println("Error: File not found: $filePath")
            return null
        }

        val source = file.readText()

        try {
            val lexer = Lexer(source)
            val tokens = lexer.tokenize()

            val parser = Parser(tokens)
            val program = parser.parse()

            val analyzer = DefaultSemanticAnalyzer()
            val result = analyzer.analyze(program)

            if (result.error != null) {
                println("Semantic Error:")
                println("  ${result.error.message}")
                return null
            }

            val generator = BytecodeGenerator(program, result.globalScope)
            return generator.generate()
        } catch (e: LexerException) {
            println("Lexer Error:")
            println("  ${e.message}")
            return null
        } catch (e: ParseException) {
            println("Parse Error:")
            println("  ${e.message}")
            return null
        } catch (e: SemanticException) {
            println("Semantic Error:")
            println("  ${e.message}")
            return null
        } catch (e: Exception) {
            println("Unexpected error:")
            println("  ${e.message}")
            e.printStackTrace()
            return null
        }
    }
}

