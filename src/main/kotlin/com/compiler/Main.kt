package com.compiler

import com.compiler.lexer.Lexer
import com.compiler.lexer.LexerException
import com.compiler.lexer.Token
import com.compiler.parser.*
import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        Help.printHelp()
        return
    }

    when {
        args[0] == "--lex" && args.size >= 2 -> {
            LexerService.run(args[1])
        }
        args[0] == "--parse" && args.size >= 2 -> {
            ParserService.run(args[1])
        }
        args[0] == "--semantic" && args.size >= 2 -> {
            SemanticService.run(args[1])
        }
        args[0] == "--run" && args.size >= 2 -> {
            VMService.run(args[1])
        }
        args[0] == "--help" -> {
            Help.printHelp()
        }
        args[0].endsWith(".lang") || (!args[0].startsWith("--") && args.size == 1) -> {
            // Default: compile and run
            VMService.run(args[0])
        }
        else -> {
            println("Full compilation not yet implemented.")
            println("Use --lex <file> to tokenize a source file.")
            println("Use --parse <file> to tokenize + parse and print AST.")
            println("Use --semantic <file> to perform semantic analysis.")
            println("Use --run <file> to compile and run on VM.")
        }
    }
}
