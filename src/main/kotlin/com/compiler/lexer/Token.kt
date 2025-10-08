package com.compiler.lexer

/**
 * Token - minimal unit of source code
 * 
 * @property type token type
 * @property lexeme source text of the token
 * @property line line number (starting from 1)
 * @property column column number (starting from 1)
 * @property literal literal value (for numbers)
 */
data class Token(
    val type: TokenType,
    val lexeme: String,
    val line: Int,
    val column: Int,
    val literal: Any? = null
) {
    override fun toString(): String {
        return if (literal != null) {
            "Token($type, '$lexeme', literal=$literal, pos=$line:$column)"
        } else {
            "Token($type, '$lexeme', pos=$line:$column)"
        }
    }
    
    /**
     * Short version for debugging
     */
    fun toShortString(): String {
        return if (literal != null) {
            "$type($literal)"
        } else {
            type.toString()
        }
    }
}

