package com.compiler.parser.ast

import com.compiler.domain.SourcePos
import com.compiler.lexer.TokenType

/** Базовый класс для всех выражений. */
sealed class Expression : ASTNode()

/**
 * Интерфейс для допустимых левых частей присваивания (lvalue). В языке допускаются:
 * - простая переменная (VariableExpr)
 * - доступ по индексу массива (ArrayAccessExpr)
 */
sealed interface LValue

/**
 * Присваивание: `<lvalue> = <value>`
 *
 * @property target целевая левая часть присваивания (LValue)
 * @property value выражение, вычисляемое и записываемое в target
 * @property pos позиция символа `=` или позиция операции присваивания
 */
data class AssignExpr(val target: LValue, val value: Expression, val pos: SourcePos) : Expression()

/**
 * Бинарное выражение: `<left> <operator> <right>`.
 *
 * @property left левый операнд
 * @property operator тип токена оператора (TokenType.PLUS, EQ, LT и т.д.)
 * @property right правый операнд
 * @property pos позиция оператора в исходном тексте (используется для сообщений об ошибках)
 */
data class BinaryExpr(
        val left: Expression,
        val operator: TokenType,
        val right: Expression,
        val pos: SourcePos
) : Expression()

/**
 * Унарное выражение: `<op> <right>` (например, `-x`, `!flag`).
 *
 * @property operator тип токена оператора (TokenType.MINUS, NOT и т.д.)
 * @property right операнд
 * @property pos позиция оператора в исходном тексте
 */
data class UnaryExpr(val operator: TokenType, val right: Expression, val pos: SourcePos) :
        Expression()

/**
 * Литеральное выражение для примитивных значений.
 *
 * Значение может быть:
 * - Long для целых литералов
 * - Double для чисел с плавающей точкой
 * - Boolean для true/false
 * - null (при необходимости)
 *
 * @property value значение литерала
 * @property pos позиция первого символа литерала в исходном файле
 */
data class LiteralExpr(val value: Any?, val pos: SourcePos) : Expression()

/**
 * Массивный литерал: `[expr1, expr2, ...]`
 *
 * @property elements список выражений-элементов массива (может быть пустым)
 * @property pos позиция начала литерала (`[`)
 */
data class ArrayLiteralExpr(val elements: List<Expression>, val pos: SourcePos) : Expression()

/**
 * Группирующее/скобочное выражение: `(expr)`.
 *
 * @property expression вложенное выражение
 * @property pos позиция открывающей круглой скобки `(`
 */
data class GroupingExpr(val expression: Expression, val pos: SourcePos) : Expression()

/**
 * Выражение-идентификатор (переменная).
 *
 * @property name имя идентификатора
 * @property pos позиция начала идентификатора
 */
data class VariableExpr(val name: String, val pos: SourcePos) : Expression(), LValue

/**
 * Вызов функции или метода: `<callee>(arg1, arg2, ...)`.
 *
 * @property callee выражение, которое вызывается (переменная, свойство и т.д.)
 * @property args аргументы вызова в порядке перечисления
 * @property pos позиция открывающей круглой скобки вызова или позиции самого вызова
 */
data class CallExpr(val callee: Expression, val args: List<Expression>, val pos: SourcePos) :
        Expression()

/**
 * Доступ к элементу массива: `<array>[<index>]`.
 *
 * @property array выражение, возвращающее массив
 * @property index выражение индекса
 * @property pos позиция закрывающей скобки `]` или позиции доступа (используется для ошибок)
 */
data class ArrayAccessExpr(val array: Expression, val index: Expression, val pos: SourcePos) :
        Expression(), LValue

/**
 * Доступ к свойству/методу через точку: `<receiver>.<property>`
 *
 * Для вызова метода в AST должно использоваться сочетание PropertyAccessExpr в качестве callee
 * внутри CallExpr (т.е. `CallExpr(PropertyAccessExpr(...), args)`).
 *
 * @property receiver выражение-ресивер (объект)
 * @property property имя свойства/метода
 * @property pos позиция токена свойства (точки или начала имени свойства)
 */
data class PropertyAccessExpr(val receiver: Expression, val property: String, val pos: SourcePos) :
        Expression()
