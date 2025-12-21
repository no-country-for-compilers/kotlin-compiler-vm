package com.compiler.parser.ast.optimizations

import com.compiler.domain.SourcePos
import com.compiler.lexer.TokenType
import com.compiler.parser.ast.*

/**
 * Performs a constant folding optimization pass over the AST.
 *
 * Implements an immutable post-order traversal of the tree and returns a new [Program] in which
 * literals computed at compile time are substituted.
 *
 * Supports folding of boolean and numeric (Long, Double) expressions, unary operations,
 * simplification of [IfStmt] branching with constant conditions, folding of call arguments and
 * array indices.
 *
 * Division and modulo by zero are not evaluated during folding.
 */
object ConstantFolder : AstOptimization {

    override val name = "Constant Folding"

    /**
     * Runs the constant folding optimization for the program root node.
     *
     * @param program original program AST
     * @return new [Program] with applied optimization
     */
    override fun apply(program: Program): Program {
        val folded = program.statements.map { foldStatement(it) }
        return Program(folded)
    }

    /**
     * Processes a statement node and returns its folded version.
     *
     * @param stmt input statement
     * @return folded statement
     */
    private fun foldStatement(stmt: Statement): Statement =
            when (stmt) {
                is VarDecl -> stmt.copy(expression = foldExpression(stmt.expression))
                is FunctionDecl -> stmt.copy(body = foldBlock(stmt.body))
                is BlockStmt -> foldBlock(stmt)
                is IfStmt -> foldIf(stmt)
                is ForStmt -> foldFor(stmt)
                is ReturnStmt -> stmt.copy(value = stmt.value?.let { foldExpression(it) })
                is ExprStmt -> ExprStmt(foldExpression(stmt.expr))
            }

    /**
     * Processes a block of statements.
     *
     * @param block input block
     * @return new [BlockStmt] with transformed statements
     */
    private fun foldBlock(block: BlockStmt): BlockStmt {
        val foldedStmts = block.statements.map { foldStatement(it) }
        return BlockStmt(foldedStmts)
    }

    /**
     * Simplifies a conditional statement if the condition is a constant.
     *
     * @param ifStmt input [IfStmt]
     * @return replaced statement: then-branch, else-branch, or original if with folded parts
     */
    private fun foldIf(ifStmt: IfStmt): Statement {
        val condF = foldExpression(ifStmt.condition)
        if (condF is LiteralExpr && condF.value is Boolean) {
            return if (condF.value) {
                foldBlock(ifStmt.thenBranch)
            } else {
                ifStmt.elseBranch?.let { foldBlock(it) } ?: BlockStmt(emptyList())
            }
        }
        val thenF = foldBlock(ifStmt.thenBranch)
        val elseF = ifStmt.elseBranch?.let { foldBlock(it) }
        return IfStmt(condF, thenF, elseF)
    }

    /**
     * Processes a for-loop statement and its components.
     *
     * @param forStmt input [ForStmt]
     * @return new [ForStmt] with transformed parts
     */
    private fun foldFor(forStmt: ForStmt): ForStmt {
        val initF: ForInitializer =
                when (val init = forStmt.initializer) {
                    is ForVarInit ->
                            ForVarInit(
                                    init.decl.copy(
                                            expression = foldExpression(init.decl.expression)
                                    )
                            )
                    is ForExprInit -> ForExprInit(foldExpression(init.expr))
                    ForNoInit -> ForNoInit
                }
        val condF = forStmt.condition?.let { foldExpression(it) }
        val incF = forStmt.increment?.let { foldExpression(it) }
        val bodyF = foldBlock(forStmt.body)
        return ForStmt(initF, condF, incF, bodyF)
    }

    /**
     * Processes an expression and returns its folded version if possible.
     *
     * @param expr input expression
     * @return folded expression or original one (with processed children)
     */
    private fun foldExpression(expr: Expression): Expression =
            when (expr) {
                is LiteralExpr -> expr
                is VariableExpr -> expr
                is GroupingExpr -> {
                    val inner = foldExpression(expr.expression)
                    if (inner is LiteralExpr) inner else GroupingExpr(inner, expr.pos)
                }
                is ArrayInitExpr -> expr.copy(size = foldExpression(expr.size))
                is ArrayAccessExpr -> {
                    val arr = foldExpression(expr.array)
                    val idx = foldExpression(expr.index)
                    ArrayAccessExpr(arr, idx, expr.pos)
                }
                is CallExpr -> CallExpr(expr.name, expr.args.map { foldExpression(it) }, expr.pos)
                is AssignExpr -> {
                    val tgt =
                            when (val t = expr.target) {
                                is VariableExpr -> t
                                is ArrayAccessExpr -> {
                                    val a = foldExpression(t.array)
                                    val i = foldExpression(t.index)
                                    ArrayAccessExpr(a, i, t.pos)
                                }
                            }
                    AssignExpr(tgt, foldExpression(expr.value), expr.pos)
                }
                is UnaryExpr -> foldUnary(expr)
                is BinaryExpr -> foldBinary(expr)
            }

