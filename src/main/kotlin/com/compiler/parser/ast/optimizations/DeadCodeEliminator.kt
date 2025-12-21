package com.compiler.parser.ast.optimizations

import com.compiler.parser.ast.*

/**
 * Dead Code Elimination (DCE) processor.
 *
 * Algorithm:
 * 1. Removes unreachable code located after `return` inside a block.
 * 2. Performs conservative local DCE inside blocks — backward liveness analysis to remove
 * ```
 *    unused declarations and pure expressions. Side effects are preserved.
 * ```
 * Side-effect rules (conservative):
 * - considered side-effectful: calls (CallExpr), assignments (AssignExpr), array creation
 * (ArrayInitExpr), array access (ArrayAccessExpr).
 * - if there are no side effects, unused VarDecls are removed; if the initializer has side effects,
 * VarDecl is transformed into ExprStmt(init).
 */
object DeadCodeEliminator : AstOptimization {

    override val name = "Dead Code Elimination"

    /** Applies the optimization to the program root and returns a new Program. */
    override fun apply(program: Program): Program {
        val noUnreach = program.statements.mapNotNull { removeUnreachableTopLevel(it) }
        val optimizedTop = processBlockStatements(noUnreach)
        return Program(optimizedTop)
    }

    /** Removes unreachable code inside a top-level statement or returns the processed statement. */
    private fun removeUnreachableTopLevel(stmt: Statement): Statement? =
            when (stmt) {
                is BlockStmt -> removeUnreachableBlock(stmt)
                is FunctionDecl -> stmt.copy(body = removeUnreachableBlock(stmt.body))
                is IfStmt -> {
                    val thenB = removeUnreachableBlock(stmt.thenBranch)
                    val elseB = stmt.elseBranch?.let { removeUnreachableBlock(it) }
                    IfStmt(stmt.condition, thenB, elseB)
                }
                is ForStmt -> {
                    val body = removeUnreachableBlock(stmt.body)
                    ForStmt(stmt.initializer, stmt.condition, stmt.increment, body)
                }
                else -> stmt
            }

    /** Removes statements following the first ReturnStmt inside a block. */
    private fun removeUnreachableBlock(block: BlockStmt): BlockStmt {
        val out = ArrayList<Statement>()
        for (s in block.statements) {
            when (s) {
                is ReturnStmt -> {
                    out.add(s)
                    break
                }
                else -> removeUnreachableTopLevel(s)?.let { out.add(it) }
            }
        }
        return BlockStmt(out)
    }

