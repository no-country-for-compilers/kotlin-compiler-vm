package com.compiler.semantic

import com.compiler.parser.ast.Program
import com.compiler.parser.ast.TypeNode
import com.compiler.domain.SourcePos

sealed class Type {
    object Int : Type()
    object Float : Type()
    object Bool : Type()
    object Void : Type()
    data class Array(val elementType: Type) : Type()
    object Unknown : Type()

    override fun toString(): String = when (this) {
        Int -> "int"
        Float -> "float"
        Bool -> "bool"
        Void -> "void"
        is Array -> "${elementType}[]"
        Unknown -> "unknown"
    }
}

fun TypeNode.toSemanticType(): Type = when (this) {
    TypeNode.IntType -> Type.Int
    TypeNode.FloatType -> Type.Float
    TypeNode.BoolType -> Type.Bool
    TypeNode.VoidType -> Type.Void
    is TypeNode.ArrayType -> Type.Array(elementType.toSemanticType())
}

sealed class Symbol {
    abstract val name: String
}

data class VariableSymbol(
    override val name: String,
    val type: Type
) : Symbol()

data class FunctionSymbol(
    override val name: String,
    val parameters: List<VariableSymbol>,
    val returnType: Type
) : Symbol()

class Scope(val parent: Scope?) {
    private val variables = mutableMapOf<String, VariableSymbol>()
    private val functions = mutableMapOf<String, MutableList<FunctionSymbol>>()

    fun defineVariable(symbol: VariableSymbol): Boolean {
        if (variables.containsKey(symbol.name)) {
            return false
        }
        variables[symbol.name] = symbol
        return true
    }

    fun defineFunction(symbol: FunctionSymbol): Boolean {
        val functionList = functions.getOrPut(symbol.name) { mutableListOf() }
        // Проверяем, нет ли уже функции с такой же сигнатурой
        if (functionList.any { it.parameters == symbol.parameters }) {
            return false
        }
        functionList.add(symbol)
        return true
    }

    fun resolveVariable(name: String): VariableSymbol? =
        variables[name] ?: parent?.resolveVariable(name)

    fun resolveFunction(name: String): FunctionSymbol? {
        val localFunctions = functions[name]
        if (localFunctions != null && localFunctions.isNotEmpty()) {
            return localFunctions[0] // Возвращаем первую найденную (для обратной совместимости)
        }
        return parent?.resolveFunction(name)
    }

    /**
     * Разрешает функцию по имени и типам аргументов (для поддержки перегрузки).
     * 
     * @param name имя функции
     * @param argTypes типы аргументов
     * @return подходящая функция или null, если не найдена
     */
    fun resolveFunction(name: String, argTypes: List<Type>): FunctionSymbol? {
        val localFunctions = functions[name]
        if (localFunctions != null) {
            // Ищем функцию с подходящими типами параметров
            for (fn in localFunctions) {
                if (fn.parameters.size == argTypes.size) {
                    var matches = true
                    for (i in argTypes.indices) {
                        if (!isAssignable(argTypes[i], fn.parameters[i].type)) {
                            matches = false
                            break
                        }
                    }
                    if (matches) {
                        return fn
                    }
                }
            }
        }
        return parent?.resolveFunction(name, argTypes)
    }

    private fun isAssignable(from: Type, to: Type): Boolean {
        if (to == Type.Unknown || from == Type.Unknown) return true
        return from == to
    }

    fun getAllVariables(): Map<String, VariableSymbol> = variables.toMap()
    fun getAllFunctions(): Map<String, List<FunctionSymbol>> = functions.mapValues { it.value.toList() }
}

data class SemanticError(
    val message: String,
    val position: Any?
)

/**
 * Исключение, выбрасываемое семантическим анализатором при обнаружении семантической ошибки.
 *
 * @property pos позиция в исходном коде, где произошла ошибка (может быть null)
 * @param message описание семантической ошибки
 */
class SemanticException(
    val pos: SourcePos?,
    message: String
) : RuntimeException(
    if (pos != null) {
        "Semantic error at ${pos.line}:${pos.column}: $message"
    } else {
        "Semantic error: $message"
    }
)

data class AnalysisResult(
    val program: Program,
    val globalScope: Scope,
    val error: SemanticError?
)
