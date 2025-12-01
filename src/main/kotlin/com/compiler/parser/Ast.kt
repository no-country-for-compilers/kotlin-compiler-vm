package com.compiler.parser

import com.compiler.lexer.TokenType
import com.compiler.domain.SourcePos

/**
 * Базовый класс для всех узлов AST. 
 * Используется как общий суперкласс для выражений, операторов и типов.
 */
sealed class ASTNode


// --- Program ---
/**
 * Корневой узел разобранной программы.
 *
 * @property statements список верхнеуровневых операторов и объявлений в порядке появления в
 * исходнике
 */
data class Program(val statements: List<Statement>) : ASTNode()


// --- Types ---
/**
 * Представление типа в языке. 
 * Может быть примитивным (int/float/bool/void) или составным (массив).
 */
sealed class TypeNode : ASTNode() {
    /** 
     * Целочисленный тип (signed 64-bit). 
     */
    object IntType : TypeNode()

    /** 
     * Тип с плавающей точкой (double precision). 
     */
    object FloatType : TypeNode()

    /** 
     * Булев тип. 
     */
    object BoolType : TypeNode()

    /** 
     * Void-тип (используется для функций, не возвращающих значение). 
     */
    object VoidType : TypeNode()

    /**
     * Тип массива.
     *
     * @property elementType тип элементов массива (может быть любым TypeNode, в т.ч. ArrayType для многомерных массивов)
     */
    data class ArrayType(val elementType: TypeNode) : TypeNode()
}


// --- Statements ---
/**
 * Базовый класс для всех узлов-операторов (statements).
 */
sealed class Statement : ASTNode()

/**
 * Объявление переменной: `let <identifier>: <type> = <expression>;`
 *
 * @property identifier имя переменной
 * @property type объявленный тип переменной
 * @property expression выражение-инициализатор (обязательное)
 * @property pos позиция в исходном файле (рядок:столбец) начала объявления (ключевого слова `let` или идентификатора)
 */
data class VarDecl(
    val identifier: String,
    val type: TypeNode,
    val expression: Expression,
    val pos: SourcePos
) : Statement()

/**
 * Объявление функции: `func <identifier>(<parameters>): <returnType> { <body> }`
 *
 * @property identifier имя функции
 * @property parameters список параметров функции (имя + тип + позиция)
 * @property returnType возвращаемый тип функции
 * @property body тело функции в виде блок-оператора
 * @property pos позиция имени функции (используется для сообщений об ошибках)
 */
data class FunctionDecl(
    val identifier: String,
    val parameters: List<Parameter>,
    val returnType: TypeNode,
    val body: BlockStmt,
    val pos: SourcePos
) : Statement()

/**
 * Параметр функции: `<identifier>: <type>`
 *
 * @property identifier имя параметра
 * @property type тип параметра
 * @property pos позиция имени параметра в исходном тексте
 */
data class Parameter(val identifier: String, val type: TypeNode, val pos: SourcePos)

/**
 * Условный оператор: `if (<condition>) { <thenBranch> } [ else { <elseBranch> } ]`
 *
 * @property condition выражение-условие
 * @property thenBranch блок, выполняемый при истинности условия
 * @property elseBranch опциональный блок-ветвь при ложном условии
 */
data class IfStmt(
    val condition: Expression,
    val thenBranch: BlockStmt,
    val elseBranch: BlockStmt? = null
) : Statement()

/**
 * Вспомогательная иерархия для инициализатора в `for`-цикле. Позволяет однозначно представлять
 * формы `for`:
 * - ForVarInit: `let ...` (инициализация переменной)
 * - ForExprInit: выражение-инициализация
 * - ForNoInit: отсутствие инициализации
 */
sealed class ForInitializer

/**
 * Инициализация переменной внутри заголовка for: `let ...`
 *
 * @property decl VarDecl без завершающего `;` (представляет саму декларацию)
 */
data class ForVarInit(val decl: VarDecl) : ForInitializer()

/**
 * Инициализация выражением в заголовке for.
 *
 * @property expr выражение инициализации
 */
data class ForExprInit(val expr: Expression) : ForInitializer()

/** 
 * Отсутствие инициализатора в заголовке for. 
 */
object ForNoInit : ForInitializer()

/**
 * Цикл for.
 *
 * Поддерживаются две формы:
 * 1. Классическая: `for ( <initializer> ; <condition> ; <increment> ) { <body> }`
 * 2. While-подобная: `for ( <condition> ) { <body> }`
 *
 * @property initializer инициализатор (ForVarInit | ForExprInit | ForNoInit)
 * @property condition выражение-условие (nullable для случаев отсутствия)
 * @property increment выражение-инкремента (выполняется в конце итерации), nullable
 * @property body тело цикла (блок операторов)
 */
data class ForStmt(
    val initializer: ForInitializer = ForNoInit,
    val condition: Expression?,
    val increment: Expression?,
    val body: BlockStmt
) : Statement()

/**
 * Оператор return: `return [expression];`
 *
 * @property value опциональное возвращаемое выражение (null для `return;`)
 * @property pos позиция ключевого слова `return` (используется для ошибок/диагностик)
 */
data class ReturnStmt(
    val value: Expression?, 
    val pos: SourcePos
) : Statement()

/**
 * Оператор-выражение: любое выражение, использованное как оператор и завершающееся `;`.
 *
 * @property expr выражение внутри оператора
 */
data class ExprStmt(
    val expr: Expression
) : Statement()

/**
 * Блок операторов, заключённых в `{}`.
 *
 * @property statements список операторов внутри блока, в порядке выполнения
 */
data class BlockStmt(
    val statements: List<Statement>
) : Statement()

// --- Expressions ---
/** 
 * Базовый класс для всех выражений. 
 */
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
data class AssignExpr(val target: LValue, val value: Expression, val pos: SourcePos) :
        Expression()

/**
 * Бинарное выражение: `<left> <operator> <right>`.
 *
 * @property left левый операнд
 * @property operator тип токена оператора (TokenType.PLUS, EQ, LT и т.д.)
 * @property right правый операнд
 * @property pos позиция оператора в исходном тексте (используется для сообщений об ошибках)
 */
data class BinaryExpr(val left: Expression, val operator: TokenType, val right: Expression, val pos: SourcePos) : Expression()

/**
 * Унарное выражение: `<op> <right>` (например, `-x`, `!flag`).
 *
 * @property operator тип токена оператора (TokenType.MINUS, NOT и т.д.)
 * @property right операнд
 * @property pos позиция оператора в исходном тексте
 */
data class UnaryExpr(val operator: TokenType, val right: Expression, val pos: SourcePos) : Expression()

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
data class CallExpr(val callee: Expression, val args: List<Expression>, val pos: SourcePos) : Expression()

/**
 * Доступ к элементу массива: `<array>[<index>]`.
 *
 * @property array выражение, возвращающее массив
 * @property index выражение индекса
 * @property pos позиция закрывающей скобки `]` или позиции доступа (используется для ошибок)
 */
data class ArrayAccessExpr(val array: Expression, val index: Expression, val pos: SourcePos) : Expression(), LValue

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
data class PropertyAccessExpr(val receiver: Expression, val property: String, val pos: SourcePos) : Expression()