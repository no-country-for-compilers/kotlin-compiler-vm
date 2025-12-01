package com.compiler.lexer

import com.compiler.domain.SourcePos

/**
 * Token - minimal unit of source code
 * 
 * @property type token type
 * @property lexeme source text of the token
 * @property literal literal value (for numbers)
 * @property pos position in source code
 */
data class Token(
    val type: TokenType,
    val lexeme: String,
    val literal: Any? = null,
    val pos: SourcePos,
) {
    override fun toString(): String {
        return if (literal != null) {
            "Token($type, '$lexeme', literal=$literal, pos=${pos.line}:${pos.column})"
        } else {
            "Token($type, '$lexeme', pos=${pos.line}:${pos.column})"
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
