package com.compiler

object Help {

    fun printHelp() {
        println(
                """
            Kotlin Compiler VM - Educational Compiler Project

            Usage:
              compiler <source-file>              Compile and run source file
              compiler --lex <source-file>        Tokenize source file (lexer only)
              compiler --parse <source-file>      Tokenize + parse and pretty-print AST
              compiler --semantic <source-file>   Perform semantic analysis
              compiler --run <source-file>        Compile and run on virtual machine
              compiler --help                     Show this help message

            Examples:
              compiler examples/factorial.lang
              compiler --lex examples/factorial.lang
              compiler --parse examples/factorial.lang
              compiler --semantic examples/factorial.lang
              compiler --run examples/factorial.lang

            Source file extension: .lang
        """.trimIndent()
        )
    }
}
