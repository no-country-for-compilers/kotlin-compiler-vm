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
        // Program:
        // {
        //   x = 1;
        //   return 2;
        //   y = 3;
        // }
        val s1 = exprStmt(AssignExpr(varExpr("x"), litLong(1), p))
        val ret = ReturnStmt(litLong(2), p)
        val s2 = exprStmt(AssignExpr(varExpr("y"), litLong(3), p))
        val prog = program(block(s1, ret, s2))

        // After dead code elimination:
        // {
        //   x = 1;
        //   return 2;
        // }
        val opt = DeadCodeEliminator.apply(prog)
        val blk = opt.statements[0] as BlockStmt
        assertEquals(2, blk.statements.size)
        assertTrue(blk.statements[1] is ReturnStmt)
    }

    @Test
    fun `removes unused pure variable declaration`() {
        // Program:
        // let a: int = 1;
        val prog = program(varDecl("a", TypeNode.IntType, litLong(1)))

        // After dead code elimination:
        // (empty)
        val opt = DeadCodeEliminator.apply(prog)
        assertTrue(opt.statements.isEmpty())
    }

    @Test
    fun `keeps side effect of unused variable initializer`() {
        // Program:
        // let a: int = f();
        val prog = program(varDecl("a", TypeNode.IntType, call("f", emptyList())))

        // After dead code elimination:
        // f();
        val opt = DeadCodeEliminator.apply(prog)
        assertEquals(1, opt.statements.size)
        assertTrue(opt.statements[0] is ExprStmt)
        assertTrue((opt.statements[0] as ExprStmt).expr is CallExpr)
    }

    @Test
    fun `keeps variable declaration when variable is used`() {
        // Program:
        // let a: int = 1;
        // a;
        val prog = program(varDecl("a", TypeNode.IntType, litLong(1)), exprStmt(varExpr("a")))

        // After dead code elimination:
        // let a: int = 1;
        // a;
        val opt = DeadCodeEliminator.apply(prog)
        assertEquals(2, opt.statements.size)
        assertTrue(opt.statements[0] is VarDecl)
    }

    @Test
    fun `removes empty if with pure condition`() {
        // Program:
        // if (true) { } else { }
        val prog = program(IfStmt(litBool(true), block(), block()))

        // After dead code elimination:
        // (empty)
        val opt = DeadCodeEliminator.apply(prog)
        assertTrue(opt.statements.isEmpty())
    }

    @Test
    fun `keeps if when condition has side effects`() {
        // Program:
        // if (f()) { } else { }
        val prog = program(IfStmt(call("f", emptyList()), block(), block()))

        // After dead code elimination:
        // if (f()) { } else { }
        val opt = DeadCodeEliminator.apply(prog)
        assertEquals(1, opt.statements.size)
        assertTrue(opt.statements[0] is IfStmt)
    }

    @Test
    fun `removes empty for without side effects`() {
        // Program:
        // for (; true; ) { }
        val prog = program(ForStmt(ForNoInit, litBool(true), null, block()))

        // After dead code elimination:
        // (empty)
        val opt = DeadCodeEliminator.apply(prog)
        assertTrue(opt.statements.isEmpty())
    }

    @Test
    fun `keeps for with side effect in initializer`() {
        // Program:
        // for (f(); ; ) { }
        val prog = program(ForStmt(ForExprInit(call("f", emptyList())), null, null, block()))

        // After dead code elimination:
        // for (f(); ; ) { }
        val opt = DeadCodeEliminator.apply(prog)
        assertEquals(1, opt.statements.size)
        assertTrue(opt.statements[0] is ForStmt)
    }

    @Test
    fun `optimizes function body but keeps function`() {
        // Program:
        // func foo() { let a: int = 1; }
        val fn =
                FunctionDecl(
                        "foo",
                        emptyList(),
                        TypeNode.VoidType,
                        block(varDecl("a", TypeNode.IntType, litLong(1))),
                        p
                )

        // After dead code elimination:
        // func foo() { }
        val opt = DeadCodeEliminator.apply(program(fn))
        val f = opt.statements[0] as FunctionDecl
        assertTrue(f.body.statements.isEmpty())
    }

    @Test
    fun `removes pure expression statement`() {
        // Program:
        // 1 + 2;
        val prog = program(exprStmt(bin(litLong(1), TokenType.PLUS, litLong(2))))

        // After dead code elimination:
        // (empty)
        val opt = DeadCodeEliminator.apply(prog)
        assertTrue(opt.statements.isEmpty())
    }

    @Test
    fun `complex scenario with return and side effects`() {
        // Program:
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

        // After dead code elimination:
        // let b = f();
        // b;
        // return 0;
        val opt = DeadCodeEliminator.apply(prog)
        assertEquals(3, opt.statements.size)
        assertTrue(opt.statements[2] is ReturnStmt)
    }
}