    /**
     * Processes a list of statements (as a block) and returns a new list with dead code removed
     * within this block.
     */
    private fun processBlockStatements(stmts: List<Statement>): List<Statement> {
        val result = ArrayList<Statement>()
        val used = mutableSetOf<String>()

        for (stmt in stmts.asReversed()) {
            when (stmt) {
                is VarDecl -> {
                    val initUsed = collectUsedVars(stmt.expression)
                    if (stmt.identifier in used) {
                        used.remove(stmt.identifier)
                        used.addAll(initUsed)
                        result.add(stmt.copy(expression = processExpression(stmt.expression)))
                    } else {
                        if (isSideEffectful(stmt.expression)) {
                            used.addAll(initUsed)
                            result.add(ExprStmt(processExpression(stmt.expression)))
                        }
                    }
                }
                is ExprStmt -> {
                    val readVars = collectUsedVars(stmt.expr)
                    if (readVars.isNotEmpty() || isSideEffectful(stmt.expr)) {
                        used.addAll(readVars)
                        result.add(ExprStmt(processExpression(stmt.expr)))
                    }
                }
                is ReturnStmt -> {
                    stmt.value?.let { used.addAll(collectUsedVars(it)) }
                    result.add(ReturnStmt(stmt.value?.let { processExpression(it) }, stmt.pos))
                }
                is IfStmt -> {
                    val thenOptim = processBlockStatements(stmt.thenBranch.statements)
                    val thenBefore = computeUsedBeforeForBlock(thenOptim, used)
                    val elseOptim = stmt.elseBranch?.let { processBlockStatements(it.statements) }
                    val elseBefore =
                            elseOptim?.let { computeUsedBeforeForBlock(it, used) } ?: used.toSet()
                    val condUsed = collectUsedVars(stmt.condition)

                    val before = HashSet<String>()
                    before.addAll(thenBefore)
                    before.addAll(elseBefore)
                    before.addAll(condUsed)

                    used.clear()
                    used.addAll(before)

                    val condSide = isSideEffectful(stmt.condition)
                    val thenEmpty = thenOptim.isEmpty()
                    val elseEmpty = elseOptim == null || elseOptim.isEmpty()
                    if (!(thenEmpty && elseEmpty && !condSide)) {
                        val thenBlock = BlockStmt(thenOptim)
                        val elseBlock = elseOptim?.let { BlockStmt(it) }
                        result.add(IfStmt(processExpression(stmt.condition), thenBlock, elseBlock))
                    }
                }
                is BlockStmt -> {
                    val inner = processBlockStatements(stmt.statements)
                    val before = computeUsedBeforeForBlock(inner, used)
                    used.addAll(before)
                    if (inner.isNotEmpty()) result.add(BlockStmt(inner))
                }
                is FunctionDecl -> {
                    val bodyOptim = processBlockStatements(stmt.body.statements)
                    result.add(stmt.copy(body = BlockStmt(bodyOptim)))
                }
                is ForStmt -> {
                    val initOpt =
                            when (val init = stmt.initializer) {
                                is ForVarInit ->
                                        ForVarInit(
                                                init.decl.copy(
                                                        expression =
                                                                processExpression(
                                                                        init.decl.expression
                                                                )
                                                )
                                        )
                                is ForExprInit -> ForExprInit(processExpression(init.expr))
                                ForNoInit -> ForNoInit
                            }
                    val condOpt = stmt.condition?.let { processExpression(it) }
                    val incOpt = stmt.increment?.let { processExpression(it) }
                    val bodyOpt = processBlockStatements(stmt.body.statements)
                    val bodyBefore = computeUsedBeforeForBlock(bodyOpt, used)
                    val before = HashSet<String>()
                    before.addAll(bodyBefore)
                    condOpt?.let { before.addAll(collectUsedVars(it)) }
                    incOpt?.let { before.addAll(collectUsedVars(it)) }
                    when (initOpt) {
                        is ForVarInit -> before.addAll(collectUsedVars(initOpt.decl.expression))
                        is ForExprInit -> before.addAll(collectUsedVars(initOpt.expr))
                        else -> {}
                    }
                    used.clear()
                    used.addAll(before)

                    val hasSide =
                            when {
                                (condOpt != null && isSideEffectful(condOpt)) -> true
                                (incOpt != null && isSideEffectful(incOpt)) -> true
                                (initOpt is ForVarInit &&
                                        isSideEffectful(initOpt.decl.expression)) -> true
                                (initOpt is ForExprInit && isSideEffectful(initOpt.expr)) -> true
                                else -> false
                            }
                    if (!(bodyOpt.isEmpty() && !hasSide)) {
                        result.add(ForStmt(initOpt, condOpt, incOpt, BlockStmt(bodyOpt)))
                    }
                }
            }
        }

        return result.asReversed()
    }

    /**
     * Recursively processes an expression: applies optimization to sub-expressions and returns a
     * new expression.
     */
    private fun processExpression(expr: Expression): Expression =
            when (expr) {
                is LiteralExpr -> expr
                is VariableExpr -> expr
                is GroupingExpr -> {
                    val inner = processExpression(expr.expression)
                    if (inner is LiteralExpr) inner else GroupingExpr(inner, expr.pos)
                }
                is ArrayInitExpr ->
                        ArrayInitExpr(expr.elementType, processExpression(expr.size), expr.pos)
                is ArrayAccessExpr ->
                        ArrayAccessExpr(
                                processExpression(expr.array),
                                processExpression(expr.index),
                                expr.pos
                        )
                is CallExpr ->
                        CallExpr(expr.name, expr.args.map { processExpression(it) }, expr.pos)
                is AssignExpr -> {
                    val tgt =
                            when (val t = expr.target) {
                                is VariableExpr -> t
                                is ArrayAccessExpr ->
                                        ArrayAccessExpr(
                                                processExpression(t.array),
                                                processExpression(t.index),
                                                t.pos
                                        )
                            }
                    AssignExpr(tgt, processExpression(expr.value), expr.pos)
                }
                is UnaryExpr -> UnaryExpr(expr.operator, processExpression(expr.right), expr.pos)
                is BinaryExpr ->
                        BinaryExpr(
                                processExpression(expr.left),
                                expr.operator,
                                processExpression(expr.right),
                                expr.pos
                        )
            }

    /** Collects variable names read in an expression. */
    private fun collectUsedVars(expr: Expression): Set<String> {
        val used = HashSet<String>()
        fun walk(e: Expression) {
            when (e) {
                is VariableExpr -> used.add(e.name)
                is LiteralExpr -> {}
                is GroupingExpr -> walk(e.expression)
                is ArrayInitExpr -> walk(e.size)
                is ArrayAccessExpr -> {
                    walk(e.array)
                    walk(e.index)
                }
                is CallExpr -> {
                    e.args.forEach { walk(it) }
                }
                is AssignExpr -> {
                    when (val t = e.target) {
                        is ArrayAccessExpr -> {
                            walk(t.array)
                            walk(t.index)
                        }
                        is VariableExpr -> {}
                    }
                    walk(e.value)
                }
                is UnaryExpr -> walk(e.right)
                is BinaryExpr -> {
                    walk(e.left)
                    walk(e.right)
                }
            }
        }
        walk(expr)
        return used
    }

