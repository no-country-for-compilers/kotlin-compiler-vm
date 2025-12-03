package com.compiler

import com.compiler.lexer.Lexer
import com.compiler.lexer.LexerException
import com.compiler.parser.ParseException
import com.compiler.parser.Parser
import com.compiler.semantic.DefaultSemanticAnalyzer
import com.compiler.semantic.SemanticException
import java.io.File

object SemanticService {

    fun run(filePath: String) {
        val file = File(filePath)

        if (!file.exists()) {
            println("Error: File not found: $filePath")
            return
        }

        val source = file.readText()

        try {
            val lexer = Lexer(source)
            val tokens = lexer.tokenize()

            val parser = Parser(tokens)
            val program = parser.parse()

            val analyzer = DefaultSemanticAnalyzer()
            val result = analyzer.analyze(program)

            Printer.printSemanticAnalysis(result)
        } catch (e: LexerException) {
            println("Lexer Error:")
            println("  ${e.message}")
        } catch (e: ParseException) {
            println("Parse Error:")
            println("  ${e.message}")
        } catch (e: SemanticException) {
            println("Semantic Error:")
            println("  ${e.message}")
        } catch (e: Exception) {
            println("Unexpected error:")
            println("  ${e.message}")
            e.printStackTrace()
        }
    }
}

