package com.compiler.semantic

import com.compiler.parser.ast.*
import com.compiler.lexer.TokenType
import com.compiler.domain.SourcePos

interface SemanticAnalyzer {
    fun analyze(program: Program): AnalysisResult
}

class DefaultSemanticAnalyzer : SemanticAnalyzer {

    private lateinit var globalScope: Scope
    private var currentFunction: FunctionSymbol? = null

    override fun analyze(program: Program): AnalysisResult {
        globalScope = Scope(parent = null)

        for (stmt in program.statements) {
            analyzeStatement(stmt, globalScope)
        }

        return AnalysisResult(
            program = program,
            globalScope = globalScope,
            error = null
        )
    }

    private fun analyzeStatement(stmt: Statement, scope: Scope) {
        when (stmt) {
            is FunctionDecl -> analyzeFunctionDecl(stmt, scope)
            is VarDecl -> analyzeVarDecl(stmt, scope)
            is BlockStmt -> analyzeBlock(stmt, scope)
            is ExprStmt -> analyzeExpression(stmt.expr, scope)
            is IfStmt -> analyzeIf(stmt, scope)
            is ForStmt -> analyzeFor(stmt, scope)
            is ReturnStmt -> analyzeReturn(stmt, scope)
        }
    }

    private fun analyzeFunctionDecl(fn: FunctionDecl, scope: Scope) {
        val paramSymbols = mutableListOf<VariableSymbol>()
        for (p in fn.parameters) {
            val pType = p.type.toSemanticType()
            paramSymbols += VariableSymbol(p.identifier, pType)
        }
        val returnType = fn.returnType.toSemanticType()
        val fnSymbol = FunctionSymbol(fn.identifier, paramSymbols, returnType)

        if (!scope.defineFunction(fnSymbol)) {
            report(
                "Function '${fn.identifier}' is already defined in this scope",
                fn.pos
            )
        }

        val seenNames = mutableSetOf<String>()
        for (param in fn.parameters) {
            if (!seenNames.add(param.identifier)) {
                report(
                    "Duplicate parameter name '${param.identifier}' in function '${fn.identifier}'",
                    param.pos
                )
            }
        }

        val prevFunction = currentFunction
        currentFunction = fnSymbol

        val functionScope = Scope(scope)

        for (paramSymbol in paramSymbols) {
            if (!functionScope.defineVariable(paramSymbol)) {
                report(
                    "Parameter '${paramSymbol.name}' is already defined in function '${fn.identifier}'",
                    fn.pos
                )
            }
        }

        analyzeBlock(fn.body, functionScope)

        currentFunction = prevFunction
    }

    private fun analyzeVarDecl(decl: VarDecl, scope: Scope) {
        val declaredType = decl.type.toSemanticType()

        val initType = analyzeExpression(decl.expression, scope)

        if (!isAssignable(initType, declaredType)) {
            report(
                "Type mismatch: cannot assign value of type '${initType}' to variable '${decl.identifier}' of type '${declaredType}'",
                decl.pos
            )
        }

        val symbol = VariableSymbol(decl.identifier, declaredType)
        if (!scope.defineVariable(symbol)) {
            report(
                "Variable '${decl.expression}' is already defined in this scope",
                decl.pos
            )
        }
    }

    private fun analyzeBlock(block: BlockStmt, parent: Scope) {
        val innerScope = Scope(parent)
        for (stmt in block.statements) {
            analyzeStatement(stmt, innerScope)
        }
    }

    private fun analyzeIf(stmt: IfStmt, scope: Scope) {
        val condType = analyzeExpression(stmt.condition, scope)
        if (condType != Type.Bool && condType != Type.Unknown) {
            report(
                "If condition must be of type 'bool', got '$condType'",
                null
            )
        }

        analyzeBlock(stmt.thenBranch, scope)

        stmt.elseBranch?.let { elseBlock ->
            analyzeBlock(elseBlock, scope)
        }
    }

    private fun analyzeFor(stmt: ForStmt, parentScope: Scope) {
        val loopScope = Scope(parentScope)

        when (val init = stmt.initializer) {
            is ForNoInit -> {
                // ничего
            }
            is ForVarInit -> {
                analyzeVarDecl(init.decl, loopScope)
            }
            is ForExprInit -> {
                analyzeExpression(init.expr, loopScope)
            }
        }

        stmt.condition?.let { cond ->
            val condType = analyzeExpression(cond, loopScope)
            if (condType != Type.Bool && condType != Type.Unknown) {
                report(
                    "For-loop condition must be of type 'bool', got '$condType'",
                    null
                )
            }
        }

        stmt.increment?.let { inc ->
            analyzeExpression(inc, loopScope)
        }

        analyzeBlock(stmt.body, loopScope)
    }