    /** Conservative check for side effects in an expression. */
    private fun isSideEffectful(expr: Expression): Boolean {
        var has = false
        fun walk(e: Expression) {
            if (has) return
            when (e) {
                is CallExpr -> has = true
                is AssignExpr -> has = true
                is ArrayInitExpr -> has = true
                is ArrayAccessExpr -> has = true
                is UnaryExpr -> walk(e.right)
                is BinaryExpr -> {
                    walk(e.left)
                    walk(e.right)
                }
                is GroupingExpr -> walk(e.expression)
                is LiteralExpr -> {}
                is VariableExpr -> {}
            }
        }
        walk(expr)
        return has
    }

    /** Collects variable names used in a statement (conservatively). */
    private fun collectUsedVarsFromStatement(stmt: Statement): Set<String> =
            when (stmt) {
                is ReturnStmt -> stmt.value?.let { collectUsedVars(it) } ?: emptySet()
                is ExprStmt -> collectUsedVars(stmt.expr)
                is VarDecl -> collectUsedVars(stmt.expression)
                is IfStmt -> {
                    val s = HashSet<String>()
                    s.addAll(collectUsedVars(stmt.condition))
                    s.addAll(
                            stmt.thenBranch.statements.flatMap { collectUsedVarsFromStatement(it) }
                    )
                    stmt.elseBranch?.let {
                        s.addAll(it.statements.flatMap { collectUsedVarsFromStatement(it) })
                    }
                    s
                }
                is ForStmt -> {
                    val s = HashSet<String>()
                    when (val init = stmt.initializer) {
                        is ForVarInit -> s.addAll(collectUsedVars(init.decl.expression))
                        is ForExprInit -> s.addAll(collectUsedVars(init.expr))
                        else -> {}
                    }
                    stmt.condition?.let { s.addAll(collectUsedVars(it)) }
                    stmt.increment?.let { s.addAll(collectUsedVars(it)) }
                    s.addAll(stmt.body.statements.flatMap { collectUsedVarsFromStatement(it) })
                    s
                }
                is BlockStmt -> stmt.statements.flatMap { collectUsedVarsFromStatement(it) }.toSet()
                is FunctionDecl ->
                        stmt.body.statements.flatMap { collectUsedVarsFromStatement(it) }.toSet()
            }

    /**
     * Computes the set of variables that must be live before entering a block, given that usedAfter
     * are live after the block.
     */
    private fun computeUsedBeforeForBlock(
            blockStmts: List<Statement>,
            usedAfter: Set<String>
    ): Set<String> {
        val used = HashSet(usedAfter)
        for (stmt in blockStmts.asReversed()) {
            when (stmt) {
                is VarDecl -> {
                    val initUsed = collectUsedVars(stmt.expression)
                    if (stmt.identifier in used) {
                        used.remove(stmt.identifier)
                        used.addAll(initUsed)
                    } else {
                        if (isSideEffectful(stmt.expression)) {
                            used.addAll(initUsed)
                        }
                    }
                }
                is ExprStmt -> {
                    val readVars = collectUsedVars(stmt.expr)
                    if (readVars.isNotEmpty() || isSideEffectful(stmt.expr)) {
                        used.addAll(readVars)
                    }
                }
                is ReturnStmt -> stmt.value?.let { used.addAll(collectUsedVars(it)) }
                is IfStmt -> {
                    val thenBefore = computeUsedBeforeForBlock(stmt.thenBranch.statements, used)
                    val elseBefore =
                            stmt.elseBranch?.let { computeUsedBeforeForBlock(it.statements, used) }
                                    ?: used
                    used.clear()
                    used.addAll(thenBefore)
                    used.addAll(elseBefore)
                    used.addAll(collectUsedVars(stmt.condition))
                }
                is BlockStmt -> used.addAll(computeUsedBeforeForBlock(stmt.statements, used))
                is ForStmt -> {
                    val bodyBefore = computeUsedBeforeForBlock(stmt.body.statements, used)
                    used.addAll(bodyBefore)
                    stmt.condition?.let { used.addAll(collectUsedVars(it)) }
                    stmt.increment?.let { used.addAll(collectUsedVars(it)) }
                    when (val init = stmt.initializer) {
                        is ForVarInit -> used.addAll(collectUsedVars(init.decl.expression))
                        is ForExprInit -> used.addAll(collectUsedVars(init.expr))
                        else -> {}
                    }
                }
                is FunctionDecl -> {}
            }
        }
        return used
    }
}
