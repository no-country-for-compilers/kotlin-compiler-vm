package com.compiler.lexer

import com.compiler.domain.SourcePos

/**
 * Lexical analyzer (Lexer/Tokenizer)
 * Converts source code into a sequence of tokens
 */
class Lexer(private val source: String) {
    private var current = 0  // current position in source
    private var line = 1     // current line
    private var column = 1   // current pos
    
    private val tokens = mutableListOf<Token>()
    
    // Language keywords
    private val keywords = mapOf(
        "let" to TokenType.LET,
        "func" to TokenType.FUNC,
        "if" to TokenType.IF,
        "else" to TokenType.ELSE,
        "for" to TokenType.FOR,
        "return" to TokenType.RETURN,
        "true" to TokenType.TRUE,
        "false" to TokenType.FALSE,
        "int" to TokenType.TYPE_INT,
        "float" to TokenType.TYPE_FLOAT,
        "bool" to TokenType.TYPE_BOOL,
        "void" to TokenType.TYPE_VOID
    )
    
    /**
     * Main tokenization function
     */
    fun tokenize(): List<Token> {
        while (!isAtEnd()) {
            scanToken()
        }
        
        // Add EOF token at the end
        tokens.add(Token(TokenType.EOF, "", pos = SourcePos(line, column)))
        return tokens
    }
    
    /**
     * Scan a single token
     */
    private fun scanToken() {
        val startColumn = column
        val c = advance()
        
        when (c) {
            // Whitespace characters
            ' ', '\r', '\t' -> { /* ignore */ }
            '\n' -> {
                line++
                column = 1
                return
            }
            
            // Single character tokens
            '(' -> addToken(TokenType.LPAREN, "(", startColumn)
            ')' -> addToken(TokenType.RPAREN, ")", startColumn)
            '{' -> addToken(TokenType.LBRACE, "{", startColumn)
            '}' -> addToken(TokenType.RBRACE, "}", startColumn)
            '[' -> addToken(TokenType.LBRACKET, "[", startColumn)
            ']' -> addToken(TokenType.RBRACKET, "]", startColumn)
            ';' -> addToken(TokenType.SEMICOLON, ";", startColumn)
            ':' -> addToken(TokenType.COLON, ":", startColumn)
            ',' -> addToken(TokenType.COMMA, ",", startColumn)
            '.' -> addToken(TokenType.DOT, ".", startColumn)
            '+' -> addToken(TokenType.PLUS, "+", startColumn)
            '-' -> addToken(TokenType.MINUS, "-", startColumn)
            '*' -> addToken(TokenType.STAR, "*", startColumn)
            '%' -> addToken(TokenType.PERCENT, "%", startColumn)
            
            // Two-character operators
            '/' -> {
                if (match('/')) {
                    // Comment until end of line
                    while (peek() != '\n' && !isAtEnd()) {
                        advance()
                    }
                } else {
                    addToken(TokenType.SLASH, "/", startColumn)
                }
            }
            
            '=' -> {
                if (match('=')) {
                    addToken(TokenType.EQ, "==", startColumn)
                } else {
                    addToken(TokenType.ASSIGN, "=", startColumn)
                }
            }
            
            '!' -> {
                if (match('=')) {
                    addToken(TokenType.NE, "!=", startColumn)
                } else {
                    addToken(TokenType.NOT, "!", startColumn)
                }
            }
            
            '<' -> {
                if (match('=')) {
                    addToken(TokenType.LE, "<=", startColumn)
                } else {
                    addToken(TokenType.LT, "<", startColumn)
                }
            }
            
            '>' -> {
                if (match('=')) {
                    addToken(TokenType.GE, ">=", startColumn)
                } else {
                    addToken(TokenType.GT, ">", startColumn)
                }
            }
            
            '&' -> {
                if (match('&')) {
                    addToken(TokenType.AND, "&&", startColumn)
                } else {
                    error(startColumn, "Unexpected character: '$c'. Did you mean '&&'?")
                }
            }
            
            '|' -> {
                if (match('|')) {
                    addToken(TokenType.OR, "||", startColumn)
                } else {
                    error(startColumn, "Unexpected character: '$c'. Did you mean '||'?")
                }
            }
            
            // Numbers
            in '0'..'9' -> number(startColumn)
            
            // Identifiers and keywords
            in 'a'..'z', in 'A'..'Z', '_' -> identifier(startColumn)
            
            else -> error(startColumn, "Unexpected character: '$c'")
        }
    }
    
