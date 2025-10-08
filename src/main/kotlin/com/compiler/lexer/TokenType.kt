package com.compiler.lexer

/**
 * Token types in the language
 */
enum class TokenType {
    // Keywords
    LET,        // let
    FUNC,       // func
    IF,         // if
    ELSE,       // else
    FOR,        // for
    RETURN,     // return
    TRUE,       // true
    FALSE,      // false
    
    // Type keywords
    TYPE_INT,   // int
    TYPE_FLOAT, // float
    TYPE_BOOL,  // bool
    TYPE_VOID,  // void
    
    // Identifiers and literals
    IDENTIFIER, // variable and function names
    INT_LITERAL,    // 42, 1234567890
    FLOAT_LITERAL,  // 3.14, 1.0e10
    
    // Arithmetic operators
    PLUS,       // +
    MINUS,      // -
    STAR,       // *
    SLASH,      // /
    PERCENT,    // %
    
    // Comparison operators
    EQ,         // ==
    NE,         // !=
    LT,         // <
    LE,         // <=
    GT,         // >
    GE,         // >=
    
    // Logical operators
    AND,        // &&
    OR,         // ||
    NOT,        // !
    
    // Assignment
    ASSIGN,     // =
    
    // Delimiters
    SEMICOLON,  // ;
    COLON,      // :
    COMMA,      // ,
    DOT,        // .
    
    // Brackets
    LPAREN,     // (
    RPAREN,     // )
    LBRACE,     // {
    RBRACE,     // }
    LBRACKET,   // [
    RBRACKET,   // ]
    
    // Special
    EOF,        // end of file
    NEWLINE     // newline (for position tracking)
}

