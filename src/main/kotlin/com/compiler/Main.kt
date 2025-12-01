package com.compiler

import com.compiler.lexer.Lexer
import com.compiler.lexer.LexerException
import com.compiler.lexer.Token
import com.compiler.parser.*
import java.io.File

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: compiler <source-file>")
        println("   or: compiler --lex <source-file>  (only tokenize)")
        println("   or: compiler --parse <source-file> (tokenize + parse and print AST)")
        return
    }
    
    when {
        args[0] == "--lex" && args.size >= 2 -> {
            runLexer(args[1])
        }
        args[0] == "--parse" && args.size >= 2 -> {
            runParse(args[1])
        }
        args[0] == "--help" -> {
            printHelp()
        }
        else -> {
            println("Full compilation not yet implemented.")
            println("Use --lex <file> to tokenize a source file.")
            println("Use --parse <file> to tokenize + parse and print AST.")
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
        
        printTokens(tokens)
    } catch (e: LexerException) {
        println("Lexer Error:")
        println("  ${e.message}")
    } catch (e: Exception) {
        println("Unexpected error:")
        println("  ${e.message}")
        e.printStackTrace()
    }
}

fun runParse(filePath: String) {
    val file = File(filePath)

    if (!file.exists()) {
        println("Error: File not found: $filePath")
        return
    }

    val source = file.readText()

    try {
        val lexer = Lexer(source)
        val tokens = lexer.tokenize()

        printTokens(tokens)
        println()
        
        val parser = Parser(tokens)
        val program = parser.parse()

        printParsing(program)
    } catch (e: LexerException) {
        println("Lexer Error:")
        println("  ${e.message}")
    } catch (e: ParseException) {
        println("Parse Error:")
        println("  ${e.message}")
    } catch (e: Exception) {
        println("Unexpected error:")
        println("  ${e.message}")
        e.printStackTrace()
    }
}

fun printTokens(tokens: List<Token>) {
    println("=== Tokenization successful ===")
    println("Total tokens: ${tokens.size}")
    println()
    println("Tokens:")
    tokens.forEach { println("  ${it.type.name.padEnd(15)} $it") }
}

fun printParsing(program: Program) {
    println("=== Parsing ===")
    println("Parsing completed successfully.")
    println()
    println("=== AST ===")
    printProgram(program)
}

fun printProgram(program: Program) {
    println("Program")
    val lastIndex = program.statements.lastIndex
    program.statements.forEachIndexed { i, stmt ->
        val isLast = i == lastIndex
        printStatementTree(stmt, "", isLast)
    }
}

private fun printStatementTree(stmt: Statement, indent: String, isLast: Boolean) {
    val branch = if (isLast) "└─ " else "├─ "
    val nextIndent = indent + if (isLast) "   " else "│  "

    when (stmt) {
        is FunctionDecl -> {
            println("${indent}${branch}FunctionDecl: \"${stmt.identifier}\"")
            if (stmt.parameters.isNotEmpty()) {
                println("$nextIndent├─ Parameters:")
                val lp = stmt.parameters.lastIndex
                stmt.parameters.forEachIndexed { i, p ->
                    val pLast = i == lp
                    val pIndent = nextIndent + if (pLast) "└─ " else "├─ "
                    println("${pIndent}${p.identifier}: ${typeToString(p.type)}")
                }
            }
            println("$nextIndent├─ ReturnType: ${typeToString(stmt.returnType)}")
            println("$nextIndent└─ Body:")
            printStatementTree(stmt.body, nextIndent + "   ", true)
        }
        is VarDecl -> {
            println("${indent}${branch}VarDecl: ${stmt.identifier}: ${typeToString(stmt.type)}")
            println("${nextIndent}└─ Initializer:")
            printExpressionTree(stmt.expression, nextIndent + "   ", true)
        }
        is IfStmt -> {
            println("${indent}${branch}IfStmt")
            println("$nextIndent├─ Condition:")
            printExpressionTree(stmt.condition, nextIndent + "│  ", true)
            println("$nextIndent├─ Then:")
            printStatementTree(stmt.thenBranch, nextIndent + "│  ", stmt.elseBranch == null)
            if (stmt.elseBranch != null) {
                println("$nextIndent└─ Else:")
                printStatementTree(stmt.elseBranch, nextIndent + "   ", true)
            }
        }
        is ForStmt -> {
            println("${indent}${branch}ForStmt")
            println("$nextIndent├─ Initializer:")
            when (val init = stmt.initializer) {
                is ForVarInit -> printStatementTree(init.decl, nextIndent + "│  ", false)
                is ForExprInit -> printExpressionTree(init.expr, nextIndent + "│  ", false)
                is ForNoInit -> println("$nextIndent│  (none)")
            }
            println("$nextIndent├─ Condition:")
            stmt.condition?.let { printExpressionTree(it, nextIndent + "│  ", false) }
                ?: println("$nextIndent│  (none)")
            println("$nextIndent├─ Increment:")
            stmt.increment?.let { printExpressionTree(it, nextIndent + "│  ", false) }
                ?: println("$nextIndent│  (none)")
            println("$nextIndent└─ Body:")
            printStatementTree(stmt.body, nextIndent + "   ", true)
        }
        is ReturnStmt -> {
            println("${indent}${branch}Return")
            if (stmt.value != null) {
                println("$nextIndent└─ Value:")
                printExpressionTree(stmt.value, nextIndent + "   ", true)
            }
        }
        is ExprStmt -> {
            println("${indent}${branch}ExprStmt")
            printExpressionTree(stmt.expr, nextIndent, true)
        }
        is BlockStmt -> {
            println("${indent}${branch}Block")
            val last = stmt.statements.lastIndex
            stmt.statements.forEachIndexed { i, s -> printStatementTree(s, nextIndent, i == last) }
        }
    }
}