    /**
     * Scan a number (int or float)
     */
    private fun number(startColumn: Int) {
        val start = current - 1
        
        // Integer part
        while (peek().isDigit()) {
            advance()
        }
        
        // Check for fractional part
        var isFloat = false
        if (peek() == '.' && peekNext().isDigit()) {
            isFloat = true
            advance() // consume '.'
            
            while (peek().isDigit()) {
                advance()
            }
        }
        
        // Check for exponential part (e or E)
        if (peek() == 'e' || peek() == 'E') {
            isFloat = true
            advance() // consume 'e' or 'E'
            
            if (peek() == '+' || peek() == '-') {
                advance() // consume sign
            }
            
            if (!peek().isDigit()) {
                error(column, "Expected digit after exponent")
                return
            }
            
            while (peek().isDigit()) {
                advance()
            }
        }
        
        val lexeme = source.substring(start, current)
        
        if (isFloat) {
            val value = lexeme.toDoubleOrNull()
            if (value != null) {
                addToken(TokenType.FLOAT_LITERAL, lexeme, value, startColumn)
            } else {
                error(startColumn, "Invalid float literal: $lexeme")
            }
        } else {
            val value = lexeme.toLongOrNull()
            if (value != null) {
                addToken(TokenType.INT_LITERAL, lexeme, value, startColumn)
            } else {
                error(startColumn, "Invalid integer literal: $lexeme (out of range for 64-bit int)")
            }
        }
    }
    
    /**
     * Scan identifier or keyword
     */
    private fun identifier(startColumn: Int) {
        val start = current - 1
        
        while (peek().isLetterOrDigit() || peek() == '_') {
            advance()
        }
        
        val text = source.substring(start, current)
        val type = keywords[text] ?: TokenType.IDENTIFIER
        
        addToken(type, text, startColumn)
    }
    
    /**
     * Advance position and return current character
     */
    private fun advance(): Char {
        if (isAtEnd()) return '\u0000'
        val c = source[current]
        current++
        column++
        return c
    }
    
    /**
     * Check next character without advancing
     */
    private fun peek(): Char {
        if (isAtEnd()) return '\u0000'
        return source[current]
    }
    
    /**
     * Check character two positions ahead without advancing
     */
    private fun peekNext(): Char {
        if (current + 1 >= source.length) return '\u0000'
        return source[current + 1]
    }
    
    /**
     * Check and consume character if it matches
     */
    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false
        
        current++
        column++
        return true
    }
    
    /**
     * Check if at end of file
     */
    private fun isAtEnd(): Boolean {
        return current >= source.length
    }

    /**
     * Add token without value; explicit start column provided
     */
    private fun addToken(type: TokenType, lexeme: String, startColumn: Int) {
        tokens.add(Token(type, lexeme, pos = SourcePos(line, startColumn)))
    }


    /**
     * Add token with value; explicit start column provided
     */
    private fun addToken(type: TokenType, lexeme: String, literal: Any, startColumn: Int) {
        tokens.add(Token(type, lexeme, literal, pos = SourcePos(line, startColumn)))
    }
    
    /**
     * Error handling
     */
    private fun error(column: Int, message: String) {
        throw LexerException(SourcePos(line, column), message)
    }
}

/**
 * Lexer exception
 */
class LexerException(
    val pos: SourcePos,
    message: String
) : Exception("Lexer error at ${pos.line}:${pos.column}: $message")
