package com.compiler.lexer

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class LexerTest {
    
    private fun tokenize(source: String): List<Token> {
        return Lexer(source).tokenize()
    }
    
    private fun tokenTypes(source: String): List<TokenType> {
        return tokenize(source).map { it.type }
    }
    
    @Test
    fun `test empty source`() {
        val tokens = tokenize("")
        assertEquals(1, tokens.size)
        assertEquals(TokenType.EOF, tokens[0].type)
    }
    
    @Test
    fun `test keywords`() {
        val source = "let func if else for return true false"
        val types = tokenTypes(source)
        
        assertEquals(
            listOf(
                TokenType.LET,
                TokenType.FUNC,
                TokenType.IF,
                TokenType.ELSE,
                TokenType.FOR,
                TokenType.RETURN,
                TokenType.TRUE,
                TokenType.FALSE,
                TokenType.EOF
            ),
            types
        )
    }
    
    @Test
    fun `test type keywords`() {
        val source = "int float bool void"
        val types = tokenTypes(source)
        
        assertEquals(
            listOf(
                TokenType.TYPE_INT,
                TokenType.TYPE_FLOAT,
                TokenType.TYPE_BOOL,
                TokenType.TYPE_VOID,
                TokenType.EOF
            ),
            types
        )
    }
    
    @Test
    fun `test identifiers`() {
        val source = "x myVar _test var123 myVariable_name"
        val types = tokenTypes(source)
        
        assertEquals(
            List(5) { TokenType.IDENTIFIER } + TokenType.EOF,
            types
        )
    }
    
    @Test
    fun `test integer literals`() {
        val source = "0 42 123 9223372036854775807"
        val tokens = tokenize(source)
        
        assertEquals(0L, tokens[0].literal)
        assertEquals(42L, tokens[1].literal)
        assertEquals(123L, tokens[2].literal)
        assertEquals(9223372036854775807L, tokens[3].literal) // max Long
    }
    
    @Test
    fun `test float literals`() {
        val source = "3.14 0.5 1.0e10 3.14e-5 2e+8"
        val tokens = tokenize(source)
        
        assertEquals(TokenType.FLOAT_LITERAL, tokens[0].type)
        assertEquals(3.14, tokens[0].literal)
        
        assertEquals(TokenType.FLOAT_LITERAL, tokens[1].type)
        assertEquals(0.5, tokens[1].literal)
        
        assertEquals(TokenType.FLOAT_LITERAL, tokens[2].type)
        assertEquals(1.0e10, tokens[2].literal)
        
        assertEquals(TokenType.FLOAT_LITERAL, tokens[3].type)
        assertEquals(3.14e-5, tokens[3].literal)
        
        assertEquals(TokenType.FLOAT_LITERAL, tokens[4].type)
        assertEquals(2e+8, tokens[4].literal)
    }
    
    @Test
    fun `test operators`() {
        val source = "+ - * / %"
        val types = tokenTypes(source)
        
        assertEquals(
            listOf(
                TokenType.PLUS,
                TokenType.MINUS,
                TokenType.STAR,
                TokenType.SLASH,
                TokenType.PERCENT,
                TokenType.EOF
            ),
            types
        )
    }
    
    @Test
    fun `test comparison operators`() {
        val source = "== != < <= > >="
        val types = tokenTypes(source)
        
        assertEquals(
            listOf(
                TokenType.EQ,
                TokenType.NE,
                TokenType.LT,
                TokenType.LE,
                TokenType.GT,
                TokenType.GE,
                TokenType.EOF
            ),
            types
        )
    }
    
    @Test
    fun `test logical operators`() {
        val source = "&& || !"
        val types = tokenTypes(source)
        
        assertEquals(
            listOf(
                TokenType.AND,
                TokenType.OR,
                TokenType.NOT,
                TokenType.EOF
            ),
            types
        )
    }
    
    @Test
    fun `test delimiters`() {
        val source = "( ) { } [ ] ; : , ."
        val types = tokenTypes(source)
        
        assertEquals(
            listOf(
                TokenType.LPAREN,
                TokenType.RPAREN,
                TokenType.LBRACE,
                TokenType.RBRACE,
                TokenType.LBRACKET,
                TokenType.RBRACKET,
                TokenType.SEMICOLON,
                TokenType.COLON,
                TokenType.COMMA,
                TokenType.DOT,
                TokenType.EOF
            ),
            types
        )
    }
    
    @Test
    fun `test variable declaration`() {
        val source = "let x: int = 42;"
        val types = tokenTypes(source)
        
        assertEquals(
            listOf(
                TokenType.LET,
                TokenType.IDENTIFIER,
                TokenType.COLON,
                TokenType.TYPE_INT,
                TokenType.ASSIGN,
                TokenType.INT_LITERAL,
                TokenType.SEMICOLON,
                TokenType.EOF
            ),
            types
        )
    }
    
    @Test
    fun `test function declaration`() {
        val source = """
            func factorial(n: int): int {
                return n;
            }
        """.trimIndent()
        
        val types = tokenTypes(source)
        
        assertEquals(
            listOf(
                TokenType.FUNC,
                TokenType.IDENTIFIER, // factorial
                TokenType.LPAREN,
                TokenType.IDENTIFIER, // n
                TokenType.COLON,
                TokenType.TYPE_INT,
                TokenType.RPAREN,
                TokenType.COLON,
                TokenType.TYPE_INT,
                TokenType.LBRACE,
                TokenType.RETURN,
                TokenType.IDENTIFIER, // n
                TokenType.SEMICOLON,
                TokenType.RBRACE,
                TokenType.EOF
            ),
            types
        )
    }
    
    @Test
    fun `test expression`() {
        val source = "5 + 3 * 2"
        val types = tokenTypes(source)
        
        assertEquals(
            listOf(
                TokenType.INT_LITERAL,
                TokenType.PLUS,
                TokenType.INT_LITERAL,
                TokenType.STAR,
                TokenType.INT_LITERAL,
                TokenType.EOF
            ),
            types
        )
    }
    
    @Test
    fun `test array literal`() {
        val source = "[1, 2, 3, 4, 5]"
        val types = tokenTypes(source)
        
        assertEquals(
            listOf(
                TokenType.LBRACKET,
                TokenType.INT_LITERAL,
                TokenType.COMMA,
                TokenType.INT_LITERAL,
                TokenType.COMMA,
                TokenType.INT_LITERAL,
                TokenType.COMMA,
                TokenType.INT_LITERAL,
                TokenType.COMMA,
                TokenType.INT_LITERAL,
                TokenType.RBRACKET,
                TokenType.EOF
            ),
            types
        )
    }
    
    @Test
    fun `test line comments`() {
        val source = """
            let x: int = 42; // это комментарий
            let y: int = 10; // еще комментарий
        """.trimIndent()
        
        val types = tokenTypes(source)
        
        // Комментарии должны быть проигнорированы
        assertEquals(
            listOf(
                TokenType.LET,
                TokenType.IDENTIFIER,
                TokenType.COLON,
                TokenType.TYPE_INT,
                TokenType.ASSIGN,
                TokenType.INT_LITERAL,
                TokenType.SEMICOLON,
                TokenType.LET,
                TokenType.IDENTIFIER,
                TokenType.COLON,
                TokenType.TYPE_INT,
                TokenType.ASSIGN,
                TokenType.INT_LITERAL,
                TokenType.SEMICOLON,
                TokenType.EOF
            ),
            types
        )
    }
    
    @Test
    fun `test factorial function`() {
        val source = """
            func factorial(n: int): int {
                if (n <= 1) {
                    return 1;
                } else {
                    return n * factorial(n - 1);
                }
            }
        """.trimIndent()
        
        val tokens = tokenize(source)
        
        // Проверяем, что токенизация прошла успешно
        assert(tokens.isNotEmpty())
        assertEquals(TokenType.FUNC, tokens[0].type)
        assertEquals(TokenType.EOF, tokens.last().type)
    }
    
    @Test
    fun `test big number for factorial 20`() {
        val source = "let result: int = 2432902008176640000;"
        val tokens = tokenize(source)
        
        assertEquals(2432902008176640000L, tokens[5].literal)
    }
    
    @Test
    fun `test position tracking`() {
        val source = "let x: int = 42;"
        val tokens = tokenize(source)
        
        // Все токены должны быть на строке 1
        tokens.forEach { token ->
            if (token.type != TokenType.EOF) {
                assertEquals(1, token.line)
            }
        }
    }
    
    @Test
    fun `test multiline position tracking`() {
        val source = """
            let x: int = 42;
            let y: int = 10;
        """.trimIndent()
        
        val tokens = tokenize(source)
        
        // Первая let на строке 1
        assertEquals(1, tokens[0].line)
        
        // Вторая let на строке 2
        val secondLet = tokens.first { it.lexeme == "let" && it.line == 2 }
        assertEquals(2, secondLet.line)
    }
    
    @Test
    fun `test invalid character`() {
        val source = "let x: int = @;"
        
        assertThrows<LexerException> {
            tokenize(source)
        }
    }
    
    @Test
    fun `test single ampersand error`() {
        val source = "a & b"
        
        val exception = assertThrows<LexerException> {
            tokenize(source)
        }
        
        assert(exception.message!!.contains("Did you mean '&&'?"))
    }
    
    @Test
    fun `test single pipe error`() {
        val source = "a | b"
        
        val exception = assertThrows<LexerException> {
            tokenize(source)
        }
        
        assert(exception.message!!.contains("Did you mean '||'?"))
    }
    
    @Test
    fun `test integer overflow`() {
        val source = "let x: int = 99999999999999999999999999999;"
        
        assertThrows<LexerException> {
            tokenize(source)
        }
    }
}
