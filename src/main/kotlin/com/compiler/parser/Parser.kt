package com.compiler.parser

import com.compiler.lexer.Token
import com.compiler.lexer.TokenType
import com.compiler.parser.ast.*

/**
 * Рекурсивный нисходящий парсер для языка. Преобразует плоский список токенов в абстрактное синтаксическое дерево (AST).
 *
 * @property tokens список токенов, сгенерированных лексером
 */
class Parser(private val tokens: List<Token>) {
    private var current = 0

    /**
     * Точка входа парсера.
     *
     * Грамматика:
     * ```
     * program ::= declaration* EOF
     * ```
     *
     * @return узел Program, содержащий список разобранных инструкций.
     */
    fun parse(): Program {
        val statements = mutableListOf<Statement>()
        while (!isAtEnd()) {
            statements.add(declaration())
        }
        return Program(statements)
    }

    /**
     * Разбор высокоуровневых деклараций.
     *
     * Грамматика:
     * ```
     * declaration ::= functionDecl | varDecl | statement
     * ```
     */
    private fun declaration(): Statement {
        return when {
            match(TokenType.FUNC) -> functionDecl()
            match(TokenType.LET) -> varDecl()
            else -> statement()
        }
    }

    /**
     * Разбор объявления функции.
     *
     * Грамматика (спецификация требует явного указания типа возвращаемого значения):
     * ```
     * functionDecl ::= "func" identifier "(" parameters? ")" ":" type block
     * ```
     */
    private fun functionDecl(): FunctionDecl {
        val nameToken = consume(TokenType.IDENTIFIER, "Expected function name after 'func'")
        val name = nameToken.lexeme
        consume(TokenType.LPAREN, "Expected '(' after function name")
        val params = mutableListOf<Parameter>()
        if (!check(TokenType.RPAREN)) {
            do {
                val paramNameToken = consume(TokenType.IDENTIFIER, "Expected parameter name")
                consume(TokenType.COLON, "Expected ':' after parameter name")
                val typeNode = parseType()
                params.add(Parameter(paramNameToken.lexeme, typeNode, paramNameToken.pos))
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RPAREN, "Expected ')' after parameters")

        consume(TokenType.COLON, "Function declaration must specify return type (':' missing)")
        val returnType = parseType()

        consume(TokenType.LBRACE, "Expected '{' before function body")
        val body = parseBlock()
        return FunctionDecl(name, params, returnType, body, nameToken.pos)
    }

    /**
     * Разбор объявления переменной.
     *
     * Грамматика:
     * ```
     * varDecl ::= "let" identifier ":" type "=" expression ";"
     * ```
     */
    private fun varDecl(): VarDecl {
        val nameToken = consume(TokenType.IDENTIFIER, "Expected variable name after 'let'")
        consume(TokenType.COLON, "Expected ':' after variable name")
        val typeNode = parseType()
        consume(TokenType.ASSIGN, "Expected '=' and initializer in variable declaration")
        val initializer = expression()
        consume(TokenType.SEMICOLON, "Expected ';' after variable declaration")

        if (initializer is ArrayInitExpr) {
            if (typeNode !is TypeNode.ArrayType) {
                throw ParseException(
                        nameToken.pos,
                        "Initializer is array, but declared type is not array"
                )
            }
            val declaredBase = unwrapArrayElementType(typeNode)
            if (declaredBase::class != initializer.elementType::class) {
                throw ParseException(
                        nameToken.pos,
                        "Array initializer base type '${initializer.elementType}' does not match declared element type '$declaredBase'"
                )
            }
        }

        return VarDecl(nameToken.lexeme, typeNode, initializer, nameToken.pos)
    }

    private fun unwrapArrayElementType(t: TypeNode): TypeNode {
        var cur = t
        while (cur is TypeNode.ArrayType) {
            cur = cur.elementType
        }
        return cur
    }

    /**
     * Разбор объявления переменной внутри заголовка цикла for (без завершающей ';').
     *
     * Фрагмент грамматики: "let" identifier ":" type "=" expression
     */
    private fun varDeclInForHeader(): VarDecl {
        val nameToken = consume(TokenType.IDENTIFIER, "Expected variable name after 'let'")
        consume(TokenType.COLON, "Expected ':' after variable name")
        val typeNode = parseType()
        consume(TokenType.ASSIGN, "Expected '=' and initializer in variable declaration")
        val initializer = expression()
        return VarDecl(nameToken.lexeme, typeNode, initializer, nameToken.pos)
    }

    /**
     * Разбор аннотации типа, включая массивы.
     *
     * Грамматика:
     * ```
     * type ::= "int" | "float" | "bool" | "void" | identifier | type "[]"
     * ```
     */
    private fun parseType(): TypeNode {
        val t = advance()
        val base: TypeNode =
                when (t.type) {
                    TokenType.TYPE_INT -> TypeNode.IntType
                    TokenType.TYPE_FLOAT -> TypeNode.FloatType
                    TokenType.TYPE_BOOL -> TypeNode.BoolType
                    TokenType.TYPE_VOID -> TypeNode.VoidType
                    else ->
                            throw ParseException(
                                    t.pos,
                                    "Expected type annotation (int, float, bool, void or custom)"
                            )
                }

        var resultType = base
        while (match(TokenType.LBRACKET)) {
            consume(TokenType.RBRACKET, "Expected ']' after '[' in array type")
            resultType = TypeNode.ArrayType(resultType)
        }
        return resultType
    }

    /** 
     * Разбор любой инструкции. 
     */
    private fun statement(): Statement {
        return when {
            match(TokenType.IF) -> ifStatement()
            match(TokenType.FOR) -> forStatement()
            match(TokenType.RETURN) -> returnStatement()
            match(TokenType.LBRACE) -> parseBlock()
            else -> expressionStatement()
        }
    }

    /**
     * Разбор инструкции if.
     *
     * Грамматика:
     * ```
     * ifStatement ::= "if" "(" expression ")" statement ("else" statement)?
     * ```
     */
    private fun ifStatement(): IfStmt {
        consume(TokenType.LPAREN, "Expected '(' after 'if'")
        val condition = expression()
        consume(TokenType.RPAREN, "Expected ')' after if condition")
        val thenBranch =
                statement() as? BlockStmt
                        ?: BlockStmt(listOf(thenBranchOrSingleStatement(thenBranch = statement())))
        var elseBranch: BlockStmt? = null
        if (match(TokenType.ELSE)) {
            val elseStmt = statement()
            elseBranch = elseStmt as? BlockStmt ?: BlockStmt(listOf(elseStmt))
        }
        return IfStmt(condition, toBlockStmt(thenBranch), elseBranch)
    }

    /**
     * Разбор цикла for.
     *
     * Поддерживается:
     * - for (<initializer>? ; <condition>? ; <increment>?) statement
     * - for (<condition>) statement
     */
    private fun forStatement(): ForStmt {
        consume(TokenType.LPAREN, "Expected '(' after 'for'")

        val initializer: ForInitializer =
            when {
                match(TokenType.SEMICOLON) -> ForNoInit
                match(TokenType.LET) -> {
                    val decl = varDeclInForHeader()
                    consume(TokenType.SEMICOLON, "Expected ';' after loop initializer")
                    ForVarInit(decl)
                }
                else -> {
                    val expr = expression()
                    consume(TokenType.SEMICOLON, "Expected ';' after loop initializer")
                    ForExprInit(expr)
                }
            }


        val condition: Expression? =
            if (!check(TokenType.SEMICOLON)) {
                expression()
            } else {
                null
            }
        consume(TokenType.SEMICOLON, "Expected ';' after loop condition")

        val increment: Expression? =
            if (!check(TokenType.RPAREN)) {
                expression()
            } else {
                null
            }
        consume(TokenType.RPAREN, "Expected ')' after for clauses")
        val bodyStmt = statement()
        val body = toBlockStmt(bodyStmt)
        return ForStmt(initializer, condition, increment, body)
    }

    /**
     * Разбор инструкции return.
     *
     * Грамматика:
     * ```
     * returnStatement ::= "return" expression? ";"
     * ```
     */
    private fun returnStatement(): ReturnStmt {
        val keyword = previous()
        val value =
                if (!check(TokenType.SEMICOLON)) {
                    expression()
                } else null
        consume(TokenType.SEMICOLON, "Expected ';' after return value")
        return ReturnStmt(value, keyword.pos)
    }

    /**
     * Разбор блока инструкций/деклараций.
     *
     * Грамматика:
     * ```
     * block ::= "{" declaration* "}"
     * ```
     */
    private fun parseBlock(): BlockStmt {
        val statements = mutableListOf<Statement>()
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            statements.add(declaration())
        }
        consume(TokenType.RBRACE, "Expected '}' after block")
        return BlockStmt(statements)
    }