private fun printExpressionTree(expr: Expression, indent: String, isLast: Boolean) {
    val branch = if (isLast) "└─ " else "├─ "
    val nextIndent = indent + if (isLast) "   " else "│  "

    when (expr) {
        is LiteralExpr -> println("${indent}${branch}Literal(${expr.value})")
        is VariableExpr -> println("${indent}${branch}Variable(${expr.name})")
        is BinaryExpr -> {
            println("${indent}${branch}BinaryExpr(${expr.operator.name})")
            println("$nextIndent├─ Left:")
            printExpressionTree(expr.left, nextIndent + "│  ", false)
            println("$nextIndent└─ Right:")
            printExpressionTree(expr.right, nextIndent + "   ", true)
        }
        is UnaryExpr -> {
            println("${indent}${branch}UnaryExpr(${expr.operator.name})")
            printExpressionTree(expr.right, nextIndent, true)
        }
        is GroupingExpr -> {
            println("${indent}${branch}Grouping")
            printExpressionTree(expr.expression, nextIndent, true)
        }
        is AssignExpr -> {
            println("${indent}${branch}Assign")
            println("$nextIndent├─ Target:")
            printExpressionTree(expr.target as Expression, nextIndent + "│  ", false)
            println("$nextIndent└─ Value:")
            printExpressionTree(expr.value, nextIndent + "   ", true)
        }
        is CallExpr -> {
            println("${indent}${branch}Call")
            println("$nextIndent├─ Callee:")
            printExpressionTree(expr.callee, nextIndent + "│  ", false)
            if (expr.args.isNotEmpty()) {
                println("$nextIndent└─ Args:")
                val last = expr.args.lastIndex
                expr.args.forEachIndexed { i, a ->
                    printExpressionTree(a, nextIndent + "   ", i == last)
                }
            } else {
                println("$nextIndent└─ Args: (none)")
            }
        }
        is ArrayLiteralExpr -> {
            println("${indent}${branch}ArrayLiteral")
            val last = expr.elements.lastIndex
            expr.elements.forEachIndexed { i, e -> printExpressionTree(e, nextIndent, i == last) }
        }
        is ArrayAccessExpr -> {
            println("${indent}${branch}ArrayAccess")
            println("$nextIndent├─ Array:")
            printExpressionTree(expr.array, nextIndent + "│  ", false)
            println("$nextIndent└─ Index:")
            printExpressionTree(expr.index, nextIndent + "   ", true)
        }
        is PropertyAccessExpr -> {
            println("${indent}${branch}Property(.${expr.property})")
            printExpressionTree(expr.receiver, nextIndent, true)
        }
    }
}

private fun typeToString(type: TypeNode): String =
        when (type) {
            TypeNode.IntType -> "int"
            TypeNode.FloatType -> "float"
            TypeNode.BoolType -> "bool"
            TypeNode.VoidType -> "void"
            is TypeNode.ArrayType -> "${typeToString(type.elementType)}[]"
}

fun printHelp() {
    println(
            """
        Kotlin Compiler VM - Educational Compiler Project

        Usage:
          compiler <source-file>              Compile and run source file
          compiler --lex <source-file>        Tokenize source file (lexer only)
          compiler --parse <source-file>       Tokenize + parse and pretty-print AST
          compiler --help                     Show this help message

        Examples:
          compiler examples/factorial.lang
          compiler --lex examples/factorial.lang
          compiler --parse examples/factorial.lang

        Source file extension: .lang
    """.trimIndent()
    )
}
