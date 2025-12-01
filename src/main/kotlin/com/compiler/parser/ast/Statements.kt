package com.compiler.parser.ast

import com.compiler.parser.ast.Expression
import com.compiler.domain.SourcePos

/** Базовый класс для всех узлов-операторов (statements). */
sealed class Statement : ASTNode()

/**
 * Объявление переменной: `let <identifier>: <type> = <expression>;`
 *
 * @property identifier имя переменной
 * @property type объявленный тип переменной
 * @property expression выражение-инициализатор (обязательное)
 * @property pos позиция в исходном файле (рядок:столбец) начала объявления (ключевого слова `let`
 * или идентификатора)
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
 * Оператор return: `return [expression];`
 *
 * @property value опциональное возвращаемое выражение (null для `return;`)
 * @property pos позиция ключевого слова `return` (используется для ошибок/диагностик)
 */
data class ReturnStmt(val value: Expression?, val pos: SourcePos) : Statement()

/**
 * Оператор-выражение: любое выражение, использованное как оператор и завершающееся `;`.
 *
 * @property expr выражение внутри оператора
 */
data class ExprStmt(val expr: Expression) : Statement()

/**
 * Блок операторов, заключённых в `{}`.
 *
 * @property statements список операторов внутри блока, в порядке выполнения
 */
data class BlockStmt(val statements: List<Statement>) : Statement()