    /**
     * Processes a unary expression and evaluates it if it contains a literal.
     *
     * @param un input [UnaryExpr]
     * @return computed [LiteralExpr] or new [UnaryExpr] with folded operand
     */
    private fun foldUnary(un: UnaryExpr): Expression {
        val rightF = foldExpression(un.right)
        if (rightF is LiteralExpr) {
            val v = rightF.value
            return when (un.operator) {
                TokenType.NOT ->
                        if (v is Boolean) LiteralExpr(!v, un.pos)
                        else UnaryExpr(un.operator, rightF, un.pos)
                TokenType.MINUS ->
                        when (v) {
                            is Long -> LiteralExpr(-v, un.pos)
                            is Double -> LiteralExpr(-v, un.pos)
                            else -> UnaryExpr(un.operator, rightF, un.pos)
                        }
                TokenType.PLUS ->
                        when (v) {
                            is Long -> LiteralExpr(+v, un.pos)
                            is Double -> LiteralExpr(+v, un.pos)
                            else -> UnaryExpr(un.operator, rightF, un.pos)
                        }
                else -> UnaryExpr(un.operator, rightF, un.pos)
            }
        }
        return UnaryExpr(un.operator, rightF, un.pos)
    }

    /**
     * Processes a binary expression, attempting to evaluate arithmetic/logical operations on
     * constants.
     *
     * @param bin input [BinaryExpr]
     * @return computed [LiteralExpr] or new [BinaryExpr] with folded operands
     */
    private fun foldBinary(bin: BinaryExpr): Expression {
        val leftF = foldExpression(bin.left)
        val rightF = foldExpression(bin.right)

        if (bin.operator == TokenType.OR || bin.operator == TokenType.AND) {
            val lLit = leftF as? LiteralExpr
            if (lLit?.value is Boolean) {
                return when (bin.operator) {
                    TokenType.OR -> if (lLit.value) LiteralExpr(true, bin.pos) else rightF
                    TokenType.AND -> if (!lLit.value) LiteralExpr(false, bin.pos) else rightF
                    else -> BinaryExpr(leftF, bin.operator, rightF, bin.pos)
                }
            }
        }

        if (leftF is LiteralExpr && rightF is LiteralExpr) {
            val l = leftF.value
            val r = rightF.value

            if (l is Boolean && r is Boolean) {
                return when (bin.operator) {
                    TokenType.AND -> LiteralExpr(l && r, bin.pos)
                    TokenType.OR -> LiteralExpr(l || r, bin.pos)
                    TokenType.EQ -> LiteralExpr(l == r, bin.pos)
                    TokenType.NE -> LiteralExpr(l != r, bin.pos)
                    else -> BinaryExpr(leftF, bin.operator, rightF, bin.pos)
                }
            }

            if ((l is Long || l is Double) && (r is Long || r is Double)) {
                try {
                    return when (bin.operator) {
                        TokenType.PLUS -> numericPlus(l, r, bin.pos)
                        TokenType.MINUS -> numericMinus(l, r, bin.pos)
                        TokenType.STAR -> numericMul(l, r, bin.pos)
                        TokenType.SLASH -> numericDiv(l, r, bin.pos)
                        TokenType.PERCENT -> numericRem(l, r, bin.pos)
                        TokenType.EQ -> LiteralExpr(numericEq(l, r), bin.pos)
                        TokenType.NE -> LiteralExpr(!numericEq(l, r), bin.pos)
                        TokenType.LT -> LiteralExpr(numericCompare(l, r) < 0, bin.pos)
                        TokenType.LE -> LiteralExpr(numericCompare(l, r) <= 0, bin.pos)
                        TokenType.GT -> LiteralExpr(numericCompare(l, r) > 0, bin.pos)
                        TokenType.GE -> LiteralExpr(numericCompare(l, r) >= 0, bin.pos)
                        else -> BinaryExpr(leftF, bin.operator, rightF, bin.pos)
                    }
                } catch (e: ArithmeticException) {
                    return BinaryExpr(leftF, bin.operator, rightF, bin.pos)
                }
            }
        }

        return BinaryExpr(leftF, bin.operator, rightF, bin.pos)
    }

