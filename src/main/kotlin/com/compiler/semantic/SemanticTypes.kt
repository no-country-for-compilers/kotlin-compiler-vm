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
    private val functions = mutableMapOf<String, FunctionSymbol>()

    fun defineVariable(symbol: VariableSymbol): Boolean {
        if (variables.containsKey(symbol.name)) {
            return false
        }
        variables[symbol.name] = symbol
        return true
    }

    fun defineFunction(symbol: FunctionSymbol): Boolean {
        if (functions.containsKey(symbol.name)) {
            return false
        }
        functions[symbol.name] = symbol
        return true
    }

    fun resolveVariable(name: String): VariableSymbol? =
        variables[name] ?: parent?.resolveVariable(name)

    fun resolveFunction(name: String): FunctionSymbol? =
        functions[name] ?: parent?.resolveFunction(name)

    fun getAllVariables(): Map<String, VariableSymbol> = variables.toMap()
    fun getAllFunctions(): Map<String, FunctionSymbol> = functions.toMap()
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
