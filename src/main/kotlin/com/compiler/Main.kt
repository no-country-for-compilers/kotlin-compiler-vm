package com.compiler

import com.compiler.lexer.Lexer
import com.compiler.lexer.LexerException
import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: compiler <source-file>")
        println("   or: compiler --lex <source-file>  (only tokenize)")
        return
    }
    
    when {
        args[0] == "--lex" && args.size >= 2 -> {
            // Lexical analysis only
            runLexer(args[1])
        }
        args[0] == "--help" -> {
            printHelp()
        }
        else -> {
            // Full compilation (not yet implemented)
            println("Full compilation not yet implemented.")
            println("Use --lex <file> to tokenize a source file.")
        }
    }
}

fun runLexer(filePath: String) {
    val file = File(filePath)
    
    if (!file.exists()) {
        println("Error: File not found: $filePath")
        return
    }
    
    val source = file.readText()
    
    try {
        val lexer = Lexer(source)
        val tokens = lexer.tokenize()
        
        println("=== Tokenization successful ===")
        println("Total tokens: ${tokens.size}")
        println()
        println("Tokens:")
        
        tokens.forEach { token ->
            if (token.literal != null) {
                println("  ${token.type.toString().padEnd(15)} '${token.lexeme}' = ${token.literal} at ${token.line}:${token.column}")
            } else {
                println("  ${token.type.toString().padEnd(15)} '${token.lexeme}' at ${token.line}:${token.column}")
            }
        }
        
    } catch (e: LexerException) {
        println("Lexer Error:")
        println("  ${e.message}")
    } catch (e: Exception) {
        println("Unexpected error:")
        println("  ${e.message}")
        e.printStackTrace()
    }
}

fun printHelp() {
    println("""
        Kotlin Compiler VM - Educational Compiler Project
        
        Usage:
          compiler <source-file>              Compile and run source file
          compiler --lex <source-file>        Tokenize source file (lexer only)
          compiler --help                     Show this help message
        
        Examples:
          compiler examples/factorial.lang
          compiler --lex examples/factorial.lang
        
        Source file extension: .lang
    """.trimIndent())
}
