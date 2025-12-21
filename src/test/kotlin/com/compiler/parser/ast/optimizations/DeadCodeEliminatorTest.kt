package com.compiler.parser.ast.optimizations

import com.compiler.domain.SourcePos
import com.compiler.lexer.TokenType
import com.compiler.parser.ast.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DeadCodeEliminatorTest {

    private val p = SourcePos(1, 1)

    // --- helpers ---
    private fun litLong(v: Long) = LiteralExpr(v, p)
    private fun litBool(v: Boolean) = LiteralExpr(v, p)
    private fun varExpr(name: String) = VariableExpr(name, p)
    private fun call(name: String, args: List<Expression>) = CallExpr(name, args, p)
    private fun bin(l: Expression, op: TokenType, r: Expression) = BinaryExpr(l, op, r, p)
    private fun varDecl(name: String, type: TypeNode, expr: Expression) =
            VarDecl(name, type, expr, p)
    private fun exprStmt(expr: Expression) = ExprStmt(expr)
    private fun block(vararg stmts: Statement) = BlockStmt(stmts.toList())
    private fun program(vararg stmts: Statement) = Program(stmts.toList())

    @Test
    fun `removes unreachable statements after return`() {
        // Программа:
        // {
        //   x = 1;
        //   return 2;
        //   y = 3;
        // }
        val s1 = exprStmt(AssignExpr(varExpr("x"), litLong(1), p))
        val ret = ReturnStmt(litLong(2), p)
        val s2 = exprStmt(AssignExpr(varExpr("y"), litLong(3), p))
        val prog = program(block(s1, ret, s2))

        // После dead code elimination:
        // {
        //   x = 1;
        //   return 2;
        // }
        val opt = DeadCodeEliminator.eliminate(prog)
        val blk = opt.statements[0] as BlockStmt
        assertEquals(2, blk.statements.size)
        assertTrue(blk.statements[1] is ReturnStmt)
    }

    @Test
    fun `removes unused pure variable declaration`() {
        // Программа:
        // let a: int = 1;
        val prog = program(varDecl("a", TypeNode.IntType, litLong(1)))

        // После dead code elimination:
        // (пусто)
        val opt = DeadCodeEliminator.eliminate(prog)
        assertTrue(opt.statements.isEmpty())
    }

    @Test
    fun `keeps side effect of unused variable initializer`() {
        // Программа:
        // let a: int = f();
        val prog = program(varDecl("a", TypeNode.IntType, call("f", emptyList())))

        // После dead code elimination:
        // f();
        val opt = DeadCodeEliminator.eliminate(prog)
        assertEquals(1, opt.statements.size)
        assertTrue(opt.statements[0] is ExprStmt)
        assertTrue((opt.statements[0] as ExprStmt).expr is CallExpr)
    }

    @Test
    fun `keeps variable declaration when variable is used`() {
        // Программа:
        // let a: int = 1;
        // a;
        val prog = program(varDecl("a", TypeNode.IntType, litLong(1)), exprStmt(varExpr("a")))

        // После dead code elimination:
        // let a: int = 1;
        // a;
        val opt = DeadCodeEliminator.eliminate(prog)
        assertEquals(2, opt.statements.size)
        assertTrue(opt.statements[0] is VarDecl)
    }

    @Test
    fun `removes empty if with pure condition`() {
        // Программа:
        // if (true) { } else { }
        val prog = program(IfStmt(litBool(true), block(), block()))

        // После dead code elimination:
        // (пусто)
        val opt = DeadCodeEliminator.eliminate(prog)
        assertTrue(opt.statements.isEmpty())
    }

    @Test
    fun `keeps if when condition has side effects`() {
        // Программа:
        // if (f()) { } else { }
        val prog = program(IfStmt(call("f", emptyList()), block(), block()))

        // После dead code elimination:
        // if (f()) { } else { }
        val opt = DeadCodeEliminator.eliminate(prog)
        assertEquals(1, opt.statements.size)
        assertTrue(opt.statements[0] is IfStmt)
    }

    @Test
    fun `removes empty for without side effects`() {
        // Программа:
        // for (; true; ) { }
        val prog = program(ForStmt(ForNoInit, litBool(true), null, block()))

        // После dead code elimination:
        // (пусто)
        val opt = DeadCodeEliminator.eliminate(prog)
        assertTrue(opt.statements.isEmpty())
    }

    @Test
    fun `keeps for with side effect in initializer`() {
        // Программа:
        // for (f(); ; ) { }
        val prog = program(ForStmt(ForExprInit(call("f", emptyList())), null, null, block()))

        // После dead code elimination:
        // for (f(); ; ) { }
        val opt = DeadCodeEliminator.eliminate(prog)
        assertEquals(1, opt.statements.size)
        assertTrue(opt.statements[0] is ForStmt)
    }

    @Test
    fun `optimizes function body but keeps function`() {
        // Программа:
        // func foo() { let a: int = 1; }
        val fn =
                FunctionDecl(
                        "foo",
                        emptyList(),
                        TypeNode.VoidType,
                        block(varDecl("a", TypeNode.IntType, litLong(1))),
                        p
                )

        // После dead code elimination:
        // func foo() { }
        val opt = DeadCodeEliminator.eliminate(program(fn))
        val f = opt.statements[0] as FunctionDecl
        assertTrue(f.body.statements.isEmpty())
    }

    @Test
    fun `removes pure expression statement`() {
        // Программа:
        // 1 + 2;
        val prog = program(exprStmt(bin(litLong(1), TokenType.PLUS, litLong(2))))

        // После dead code elimination:
        // (пусто)
        val opt = DeadCodeEliminator.eliminate(prog)
        assertTrue(opt.statements.isEmpty())
    }

    @Test
    fun `complex scenario with return and side effects`() {
        // Программа:
        // let a = 1;
        // let b = f();
        // b;
        // return 0;
        // let c = 3;
        val prog =
                program(
                        varDecl("a", TypeNode.IntType, litLong(1)),
                        varDecl("b", TypeNode.IntType, call("f", emptyList())),
                        exprStmt(varExpr("b")),
                        ReturnStmt(litLong(0), p),
                        varDecl("c", TypeNode.IntType, litLong(3))
                )

        // После dead code elimination:
        // let b = f();
        // b;
        // return 0;
        val opt = DeadCodeEliminator.eliminate(prog)
        assertEquals(3, opt.statements.size)
        assertTrue(opt.statements[2] is ReturnStmt)
    }
}