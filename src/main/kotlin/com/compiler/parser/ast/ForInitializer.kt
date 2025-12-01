package com.compiler.parser.ast

/**
 * Иерархия для инициализатора в заголовке `for`-цикла.
 *
 * `for ( <initializer>? ; <condition>? ; <increment>? ) { <body> }`
 *
 * ForInitializer описывает возможные варианты инициализатора:
 * - ForVarInit — объявление переменной (`let ...`)
 * - ForExprInit — произвольное выражение
 * - ForNoInit — отсутствие инициализации
 */
sealed class ForInitializer

/**
 * Инициализация переменной внутри заголовка for: `let <id>: <type> = <expr>`
 *
 * @property decl VarDecl — декларация переменной (без завершающего `;`)
 */
data class ForVarInit(val decl: VarDecl) : ForInitializer()

/**
 * Инициализация выражением в заголовке for.
 *
 * @property expr выражение-инициализатор
 */
data class ForExprInit(val expr: Expression) : ForInitializer()

/** Отсутствие инициализатора в заголовке for (например: `for (; cond; inc)`). */
object ForNoInit : ForInitializer()

/**
 * Оператор цикла for (только классическая форма).
 *
 * Грамматика:
 * ```
 * for_statement ::= "for" "(" [ variable_declaration | expression ] ";" [ expression ] ";" [ expression ] ")" "{" { statement } "}"
 * ```
 *
 * @property initializer возможный инициализатор (ForVarInit | ForExprInit | ForNoInit)
 * @property condition условие цикла (nullable — допускается пропуск, тогда считается истинным)
 * @property increment выражение инкремента, выполняемое в конце каждой итерации (nullable)
 * @property body тело цикла в виде блока операторов
 */
data class ForStmt(
        val initializer: ForInitializer = ForNoInit,
        val condition: Expression?,
        val increment: Expression?,
        val body: BlockStmt
) : Statement()
