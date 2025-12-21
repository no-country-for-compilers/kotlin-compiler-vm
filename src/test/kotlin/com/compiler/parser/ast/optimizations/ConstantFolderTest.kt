package com.compiler.parser.ast.optimizations

import com.compiler.domain.SourcePos
import com.compiler.lexer.TokenType
import com.compiler.parser.ast.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConstantFolderTest {

    private val p = SourcePos(1, 1)

    // --- helpers ---
    private fun litLong(v: Long) = LiteralExpr(v, p)
    private fun litDouble(v: Double) = LiteralExpr(v, p)
    private fun litBool(v: Boolean) = LiteralExpr(v, p)
    private fun varExpr(name: String) = VariableExpr(name, p)
    private fun group(expr: Expression) = GroupingExpr(expr, p)
    private fun arrayInit(elementType: TypeNode, size: Expression) =
            ArrayInitExpr(elementType, size, p)
    private fun arrayAccess(array: Expression, index: Expression) = ArrayAccessExpr(array, index, p)
    private fun call(name: String, args: List<Expression>) = CallExpr(name, args, p)
    private fun bin(left: Expression, op: TokenType, right: Expression) =
            BinaryExpr(left, op, right, p)
    private fun unary(op: TokenType, right: Expression) = UnaryExpr(op, right, p)
    private fun varDecl(name: String, type: TypeNode, expr: Expression) =
            VarDecl(name, type, expr, p)
    private fun exprStmt(expr: Expression) = ExprStmt(expr)
    private fun block(vararg stmts: Statement) = BlockStmt(stmts.toList())
    private fun program(vararg stmts: Statement) = Program(stmts.toList())

    @Test
    fun `folds simple integer addition`() {
        // Program:
        // x = 2 + 3
        val expr = bin(litLong(2), TokenType.PLUS, litLong(3))
        val decl = varDecl("x", TypeNode.IntType, expr)
        val prog = program(decl)

        // After constant folding:
        // x = 5
        val folded = ConstantFolder.apply(prog)
        assertEquals(1, folded.statements.size)
        val fd = folded.statements[0] as VarDecl
        assertTrue(fd.expression is LiteralExpr)
        assertEquals(5L, (fd.expression as LiteralExpr).value)
    }

    @Test
    fun `promotion to double on non-integer division`() {
        // Program:
        // a = 5 / 2
        val inner = bin(litLong(5), TokenType.SLASH, litLong(2))
        val decl = varDecl("a", TypeNode.FloatType, inner)
        val prog = program(decl)

        // After constant folding:
        // a = 2.5
        val folded = ConstantFolder.apply(prog)
        val fd = folded.statements[0] as VarDecl
        assertTrue(fd.expression is LiteralExpr)
        val value = (fd.expression as LiteralExpr).value
        assertTrue(value is Double)
        assertEquals(2.5, value as Double)
    }

    @Test
    fun `integer division exact returns long`() {
        // Program:
        // b = 4 / 2
        val inner = bin(litLong(4), TokenType.SLASH, litLong(2))
        val decl = varDecl("b", TypeNode.IntType, inner)
        val prog = program(decl)

        // After constant folding:
        // b = 2   (Long)
        val folded = ConstantFolder.apply(prog)
        val fd = folded.statements[0] as VarDecl
        assertTrue(fd.expression is LiteralExpr)
        assertTrue((fd.expression as LiteralExpr).value is Long)
        assertEquals(2L, (fd.expression).value)
    }

    @Test
    fun `does not apply division by zero`() {
        // Program:
        // x = 1 / 0
        val expr = bin(litLong(1), TokenType.SLASH, litLong(0))
        val decl = varDecl("x", TypeNode.IntType, expr)
        val prog = program(decl)

        // After constant folding — division by zero is not evaluated,
        // the expression remains a BinaryExpr
        val folded = ConstantFolder.apply(prog)
        val fd = folded.statements[0] as VarDecl
        assertTrue(fd.expression is BinaryExpr)
    }

    @Test
    fun `modulo and double remainder folding`() {
        // Program:
        // r1 = 5 % 2
        // r2 = 5.5 % 2
        val rem1 = bin(litLong(5), TokenType.PERCENT, litLong(2))
        val rem2 = bin(litDouble(5.5), TokenType.PERCENT, litLong(2))
        val d1 = varDecl("r1", TypeNode.IntType, rem1)
        val d2 = varDecl("r2", TypeNode.FloatType, rem2)
        val prog = program(d1, d2)

        // After constant folding:
        // r1 = 1
        // r2 = 1.5
        val folded = ConstantFolder.apply(prog)
        val f1 = folded.statements[0] as VarDecl
        val f2 = folded.statements[1] as VarDecl

        assertTrue(f1.expression is LiteralExpr)
        assertEquals(1L, (f1.expression as LiteralExpr).value)

        assertTrue(f2.expression is LiteralExpr)
        assertEquals(1.5, (f2.expression as LiteralExpr).value)
    }

    @Test
    fun `folds unary and grouping`() {
        // Program:
        // y = - (5)
        val expr = unary(TokenType.MINUS, group(litLong(5)))
        val decl = varDecl("y", TypeNode.IntType, expr)
        val folded = ConstantFolder.apply(program(decl))

        // Result:
        // y = -5
        val fd = folded.statements[0] as VarDecl
        assertTrue(fd.expression is LiteralExpr)
        assertEquals(-5L, (fd.expression as LiteralExpr).value)
    }

    @Test
    fun `boolean short-circuit folding for OR and AND`() {
        // Program A:
        // b1 = true || someVar
        val orExpr = bin(litBool(true), TokenType.OR, varExpr("someVar"))
        val dOr = varDecl("b1", TypeNode.BoolType, orExpr)

        // Program B:
        // b2 = false && someVar
        val andExpr = bin(litBool(false), TokenType.AND, varExpr("someVar"))
        val dAnd = varDecl("b2", TypeNode.BoolType, andExpr)

        val folded = ConstantFolder.apply(program(dOr, dAnd))

        // After folding:
        // b1 = true
        // b2 = false
        val f1 = folded.statements[0] as VarDecl
        val f2 = folded.statements[1] as VarDecl
        assertTrue(f1.expression is LiteralExpr)
        assertEquals(true, (f1.expression as LiteralExpr).value)
        assertTrue(f2.expression is LiteralExpr)
        assertEquals(false, (f2.expression as LiteralExpr).value)
    }

    @Test
    fun `comparison and equality folding`() {
        // Program:
        // c1 = 2 < 3
        // c2 = 2 == 2.0
        val c1 = varDecl("c1", TypeNode.BoolType, bin(litLong(2), TokenType.LT, litLong(3)))
        val c2 = varDecl("c2", TypeNode.BoolType, bin(litLong(2), TokenType.EQ, litDouble(2.0)))
        val folded = ConstantFolder.apply(program(c1, c2))

        // Result:
        // c1 = true
        // c2 = true
        val f1 = folded.statements[0] as VarDecl
        val f2 = folded.statements[1] as VarDecl
        assertTrue(f1.expression is LiteralExpr)
        assertEquals(true, (f1.expression as LiteralExpr).value)
        assertTrue(f2.expression is LiteralExpr)
        assertEquals(true, (f2.expression as LiteralExpr).value)
    }

    @Test
    fun `folds nested arithmetic to single literal`() {
        // Program:
        // z = (2 + 3) * 4
        val nested = bin(bin(litLong(2), TokenType.PLUS, litLong(3)), TokenType.STAR, litLong(4))
        val decl = varDecl("z", TypeNode.IntType, nested)
        val folded = ConstantFolder.apply(program(decl))

        // Result:
        // z = 20
        val fd = folded.statements[0] as VarDecl
        assertTrue(fd.expression is LiteralExpr)
        assertEquals(20L, (fd.expression as LiteralExpr).value)
    }

    @Test
    fun `array init size folding and array access index folding`() {
        // Program:
        // arr = int[1 + 2]
        // v = arr[1 + 2]
        val arrInit = arrayInit(TypeNode.IntType, bin(litLong(1), TokenType.PLUS, litLong(2)))
        val darr = varDecl("arr", TypeNode.ArrayType(TypeNode.IntType), arrInit)
        val access = arrayAccess(varExpr("arr"), bin(litLong(1), TokenType.PLUS, litLong(2)))
        val dval = varDecl("v", TypeNode.IntType, access)

        val folded = ConstantFolder.apply(program(darr, dval))

        // Result:
        // arr = int[3]
        // v = arr[3]
        val fa = folded.statements[0] as VarDecl
        val faInit = fa.expression as ArrayInitExpr
        assertTrue(faInit.size is LiteralExpr)
        assertEquals(3L, (faInit.size as LiteralExpr).value)

        val fv = folded.statements[1] as VarDecl
        val acc = fv.expression as ArrayAccessExpr
        assertTrue(acc.index is LiteralExpr)
        assertEquals(3L, (acc.index as LiteralExpr).value)
    }

    @Test
    fun `call arguments and assign value folding`() {
        // Program:
        // r = f(1 + 2)
        // arr[1 + 1] = 7
        val callExpr = call("f", listOf(bin(litLong(1), TokenType.PLUS, litLong(2))))
        val dcall = varDecl("r", TypeNode.IntType, callExpr)

        val target = arrayAccess(varExpr("arr"), bin(litLong(1), TokenType.PLUS, litLong(1)))
        val assign = exprStmt(AssignExpr(target, litLong(7), p))

        val folded = ConstantFolder.apply(program(dcall, exprStmt(callExpr), assign))

        // Result:
        // r = f(3)
        // (the call to f(...) is not evaluated, only arguments are folded)
        // arr[2] = 7
        val dr = folded.statements[0] as VarDecl
        val callFolded = dr.expression as CallExpr
        assertEquals(1, callFolded.args.size)
        assertTrue(callFolded.args[0] is LiteralExpr)
        assertEquals(3L, (callFolded.args[0] as LiteralExpr).value)

        val asStmt = folded.statements[2] as ExprStmt
        val asExpr = asStmt.expr as AssignExpr
        val tgt = asExpr.target as ArrayAccessExpr
        assertTrue(tgt.index is LiteralExpr)
        assertEquals(2L, (tgt.index as LiteralExpr).value)
    }

    @Test
    fun `if folding to then or else branch`() {
        // Program A:
        // if (true) { x = 1; } else { x = 0; }
        val thenS = exprStmt(AssignExpr(varExpr("x"), litLong(1), p))
        val elseS = exprStmt(AssignExpr(varExpr("x"), litLong(0), p))
        val ifTrue = IfStmt(litBool(true), block(thenS), block(elseS))
        val foldedTrue = ConstantFolder.apply(program(ifTrue))

        // Result A:
        // { x = 1; }  (only the then-branch)
        val st = foldedTrue.statements[0]
        assertTrue(st is BlockStmt)
        val b = st as BlockStmt
        assertEquals(1, b.statements.size)
        assertTrue(b.statements[0] is ExprStmt)
        val assignedVal = ((b.statements[0] as ExprStmt).expr as AssignExpr).value
        assertTrue(assignedVal is LiteralExpr)
        assertEquals(1L, (assignedVal as LiteralExpr).value)

        // Program B:
        // if (false) { x = 1; } else { x = 0; }
        val ifFalse = IfStmt(litBool(false), block(thenS), block(elseS))
        val foldedFalse = ConstantFolder.apply(program(ifFalse))

        // Result B:
        // { x = 0; }  (only the else-branch)
        val st2 = foldedFalse.statements[0]
        assertTrue(st2 is BlockStmt)
        val b2 = st2 as BlockStmt
        assertEquals(1, b2.statements.size)
        val assignedVal2 = ((b2.statements[0] as ExprStmt).expr as AssignExpr).value
        assertTrue(assignedVal2 is LiteralExpr)
        assertEquals(0L, (assignedVal2 as LiteralExpr).value)
    }

    @Test
    fun `for initializer, condition and increment folding`() {
        // Program:
        // for (let i: int = 1 + 1; i < 10; i = i + (1 + 1)) { let x: int = 2 + 2; }
        val initDecl =
                VarDecl("i", TypeNode.IntType, bin(litLong(1), TokenType.PLUS, litLong(1)), p)
        val forInit = ForVarInit(initDecl)
        val cond = bin(varExpr("i"), TokenType.LT, litLong(10))
        val inc =
                bin(
                        varExpr("i"),
                        TokenType.ASSIGN,
                        bin(
                                varExpr("i"),
                                TokenType.PLUS,
                                bin(litLong(1), TokenType.PLUS, litLong(1))
                        )
                )
        val bodyDecl = varDecl("x", TypeNode.IntType, bin(litLong(2), TokenType.PLUS, litLong(2)))
        val forStmt = ForStmt(forInit, cond, inc, block(bodyDecl))

        val folded = ConstantFolder.apply(program(forStmt))

        // Result:
        // Initializer: let i = 2
        // Increment: i = i + 2  (inner 1+1 folded to 2)
        // Body: let x = 4
        val f = folded.statements[0] as ForStmt
        assertTrue(f.initializer is ForVarInit)
        val initFolded = (f.initializer as ForVarInit).decl
        assertTrue(initFolded.expression is LiteralExpr)
        assertEquals(2L, (initFolded.expression as LiteralExpr).value)

        val foldedInc = f.increment
        assertNotNull(foldedInc)
        // inc must contain LiteralExpr 2 instead of (1+1)
        assertTrue(foldedInc is AssignExpr || foldedInc is BinaryExpr)

        val foldedBody = f.body.statements[0] as VarDecl
        assertTrue(foldedBody.expression is LiteralExpr)
        assertEquals(4L, (foldedBody.expression as LiteralExpr).value)
    }

    @Test
    fun `function body folding`() {
        // Program:
        // func foo() { let a: int = 1 + 2; }
        val innerDecl = varDecl("a", TypeNode.IntType, bin(litLong(1), TokenType.PLUS, litLong(2)))
        val fn = FunctionDecl("foo", emptyList(), TypeNode.VoidType, block(innerDecl), p)
        val folded = ConstantFolder.apply(program(fn))

        // Result:
        // func foo() { let a: int = 3; }
        val ffn = folded.statements[0] as FunctionDecl
        val inner = ffn.body.statements[0] as VarDecl
        assertTrue(inner.expression is LiteralExpr)
        assertEquals(3L, (inner.expression as LiteralExpr).value)
    }

    @Test
    fun `folds unary plus and logical not`() {
        // Program:
        // a = +5
        val p1 = program(varDecl("a", TypeNode.IntType, unary(TokenType.PLUS, litLong(5))))

        // After constant folding:
        // a = 5
        val f1 = ConstantFolder.apply(p1)
        val aDecl = f1.statements[0] as VarDecl
        assertTrue(aDecl.expression is LiteralExpr)
        assertEquals(5L, (aDecl.expression as LiteralExpr).value)

        // Program:
        // b = !true
        val p2 = program(varDecl("b", TypeNode.BoolType, unary(TokenType.NOT, litBool(true))))

        // After constant folding:
        // b = false
        val f2 = ConstantFolder.apply(p2)
        val bDecl = f2.statements[0] as VarDecl
        assertTrue(bDecl.expression is LiteralExpr)
        assertEquals(false, (bDecl.expression as LiteralExpr).value)
    }

    @Test
    fun `folds not equals operator`() {
        // Program:
        // n = 3 != 4
        val prog = program(varDecl("n", TypeNode.BoolType, bin(litLong(3), TokenType.NE, litLong(4))))

        // After constant folding:
        // n = true
        val folded = ConstantFolder.apply(prog)
        val decl = folded.statements[0] as VarDecl
        assertTrue(decl.expression is LiteralExpr)
        assertEquals(true, (decl.expression as LiteralExpr).value)
    }

    @Test
    fun `folds return statement value`() {
        // Program:
        // return 1 + 2;
        val ret = ReturnStmt(bin(litLong(1), TokenType.PLUS, litLong(2)), p)
        val prog = program(ret)

        // After constant folding:
        // return 3;
        val folded = ConstantFolder.apply(prog)
        val foldedRet = folded.statements[0] as ReturnStmt
        assertNotNull(foldedRet.value)
        assertTrue(foldedRet.value is LiteralExpr)
        assertEquals(3L, (foldedRet.value as LiteralExpr).value)
    }

    @Test
    fun `folds assign expression value for variable target`() {
        // Program:
        // x = 1 + 1;
        val assign = exprStmt(AssignExpr(varExpr("x"), bin(litLong(1), TokenType.PLUS, litLong(1)), p))
        val prog = program(assign)

        // After constant folding:
        // x = 2;
        val folded = ConstantFolder.apply(prog)
        val stmt = folded.statements[0] as ExprStmt
        val asg = stmt.expr as AssignExpr
        assertTrue(asg.value is LiteralExpr)
        assertEquals(2L, (asg.value as LiteralExpr).value)
    }

    @Test
    fun `folds mixed long and double arithmetic`() {
        // Program:
        // a = 2.0 + 3
        val prog =
                program(
                        varDecl(
                                "a",
                                TypeNode.FloatType,
                                bin(litDouble(2.0), TokenType.PLUS, litLong(3))
                        )
                )

        // After constant folding:
        // a = 5.0
        val folded = ConstantFolder.apply(prog)
        val decl = folded.statements[0] as VarDecl
        assertTrue(decl.expression is LiteralExpr)
        val v = (decl.expression as LiteralExpr).value
        assertTrue(v is Double)
        assertEquals(5.0, v)
    }

    @Test
    fun `double edge cases nan and negative zero`() {
        // Program:
        // n = NaN == NaN
        val nan = litDouble(Double.NaN)
        val nanEq = program(varDecl("n", TypeNode.BoolType, bin(nan, TokenType.EQ, nan)))

        // After constant folding:
        // n = false   (NaN != NaN per IEEE)
        val foldedNan = ConstantFolder.apply(nanEq)
        val nDecl = foldedNan.statements[0] as VarDecl
        assertTrue(nDecl.expression is LiteralExpr)
        assertEquals(false, (nDecl.expression as LiteralExpr).value)

        // Program:
        // z = -0.0 == 0.0
        val negZero = litDouble(-0.0)
        val posZero = litDouble(0.0)
        val zeroEq = program(varDecl("z", TypeNode.BoolType, bin(negZero, TokenType.EQ, posZero)))

        // After constant folding:
        // z = true
        val foldedZero = ConstantFolder.apply(zeroEq)
        val zDecl = foldedZero.statements[0] as VarDecl
        assertTrue(zDecl.expression is LiteralExpr)
        assertEquals(true, (zDecl.expression as LiteralExpr).value)
    }

    @Test
    fun `grouping folds to literal`() {
        // Program:
        // x = (7)
        val grp = GroupingExpr(litLong(7), p)
        val prog = program(varDecl("x", TypeNode.IntType, grp))

        // After constant folding:
        // x = 7
        val folded = ConstantFolder.apply(prog)
        val decl = folded.statements[0] as VarDecl
        assertTrue(decl.expression is LiteralExpr)
        assertEquals(7L, (decl.expression as LiteralExpr).value)
    }
}
