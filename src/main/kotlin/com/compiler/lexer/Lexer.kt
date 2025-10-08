package com.compiler.lexer

/**
 * Lexical analyzer (Lexer/Tokenizer)
 * Converts source code into a sequence of tokens
 */
class Lexer(private val source: String) {
    private var current = 0  // current position in source
    private var line = 1     // current line
    private var column = 1   // current column
    
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
        tokens.add(Token(TokenType.EOF, "", line, column))
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
            '(' -> addToken(TokenType.LPAREN, "(")
            ')' -> addToken(TokenType.RPAREN, ")")
            '{' -> addToken(TokenType.LBRACE, "{")
            '}' -> addToken(TokenType.RBRACE, "}")
            '[' -> addToken(TokenType.LBRACKET, "[")
            ']' -> addToken(TokenType.RBRACKET, "]")
            ';' -> addToken(TokenType.SEMICOLON, ";")
            ':' -> addToken(TokenType.COLON, ":")
            ',' -> addToken(TokenType.COMMA, ",")
            '.' -> addToken(TokenType.DOT, ".")
            '+' -> addToken(TokenType.PLUS, "+")
            '-' -> addToken(TokenType.MINUS, "-")
            '*' -> addToken(TokenType.STAR, "*")
            '%' -> addToken(TokenType.PERCENT, "%")
            
            // Two-character operators
            '/' -> {
                if (match('/')) {
                    // Comment until end of line
                    while (peek() != '\n' && !isAtEnd()) {
                        advance()
                    }
                } else {
                    addToken(TokenType.SLASH, "/")
                }
            }
            
            '=' -> {
                if (match('=')) {
                    addToken(TokenType.EQ, "==")
                } else {
                    addToken(TokenType.ASSIGN, "=")
                }
            }
            
            '!' -> {
                if (match('=')) {
                    addToken(TokenType.NE, "!=")
                } else {
                    addToken(TokenType.NOT, "!")
                }
            }
            
            '<' -> {
                if (match('=')) {
                    addToken(TokenType.LE, "<=")
                } else {
                    addToken(TokenType.LT, "<")
                }
            }
            
            '>' -> {
                if (match('=')) {
                    addToken(TokenType.GE, ">=")
                } else {
                    addToken(TokenType.GT, ">")
                }
            }
            
            '&' -> {
                if (match('&')) {
                    addToken(TokenType.AND, "&&")
                } else {
                    error(line, startColumn, "Unexpected character: '$c'. Did you mean '&&'?")
                }
            }
            
            '|' -> {
                if (match('|')) {
                    addToken(TokenType.OR, "||")
                } else {
                    error(line, startColumn, "Unexpected character: '$c'. Did you mean '||'?")
                }
            }
            
            // Numbers
            in '0'..'9' -> number(startColumn)
            
            // Identifiers and keywords
            in 'a'..'z', in 'A'..'Z', '_' -> identifier(startColumn)
            
            else -> error(line, startColumn, "Unexpected character: '$c'")
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
                error(line, column, "Expected digit after exponent")
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
                addToken(TokenType.FLOAT_LITERAL, lexeme, value)
            } else {
                error(line, startColumn, "Invalid float literal: $lexeme")
            }
        } else {
            val value = lexeme.toLongOrNull()
            if (value != null) {
                addToken(TokenType.INT_LITERAL, lexeme, value)
            } else {
                error(line, startColumn, "Invalid integer literal: $lexeme (out of range for 64-bit int)")
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
        
        addToken(type, text)
    }
    
    /**
     * Advance position and return current character
     */
    private fun advance(): Char {
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
     * Add token without value
     */
    private fun addToken(type: TokenType, lexeme: String) {
        tokens.add(Token(type, lexeme, line, column - lexeme.length, null))
    }
    
    /**
     * Add token with value
     */
    private fun addToken(type: TokenType, lexeme: String, literal: Any) {
        tokens.add(Token(type, lexeme, line, column - lexeme.length, literal))
    }
    
    /**
     * Error handling
     */
    private fun error(line: Int, column: Int, message: String) {
        throw LexerException(line, column, message)
    }
}

/**
 * Lexer exception
 */
class LexerException(
    val line: Int,
    val column: Int,
    message: String
) : Exception("Lexer error at $line:$column: $message")
