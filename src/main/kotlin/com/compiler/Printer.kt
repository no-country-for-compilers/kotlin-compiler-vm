package com.compiler

import com.compiler.lexer.Token
import com.compiler.parser.*
import com.compiler.parser.ast.*
import com.compiler.semantic.AnalysisResult

object Printer {

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

    private fun printProgram(program: Program) {
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
                stmt.statements.forEachIndexed { i, s ->
                    printStatementTree(s, nextIndent, i == last)
                }
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
                when (val t = expr.target) {
                    is VariableExpr -> printExpressionTree(t, nextIndent + "│  ", false)
                    is ArrayAccessExpr -> printExpressionTree(t, nextIndent + "│  ", false)
                }
                println("$nextIndent└─ Value:")
                printExpressionTree(expr.value, nextIndent + "   ", true)
            }
            is CallExpr -> {
                println("${indent}${branch}Call(${expr.name})")

                if (expr.args.isNotEmpty()) {
                    println("${nextIndent}└─ Args:")
                    val last = expr.args.lastIndex
                    expr.args.forEachIndexed { i, a ->
                        printExpressionTree(a, nextIndent + "   ", i == last)
                    }
                } else {
                    println("${nextIndent}└─ Args: (none)")
                }
            }
            is ArrayAccessExpr -> {
                println("${indent}${branch}ArrayAccess")
                println("$nextIndent├─ Array:")
                printExpressionTree(expr.array, nextIndent + "│  ", false)
                println("$nextIndent└─ Index:")
                printExpressionTree(expr.index, nextIndent + "   ", true)
            }
            is ArrayInitExpr -> {
                println("${indent}${branch}ArrayInit(${expr.elementType})")

                println("$nextIndent└─ Size:")
                printExpressionTree(expr.size, nextIndent + "   ", true)
            }
        }
    }

    fun printSemanticAnalysis(result: AnalysisResult) {
        println("=== Semantic Analysis ===")
        if (result.error == null) {
            println("Semantic analysis completed successfully.")
            println()
            println("=== Symbol Table ===")
            printSymbolTable(result.globalScope, "")
        } else {
            println("Semantic analysis found errors:")
            println("  ${result.error.message}")
        }
    }

    private fun printSymbolTable(scope: com.compiler.semantic.Scope, indent: String) {
        val variables = scope.getAllVariables()
        val functions = scope.getAllFunctions()

        if (variables.isEmpty() && functions.isEmpty()) {
            println("${indent}(empty)")
            return
        }

        if (functions.isNotEmpty()) {
            println("${indent}Functions:")
            functions.values.forEach { fn ->
                val params = fn.parameters.joinToString(", ") { "${it.name}: ${it.type}" }
                println("${indent}  ${fn.name}($params): ${fn.returnType}")
            }
        }

        if (variables.isNotEmpty()) {
            if (functions.isNotEmpty()) {
                println()
            }
            println("${indent}Variables:")
            variables.values.forEach { varSymbol ->
                println("${indent}  ${varSymbol.name}: ${varSymbol.type}")
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
}