    /**
     * Разбор выражения с завершающей ';'.
     *
     * Грамматика:
     * ```
     * expressionStatement ::= expression ";"
     * ```
     */
    private fun expressionStatement(): Statement {
        val expr = expression()
        consume(TokenType.SEMICOLON, "Expected ';' after expression")
        return ExprStmt(expr)
    }

    /**
     * Точка входа для разбора выражений.
     *
     * Грамматика:
     * ```
     * expression ::= assignment
     * ```
     */
    private fun expression(): Expression = assignment()

    /**
     * Разбор выражений присваивания.
     *
     * Грамматика:
     * ```
     * assignment ::= logicalOr ( "=" assignment )?
     * ```
     *
     * Примечание: допустимые цели — VariableExpr, ArrayAccessExpr, PropertyAccessExpr.
     */
    private fun assignment(): Expression {
        val expr = logicOr()

        if (match(TokenType.ASSIGN)) {
            val equals = previous()
            val value = assignment()

            return when (expr) {
                is VariableExpr -> AssignExpr(expr, value, equals.pos)
                is ArrayAccessExpr -> AssignExpr(expr, value, equals.pos)
                else -> throw ParseException(equals.pos, "Invalid assignment target")
            }
        }

        return expr
    }

    /**
     * Разбор логического ИЛИ (||) выражений. 
     * Реализует левостороннюю ассоциативность: expr || expr || expr ...
     */
    private fun logicOr(): Expression {
        var expr = logicAnd()
        while (match(TokenType.OR)) {
            val opTok = previous()
            val op = opTok.type
            val right = logicAnd()
            expr = BinaryExpr(expr, op, right, opTok.pos)
        }
        return expr
    }