    /**
     * Numeric equality comparison with type coercion.
     *
     * @param a left value (Long or Double)
     * @param b right value (Long or Double)
     * @return true if values are numerically equal
     */
    private fun numericEq(a: Any, b: Any): Boolean {
        val (ad, bd) = toDoublePair(a, b)
        return ad == bd
    }

    /**
     * Numeric comparison with conversion to Double.
     *
     * @param a left value (Long or Double)
     * @param b right value (Long or Double)
     * @return negative, zero, or positive as in [Double.compareTo]
     */
    private fun numericCompare(a: Any, b: Any): Int {
        val (ad, bd) = toDoublePair(a, b)
        return ad.compareTo(bd)
    }

    /**
     * Addition returning a [LiteralExpr] of the appropriate type.
     *
     * @param a left operand
     * @param b right operand
     * @param pos result position
     * @return [LiteralExpr] with Long if both are Long, otherwise Double
     */
    private fun numericPlus(a: Any, b: Any, pos: SourcePos): LiteralExpr {
        return when {
            a is Long && b is Long -> LiteralExpr(a + b, pos)
            else -> LiteralExpr(toDouble(a) + toDouble(b), pos)
        }
    }

    /**
     * Subtraction returning a [LiteralExpr] of the appropriate type.
     *
     * @param a left operand
     * @param b right operand
     * @param pos result position
     * @return [LiteralExpr] with Long if both are Long, otherwise Double
     */
    private fun numericMinus(a: Any, b: Any, pos: SourcePos): LiteralExpr {
        return when {
            a is Long && b is Long -> LiteralExpr(a - b, pos)
            else -> LiteralExpr(toDouble(a) - toDouble(b), pos)
        }
    }

    /**
     * Multiplication returning a [LiteralExpr] of the appropriate type.
     *
     * @param a left operand
     * @param b right operand
     * @param pos result position
     * @return [LiteralExpr] with Long if both are Long, otherwise Double
     */
    private fun numericMul(a: Any, b: Any, pos: SourcePos): LiteralExpr {
        return when {
            a is Long && b is Long -> LiteralExpr(a * b, pos)
            else -> LiteralExpr(toDouble(a) * toDouble(b), pos)
        }
    }

    /**
     * Division with protection against division by zero.
     *
     * @param a left operand
     * @param b right operand
     * @param pos result position
     * @return [LiteralExpr] with Long when both are Long and divisible without remainder,
     * ```
     *         otherwise Double
     * @throws ArithmeticException
     * ```
     * if division by zero occurs
     */
    private fun numericDiv(a: Any, b: Any, pos: SourcePos): LiteralExpr {
        if (isZero(b)) throw ArithmeticException("division by zero")
        return when {
            a is Long && b is Long -> {
                if (a % b == 0L) LiteralExpr(a / b, pos)
                else LiteralExpr(a.toDouble() / b.toDouble(), pos)
            }
            else -> LiteralExpr(toDouble(a) / toDouble(b), pos)
        }
    }

    /**
     * Modulo operation with protection against division by zero.
     *
     * @param a left operand
     * @param b right operand
     * @param pos result position
     * @return [LiteralExpr] with Long if both are Long, otherwise Double
     * @throws ArithmeticException if division by zero occurs
     */
    private fun numericRem(a: Any, b: Any, pos: SourcePos): LiteralExpr {
        if (isZero(b)) throw ArithmeticException("modulo by zero")
        return when {
            a is Long && b is Long -> LiteralExpr(a % b, pos)
            else -> LiteralExpr(toDouble(a) % toDouble(b), pos)
        }
    }

    /**
     * Converts a pair of values to Double.
     *
     * @param a left value
     * @param b right value
     * @return pair of Double values
     */
    private fun toDoublePair(a: Any, b: Any): Pair<Double, Double> = Pair(toDouble(a), toDouble(b))

    /**
     * Converts a Long/Double value to Double.
     *
     * @param x input value
     * @return value as Double
     * @throws IllegalArgumentException if the value is not numeric
     */
    private fun toDouble(x: Any): Double =
            when (x) {
                is Long -> x.toDouble()
                is Double -> x
                else -> throw IllegalArgumentException("expected numeric literal")
            }

    /**
     * Checks whether a value is zero.
     *
     * @param x input value
     * @return true for 0L or 0.0, false otherwise
     */
    private fun isZero(x: Any): Boolean =
            when (x) {
                is Long -> x == 0L
                is Double -> x == 0.0
                else -> false
            }
}