    private fun analyzeReturn(stmt: ReturnStmt, scope: Scope) {
        val fn = currentFunction
        if (fn == null) {
            report(
                "Return statement is not allowed outside of a function",
                stmt.pos
            )
            stmt.value?.let { analyzeExpression(it, scope) } // чтобы поймать ошибки внутри
            return
        }

        val expected = fn.returnType

        if (expected == Type.Void) {
            if (stmt.value != null) {
                val actual = analyzeExpression(stmt.value, scope)
                report(
                    "Return statement in function '${fn.name}' of void type must not return a value (got '$actual')",
                    stmt.pos
                )
            }
            return
        }

        if (stmt.value == null) {
            report(
                "Missing return value in function '${fn.name}' of non-void type '$expected'",
                stmt.pos
            )
            return
        }

        val actual = analyzeExpression(stmt.value, scope)
        if (!isAssignable(actual, expected)) {
            report(
                "Return type mismatch in function '${fn.name}': expected '$expected', got '$actual'",
                stmt.pos
            )
        }
    }

    private fun analyzeExpression(expr: Expression, scope: Scope): Type {
        return when (expr) {
            is LiteralExpr -> inferLiteralType(expr)
            is VariableExpr -> analyzeVariableExpr(expr, scope)
            is GroupingExpr -> analyzeExpression(expr.expression, scope)
            is UnaryExpr -> analyzeUnaryExpr(expr, scope)
            is BinaryExpr -> analyzeBinaryExpr(expr, scope)
            is CallExpr -> analyzeCallExpr(expr, scope)
            is ArrayAccessExpr -> analyzeArrayAccess(expr, scope)
            is AssignExpr -> analyzeAssignExpr(expr, scope)
            is ArrayInitExpr -> analyzeArrayInitExpr(expr, scope)
        }
    }

    private fun inferLiteralType(expr: LiteralExpr): Type {
        return when (val v = expr.value) {
            is Long -> Type.Int
            is Double -> Type.Float
            is Boolean -> Type.Bool
            null -> Type.Unknown
            else -> Type.Unknown
        }
    }

    private fun analyzeVariableExpr(expr: VariableExpr, scope: Scope): Type {
        val symbol = scope.resolveVariable(expr.name)
        if (symbol == null) {
            val fn = scope.resolveFunction(expr.name)
            if (fn != null) {
                report(
                    "Cannot use function '${expr.name}' as a value",
                    expr.pos
                )
            } else {
                report(
                    "Undefined variable '${expr.name}'",
                    expr.pos
                )
            }
            return Type.Unknown
        }
        return symbol.type
    }

    private fun analyzeUnaryExpr(expr: UnaryExpr, scope: Scope): Type {
        val operandType = analyzeExpression(expr.right, scope)
        return when (expr.operator) {
            TokenType.MINUS, TokenType.PLUS -> {
                if (operandType != Type.Int && operandType != Type.Float && operandType != Type.Unknown) {
                    report(
                        "Unary operator '${expr.operator}' is not defined for type '$operandType'",
                        expr.pos
                    )
                    Type.Unknown
                } else {
                    operandType
                }
            }

            TokenType.NOT -> {
                if (operandType != Type.Bool && operandType != Type.Unknown) {
                    report(
                        "Logical not operator '!' expects operand of type 'bool', got '$operandType'",
                        expr.pos
                    )
                    Type.Unknown
                } else {
                    Type.Bool
                }
            }

            else -> {
                Type.Unknown
            }
        }
    }