    /**
     * Разбор логического И (&&) выражений. 
     * Реализует левостороннюю ассоциативность: expr && expr && expr ...
     */
    private fun logicAnd(): Expression {
        var expr = equality()
        while (match(TokenType.AND)) {
            val opTok = previous()
            val op = opTok.type
            val right = equality()
            expr = BinaryExpr(expr, op, right, opTok.pos)
        }
        return expr
    }

    /**
     * Разбор операций сравнения на равенство/неравенство (==, !=). 
     * Выражения вычисляются слева направо.
     */
    private fun equality(): Expression {
        var expr = comparison()
        while (match(TokenType.EQ, TokenType.NE)) {
            val opTok = previous()
            val op = opTok.type
            val right = comparison()
            expr = BinaryExpr(expr, op, right, opTok.pos)
        }
        return expr
    }

    /** 
     * Разбор сравнительных операций (<, <=, >, >=). 
     * Поддерживает левостороннюю ассоциативность. 
     */
    private fun comparison(): Expression {
        var expr = term()
        while (match(TokenType.LT, TokenType.LE, TokenType.GT, TokenType.GE)) {
            val opTok = previous()
            val op = opTok.type
            val right = term()
            expr = BinaryExpr(expr, op, right, opTok.pos)
        }
        return expr
    }

    /**
     * Разбор арифметических выражений сложения и вычитания (+, -). 
     * Поддерживает левостороннюю ассоциативность.
     */
    private fun term(): Expression {
        var expr = factor()
        while (match(TokenType.PLUS, TokenType.MINUS)) {
            val opTok = previous()
            val op = opTok.type
            val right = factor()
            expr = BinaryExpr(expr, op, right, opTok.pos)
        }
        return expr
    }

    /**
     * Разбор арифметических выражений умножения, деления и остатка (*, /, %). 
     * Поддерживает левостороннюю ассоциативность.
     */
    private fun factor(): Expression {
        var expr = unary()
        while (match(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            val opTok = previous()
            val op = opTok.type
            val right = unary()
            expr = BinaryExpr(expr, op, right, opTok.pos)
        }
        return expr
    }

    /**
     * Разбор унарных операторов (NOT, -, +). 
     * Рекурсивно обрабатывает последовательные унарные операции.
     */
    private fun unary(): Expression {
        if (match(TokenType.NOT, TokenType.MINUS, TokenType.PLUS)) {
            val opTok = previous()
            val op = opTok.type
            val right = unary()
            return UnaryExpr(op, right, opTok.pos)
        }
        return call()
    }

    /**
     * Разбор вызовов функций, обращения к свойствам и индексации массивов. Последовательно
     * обрабатывает: вызовы (), доступ к свойству ., доступ к элементу массива [].
     */
    private fun call(): Expression {
        var expr = primary()
        while (true) {
            when {
                match(TokenType.LPAREN) -> {
                    expr = finishCall(expr)
                }
                match(TokenType.LBRACKET) -> {
                    val indexExpr = expression()
                    val rb = consume(TokenType.RBRACKET, "Expected ']' after index")
                    expr = ArrayAccessExpr(expr, indexExpr, rb.pos)
                }
                else -> break
            }
        }
        return expr
    }

    /** 
     * Завершает разбор вызова функции, собирая список аргументов. 
     */
    private fun finishCall(calleeExpr: Expression): Expression {
        val (name, namePos) =
                when (calleeExpr) {
                    is VariableExpr -> calleeExpr.name to calleeExpr.pos
                    else ->
                        throw ParseException(
                            previous().pos,
                            "Can only call functions by identifier"
                        )
                }

        val args = mutableListOf<Expression>()
        val startPos = namePos
        if (!check(TokenType.RPAREN)) {
            do {
                args.add(expression())
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RPAREN, "Expected ')' after arguments")
        return CallExpr(name, args, startPos)
    }

    /**
     * Разбор первичных выражений:
     * - Литералы (true, false, числа)
     * - Идентификаторы (переменные)
     * - Массивные литералы
     * - Группировка через скобки
     */
    private fun primary(): Expression {
        if (match(TokenType.FALSE)) return LiteralExpr(false, previous().pos)
        if (match(TokenType.TRUE)) return LiteralExpr(true, previous().pos)
        if (match(TokenType.INT_LITERAL)) {
            val lit =
                    previous().literal as? Long
                            ?: throw ParseException(previous().pos, "Invalid int literal")
            return LiteralExpr(lit, previous().pos)
        }
        if (match(TokenType.FLOAT_LITERAL)) {
            val lit =
                    previous().literal as? Double
                            ?: throw ParseException(previous().pos, "Invalid float literal")
            return LiteralExpr(lit, previous().pos)
        }

        if (match(TokenType.TYPE_INT, TokenType.TYPE_FLOAT, TokenType.TYPE_BOOL)) {
            val baseTok = previous()
            if (!match(TokenType.LBRACKET)) {
                throw ParseException(baseTok.pos, "Unexpected type token here")
            }

            val sizeExpr = expression()
            consume(TokenType.RBRACKET, "Expected ']' after array size")

            val elementType =
                    when (baseTok.type) {
                        TokenType.TYPE_INT -> TypeNode.IntType
                        TokenType.TYPE_FLOAT -> TypeNode.FloatType
                        TokenType.TYPE_BOOL -> TypeNode.BoolType
                        else -> throw ParseException(baseTok.pos, "Invalid base type for array init")
                    }

            return ArrayInitExpr(elementType, sizeExpr, baseTok.pos)
        }

        if (match(TokenType.IDENTIFIER)) {
            val tok = previous()
            return VariableExpr(tok.lexeme, tok.pos)
        }

        if (match(TokenType.LPAREN)) {
            val startPos = previous().pos
            val expr = expression()
            consume(TokenType.RPAREN, "Expected ')' after expression")
            return GroupingExpr(expr, startPos)
        }

        val t = peek()
        throw ParseException(t.pos, "Expected expression, got ${t.type}")
    }

    /**
     * Вспомогательный метод: приводит одиночное Statement к BlockStmt, если грамматика ожидает блок.
     */
    private fun toBlockStmt(s: Statement): BlockStmt =
            when (s) {
                is BlockStmt -> s
                else -> BlockStmt(listOf(s))
            }

    /** 
     * Вспомогательный метод для защиты от ошибок при оборачивании одиночной инструкции. 
     */
    private fun thenBranchOrSingleStatement(thenBranch: Statement): Statement = thenBranch

    private fun match(vararg types: TokenType): Boolean {
        for (t in types) {
            if (check(t)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        val t = peek()
        throw ParseException(t.pos, message)
    }

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) return false
        return peek().type == type
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF

    private fun peek(): Token = tokens[current]

    private fun previous(): Token = tokens[current - 1]

    private fun synchronize() {
        while (!isAtEnd()) {
            if (previous().type == TokenType.SEMICOLON) return
            when (peek().type) {
                TokenType.FUNC, TokenType.LET, TokenType.IF, TokenType.FOR, TokenType.RETURN ->
                        return
                else -> advance()
            }
        }
    }
}