    private fun analyzeBinaryExpr(expr: BinaryExpr, scope: Scope): Type {
        val leftType = analyzeExpression(expr.left, scope)
        val rightType = analyzeExpression(expr.right, scope)

        if (leftType == Type.Unknown || rightType == Type.Unknown) {
            return Type.Unknown
        }

        return when (expr.operator) {
            TokenType.PLUS, TokenType.MINUS, TokenType.STAR,
            TokenType.SLASH, TokenType.PERCENT -> {
                if (!isNumeric(leftType) || !isNumeric(rightType)) {
                    report(
                        "Operator '${expr.operator}' is not defined for types '$leftType' and '$rightType'",
                        expr.pos
                    )
                    Type.Unknown
                } else if (leftType != rightType) {
                    report(
                        "Operands of arithmetic operator '${expr.operator}' must have the same type, got '$leftType' and '$rightType'",
                        expr.pos
                    )
                    Type.Unknown
                } else {
                    leftType
                }
            }

            TokenType.LT, TokenType.LE, TokenType.GT, TokenType.GE -> {
                if (!isNumeric(leftType) || !isNumeric(rightType)) {
                    report(
                        "Comparison operator '${expr.operator}' is not defined for types '$leftType' and '$rightType'",
                        expr.pos
                    )
                    Type.Unknown
                } else if (leftType != rightType) {
                    report(
                        "Operands of comparison operator '${expr.operator}' must have the same type, got '$leftType' and '$rightType'",
                        expr.pos
                    )
                    Type.Unknown
                } else {
                    Type.Bool
                }
            }

            TokenType.EQ, TokenType.NE -> {
                if (!areComparable(leftType, rightType)) {
                    report(
                        "Equality operator '${expr.operator}' is not defined for types '$leftType' and '$rightType'",
                        expr.pos
                    )
                    Type.Unknown
                } else {
                    Type.Bool
                }
            }

            TokenType.AND, TokenType.OR -> {
                if (leftType != Type.Bool || rightType != Type.Bool) {
                    report(
                        "Logical operator '${expr.operator}' expects operands of type 'bool', got '$leftType' and '$rightType'",
                        expr.pos
                    )
                    Type.Unknown
                } else {
                    Type.Bool
                }
            }

            else -> Type.Unknown
        }
    }

    private fun analyzeCallExpr(expr: CallExpr, scope: Scope): Type {
        val name = expr.name

        val fn = scope.resolveFunction(name)
        if (fn == null) {
            val varSymbol = scope.resolveVariable(name)
            if (varSymbol != null) {
                report("Cannot call non-function '$name' of type '${varSymbol.type}'", expr.pos)
            } else {
                report("Undefined function '$name'", expr.pos)
            }
            expr.args.forEach { analyzeExpression(it, scope) }
            return Type.Unknown
        }

        if (expr.args.size != fn.parameters.size) {
            report(
                    "Function '${fn.name}' expects ${fn.parameters.size} arguments but got ${expr.args.size}",
                    expr.pos
            )
        }

        // Типы аргументов
        val argTypes = expr.args.map { analyzeExpression(it, scope) }
        val paramTypes = fn.parameters.map { it.type }

        val count = minOf(argTypes.size, paramTypes.size)
        for (i in 0 until count) {
            val argType = argTypes[i]
            val paramType = paramTypes[i]
            if (!isAssignable(argType, paramType)) {
                report(
                        "Type mismatch for argument ${i + 1} of function '${fn.name}': expected '$paramType', got '$argType'",
                        expr.pos
                )
            }
        }

        return fn.returnType
    }

    private fun analyzeArrayAccess(expr: ArrayAccessExpr, scope: Scope): Type {
        val targetType = analyzeExpression(expr.array, scope)
        val indexType = analyzeExpression(expr.index, scope)

        if (indexType != Type.Int && indexType != Type.Unknown) {
            report(
                "Index expression must be of type 'int', got '$indexType'",
                expr.pos
            )
        }

        return when (targetType) {
            is Type.Array -> targetType.elementType
            Type.Unknown -> Type.Unknown
            else -> {
                report(
                    "Cannot index value of non-array type '$targetType'",
                    expr.pos
                )
                Type.Unknown
            }
        }
    }

    private fun analyzeAssignExpr(expr: AssignExpr, scope: Scope): Type {
        val targetType = when (val target = expr.target) {
            is VariableExpr -> analyzeVariableExpr(target, scope)
            is ArrayAccessExpr -> analyzeArrayAccess(target, scope)
        }

        val valueType = analyzeExpression(expr.value, scope)

        if (!isAssignable(valueType, targetType)) {
            report(
                "Type mismatch in assignment: cannot assign value of type '$valueType' to target of type '$targetType'",
                expr.pos
            )
        }

        return targetType
    }

    private fun analyzeArrayInitExpr(expr: ArrayInitExpr, scope: Scope): Type {
        // TODO: implement array initialization analysis
        return Type.Unknown
    }


    private fun isNumeric(t: Type): Boolean =
        t == Type.Int || t == Type.Float

    private fun areComparable(a: Type, b: Type): Boolean =
        a == b || a == Type.Unknown || b == Type.Unknown

    private fun isAssignable(from: Type, to: Type): Boolean {
        if (to == Type.Unknown || from == Type.Unknown) return true
        return from == to
    }

    private fun report(message: String, position: Any?) {
        val pos = when (position) {
            is SourcePos -> position
            null -> null
            else -> null
        }
        throw SemanticException(pos, message)
    }
}
