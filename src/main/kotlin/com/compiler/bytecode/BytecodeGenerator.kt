package com.compiler.bytecode

import com.compiler.parser.ast.*
import com.compiler.lexer.TokenType
import com.compiler.semantic.*
import com.compiler.domain.SourcePos

/**
 * Bytecode generator from validated AST.
 * Converts a semantically correct program into bytecode for the virtual machine.
 */
class BytecodeGenerator(
    private val program: Program,
    private val globalScope: Scope
) {
    // Constant pool
    private val intConstants = mutableListOf<Long>()
    private val floatConstants = mutableListOf<Double>()
    private val constantIndices = mutableMapOf<Any, Int>()
    
    // Compiled functions
    private val compiledFunctions = mutableListOf<CompiledFunction>()
    
    // Function information for call resolution
    private val functionIndices = mutableMapOf<String, Int>()
    
    // Current generation context
    private data class FunctionContext(
        val function: FunctionSymbol,
        val localVars: MutableMap<String, Int>, // name -> local variable index
        val builder: InstructionBuilder
    )
    
    private var currentContext: FunctionContext? = null
    
    /**
     * Generates a bytecode module from the program.
     */
    fun generate(): BytecodeModule {
        // First, collect all functions for call resolution
        collectFunctions()
        
        // Generate bytecode for each function
        for (stmt in program.statements) {
            if (stmt is FunctionDecl) {
                generateFunction(stmt)
            }
        }
        
        return BytecodeModule(
            intConstants = intConstants.toList(),
            floatConstants = floatConstants.toList(),
            functions = compiledFunctions,
            entryPoint = "main"
        )
    }
    
    /**
     * Collects information about all functions for call resolution.
     */
    private fun collectFunctions() {
        var index = 0
        for (stmt in program.statements) {
            if (stmt is FunctionDecl) {
                val fnSymbol = globalScope.resolveFunction(stmt.identifier)
                if (fnSymbol != null) {
                    functionIndices[stmt.identifier] = index++
                }
            }
        }
    }
    
    /**
     * Generates bytecode for a function.
     */
    private fun generateFunction(decl: FunctionDecl) {
        val fnSymbol = globalScope.resolveFunction(decl.identifier)
            ?: throw IllegalStateException("Function ${decl.identifier} not found in scope")
        
        val builder = InstructionBuilder()
        val localVars = mutableMapOf<String, Int>()
        
        // Parameters occupy the first indices
        var localIndex = 0
        for (param in decl.parameters) {
            localVars[param.identifier] = localIndex++
        }
        
        val prevContext = currentContext
        currentContext = FunctionContext(fnSymbol, localVars, builder)
        
        // Generate function body
        generateBlock(decl.body, localVars, localIndex)
        
        // Check if function ends with return statement
        val lastOpcode = builder.getLastOpcode()
        val endsWithReturn = lastOpcode != null && (lastOpcode == Opcodes.RETURN || lastOpcode == Opcodes.RETURN_VOID)
        
        // If void function doesn't end with return, add RETURN_VOID
        if (decl.returnType is TypeNode.VoidType && !endsWithReturn) {
            builder.emit(Opcodes.RETURN_VOID)
        }
        
        currentContext = prevContext
        
        val instructions = builder.build()
        
        compiledFunctions.add(
            CompiledFunction(
                name = decl.identifier,
                parameters = decl.parameters.map { ParameterInfo(it.identifier, it.type) },
                returnType = decl.returnType,
                localsCount = localVars.size,
                instructions = instructions
            )
        )
    }
    
    /**
     * Generates bytecode for a statement block.
     */
    private fun generateBlock(block: BlockStmt, localVars: MutableMap<String, Int>, nextLocalIndex: Int): Int {
        var localIndex = nextLocalIndex
        for (stmt in block.statements) {
            localIndex = generateStatement(stmt, localVars, localIndex)
        }
        return localIndex
    }
    
    /**
     * Generates bytecode for a statement.
     * Returns the next available index for local variables.
     */
    private fun generateStatement(stmt: Statement, localVars: MutableMap<String, Int>, nextLocalIndex: Int): Int {
        val ctx = currentContext ?: throw IllegalStateException("No current function context")
        var localIndex = nextLocalIndex
        
        when (stmt) {
            is VarDecl -> {
                generateExpression(stmt.expression)
                val varIndex = localVars.getOrPut(stmt.identifier) {
                    localIndex++
                }
                ctx.builder.emit(Opcodes.STORE_LOCAL, varIndex)
            }
            
            is ExprStmt -> {
                generateExpression(stmt.expr)
                ctx.builder.emit(Opcodes.POP)
            }
            
            is IfStmt -> {
                generateExpression(stmt.condition)
                val jumpFalseAddr = ctx.builder.currentAddress()
                ctx.builder.emit(Opcodes.JUMP_IF_FALSE, 0) // patch later
                
                generateBlock(stmt.thenBranch, localVars, localIndex)
                
                val jumpAddr = if (stmt.elseBranch != null) {
                    val addr = ctx.builder.currentAddress()
                    ctx.builder.emit(Opcodes.JUMP, 0) // patch later
                    addr
                } else {
                    null
                }
                
                val afterThenAddr = ctx.builder.currentAddress()
                ctx.builder.patchOperand(jumpFalseAddr, afterThenAddr - jumpFalseAddr)
                
                if (stmt.elseBranch != null) {
                    generateBlock(stmt.elseBranch, localVars, localIndex)
                    val afterElseAddr = ctx.builder.currentAddress()
                    ctx.builder.patchOperand(jumpAddr!!, afterElseAddr - jumpAddr)
                }
            }
            
            is ForStmt -> {
                val loopStartAddr: Int
                
                // Initializer
                when (val init = stmt.initializer) {
                    is ForVarInit -> {
                        generateExpression(init.decl.expression)
                        val varIndex = localVars.getOrPut(init.decl.identifier) {
                            localIndex++
                        }
                        ctx.builder.emit(Opcodes.STORE_LOCAL, varIndex)
                    }
                    is ForExprInit -> {
                        generateExpression(init.expr)
                        ctx.builder.emit(Opcodes.POP)
                    }
                    is ForNoInit -> {
                        // nothing
                    }
                }
                
                loopStartAddr = ctx.builder.currentAddress()
                
                // Condition
                if (stmt.condition != null) {
                    generateExpression(stmt.condition)
                    val jumpFalseAddr = ctx.builder.currentAddress()
                    ctx.builder.emit(Opcodes.JUMP_IF_FALSE, 0) // patch later
                    
                    // Loop body
                    localIndex = generateBlock(stmt.body, localVars, localIndex)
                    
                    // Increment
                    if (stmt.increment != null) {
                        generateExpression(stmt.increment)
                        ctx.builder.emit(Opcodes.POP)
                    }
                    
                    // Jump back to loop start
                    val jumpBackAddr = ctx.builder.currentAddress()
                    ctx.builder.emit(Opcodes.JUMP, loopStartAddr - jumpBackAddr)
                    
                    // Patch loop exit
                    val afterLoopAddr = ctx.builder.currentAddress()
                    ctx.builder.patchOperand(jumpFalseAddr, afterLoopAddr - jumpFalseAddr)
                } else {
                    // Infinite loop
                    localIndex = generateBlock(stmt.body, localVars, localIndex)
                    
                    if (stmt.increment != null) {
                        generateExpression(stmt.increment)
                        ctx.builder.emit(Opcodes.POP)
                    }
                    
                    val jumpBackAddr = ctx.builder.currentAddress()
                    ctx.builder.emit(Opcodes.JUMP, loopStartAddr - jumpBackAddr)
                }
            }
            
            is ReturnStmt -> {
                if (stmt.value != null) {
                    generateExpression(stmt.value)
                    ctx.builder.emit(Opcodes.RETURN)
                } else {
                    ctx.builder.emit(Opcodes.RETURN_VOID)
                }
            }
            
            is BlockStmt -> {
                localIndex = generateBlock(stmt, localVars, localIndex)
            }
            
            is FunctionDecl -> {
                // Functions are processed separately
            }
        }
        
        return localIndex
    }
    
    /**
     * Infers the type of an expression based on its structure.
     * Used to select the correct instructions (int vs float).
     */
    private fun inferExpressionType(expr: Expression): Type {
        return when (expr) {
            is LiteralExpr -> {
                when (expr.value) {
                    is Long -> Type.Int
                    is Double -> Type.Float
                    is Boolean -> Type.Bool
                    else -> Type.Unknown
                }
            }
            is VariableExpr -> {
                val symbol = resolveVariableInCurrentScope(expr.name)
                symbol?.type ?: Type.Unknown
            }
            is GroupingExpr -> inferExpressionType(expr.expression)
            is UnaryExpr -> {
                when (expr.operator) {
                    TokenType.MINUS, TokenType.PLUS -> inferExpressionType(expr.right)
                    TokenType.NOT -> Type.Bool
                    else -> Type.Unknown
                }
            }
            is BinaryExpr -> {
                when (expr.operator) {
                    TokenType.PLUS, TokenType.MINUS, TokenType.STAR, TokenType.SLASH, TokenType.PERCENT -> {
                        // Arithmetic operations return the type of operands
                        inferExpressionType(expr.left)
                    }
                    TokenType.EQ, TokenType.NE, TokenType.LT, TokenType.LE, TokenType.GT, TokenType.GE,
                    TokenType.AND, TokenType.OR -> Type.Bool
                    else -> Type.Unknown
                }
            }
            is CallExpr -> {
                val fnSymbol = globalScope.resolveFunction(expr.name)
                fnSymbol?.returnType ?: Type.Unknown
            }
            is ArrayAccessExpr -> {
                val arrayType = inferExpressionType(expr.array)
                if (arrayType is Type.Array) {
                    arrayType.elementType
                } else {
                    Type.Unknown
                }
            }
            is AssignExpr -> inferExpressionType(expr.target as? Expression ?: expr.value)
            is ArrayInitExpr -> Type.Array(expr.elementType.toSemanticType())
        }
    }
    
    /**
     * Resolves a variable in the current function context.
     */
    private fun resolveVariableInCurrentScope(name: String): VariableSymbol? {
        // First search in global scope
        return globalScope.resolveVariable(name)
    }
    
    /**
     * Generates bytecode for an expression.
     */
    private fun generateExpression(expr: Expression) {
        val ctx = currentContext ?: throw IllegalStateException("No current function context")
        
        when (expr) {
            is LiteralExpr -> {
                when (val value = expr.value) {
                    is Long -> {
                        val index = getIntConstantIndex(value)
                        ctx.builder.emit(Opcodes.PUSH_INT, index)
                    }
                    is Double -> {
                        val index = getFloatConstantIndex(value)
                        ctx.builder.emit(Opcodes.PUSH_FLOAT, index)
                    }
                    is Boolean -> {
                        ctx.builder.emit(Opcodes.PUSH_BOOL, if (value) 1 else 0)
                    }
                    else -> throw IllegalArgumentException("Unsupported literal type: ${value?.javaClass}")
                }
            }
            
            is VariableExpr -> {
                val varIndex = ctx.localVars[expr.name]
                    ?: throw IllegalStateException("Variable ${expr.name} not found in local variables")
                ctx.builder.emit(Opcodes.LOAD_LOCAL, varIndex)
            }
            
            is GroupingExpr -> {
                generateExpression(expr.expression)
            }
            
            is UnaryExpr -> {
                generateExpression(expr.right)
                val operandType = inferExpressionType(expr.right)
                when (expr.operator) {
                    TokenType.MINUS -> {
                        when (operandType) {
                            Type.Int -> ctx.builder.emit(Opcodes.NEG_INT)
                            Type.Float -> ctx.builder.emit(Opcodes.NEG_FLOAT)
                            else -> throw IllegalArgumentException("Unary minus not supported for type: $operandType")
                        }
                    }
                    TokenType.PLUS -> {
                        // +x = x, do nothing
                    }
                    TokenType.NOT -> {
                        ctx.builder.emit(Opcodes.NOT)
                    }
                    else -> throw IllegalArgumentException("Unsupported unary operator: ${expr.operator}")
                }
            }
            
            is BinaryExpr -> {
                generateExpression(expr.left)
                generateExpression(expr.right)
                
                val leftType = inferExpressionType(expr.left)
                val isFloat = leftType == Type.Float
                
                when (expr.operator) {
                    TokenType.PLUS -> {
                        if (isFloat) ctx.builder.emit(Opcodes.ADD_FLOAT) else ctx.builder.emit(Opcodes.ADD_INT)
                    }
                    TokenType.MINUS -> {
                        if (isFloat) ctx.builder.emit(Opcodes.SUB_FLOAT) else ctx.builder.emit(Opcodes.SUB_INT)
                    }
                    TokenType.STAR -> {
                        if (isFloat) ctx.builder.emit(Opcodes.MUL_FLOAT) else ctx.builder.emit(Opcodes.MUL_INT)
                    }
                    TokenType.SLASH -> {
                        if (isFloat) ctx.builder.emit(Opcodes.DIV_FLOAT) else ctx.builder.emit(Opcodes.DIV_INT)
                    }
                    TokenType.PERCENT -> {
                        if (isFloat) {
                            throw IllegalArgumentException("Modulo operator % is not supported for float")
                        } else {
                            ctx.builder.emit(Opcodes.MOD_INT)
                        }
                    }
                    
                    TokenType.EQ -> {
                        if (isFloat) ctx.builder.emit(Opcodes.EQ_FLOAT) else ctx.builder.emit(Opcodes.EQ_INT)
                    }
                    TokenType.NE -> {
                        if (isFloat) ctx.builder.emit(Opcodes.NE_FLOAT) else ctx.builder.emit(Opcodes.NE_INT)
                    }
                    TokenType.LT -> {
                        if (isFloat) ctx.builder.emit(Opcodes.LT_FLOAT) else ctx.builder.emit(Opcodes.LT_INT)
                    }
                    TokenType.LE -> {
                        if (isFloat) ctx.builder.emit(Opcodes.LE_FLOAT) else ctx.builder.emit(Opcodes.LE_INT)
                    }
                    TokenType.GT -> {
                        if (isFloat) ctx.builder.emit(Opcodes.GT_FLOAT) else ctx.builder.emit(Opcodes.GT_INT)
                    }
                    TokenType.GE -> {
                        if (isFloat) ctx.builder.emit(Opcodes.GE_FLOAT) else ctx.builder.emit(Opcodes.GE_INT)
                    }
                    
                    TokenType.AND -> ctx.builder.emit(Opcodes.AND)
                    TokenType.OR -> ctx.builder.emit(Opcodes.OR)
                    
                    else -> throw IllegalArgumentException("Unsupported binary operator: ${expr.operator}")
                }
            }
            
            is AssignExpr -> {
                when (val target = expr.target) {
                    is VariableExpr -> {
                        generateExpression(expr.value)
                        val varIndex = ctx.localVars[target.name]
                            ?: throw IllegalStateException("Variable ${target.name} not found")
                        ctx.builder.emit(Opcodes.STORE_LOCAL, varIndex)
                    }
                    is ArrayAccessExpr -> {
                        // For ARRAY_STORE we need order: array, index, value
                        generateExpression(target.array)
                        generateExpression(target.index)
                        generateExpression(expr.value)
                        ctx.builder.emit(Opcodes.ARRAY_STORE)
                    }
                }
            }
            
            is CallExpr -> {
                // Generate arguments (in declaration order)
                for (arg in expr.args) {
                    generateExpression(arg)
                }
                
                // Check if this is a built-in function
                val fnSymbol = globalScope.resolveFunction(expr.name)
                if (fnSymbol != null) {
                    when (expr.name) {
                        "print" -> {
                            ctx.builder.emit(Opcodes.PRINT)
                        }
                        "printArray" -> {
                            ctx.builder.emit(Opcodes.PRINT_ARRAY)
                        }
                        else -> {
                            val fnIndex = functionIndices[expr.name]
                                ?: throw IllegalStateException("Function ${expr.name} not found")
                            ctx.builder.emit(Opcodes.CALL, fnIndex)
                        }
                    }
                } else {
                    throw IllegalStateException("Function ${expr.name} not found")
                }
            }
            
            is ArrayAccessExpr -> {
                generateExpression(expr.array)
                generateExpression(expr.index)
                ctx.builder.emit(Opcodes.ARRAY_LOAD)
            }
            
            is ArrayInitExpr -> {
                generateExpression(expr.size)
                when (expr.elementType) {
                    is TypeNode.IntType -> ctx.builder.emit(Opcodes.NEW_ARRAY_INT)
                    is TypeNode.FloatType -> ctx.builder.emit(Opcodes.NEW_ARRAY_FLOAT)
                    is TypeNode.BoolType -> ctx.builder.emit(Opcodes.NEW_ARRAY_BOOL)
                    else -> throw IllegalArgumentException("Unsupported array element type: ${expr.elementType}")
                }
            }
        }
    }
    
    /**
     * Gets or adds an int constant to the pool.
     */
    private fun getIntConstantIndex(value: Long): Int {
        return constantIndices.getOrPut(value) {
            val index = intConstants.size
            intConstants.add(value)
            index
        }
    }
    
    /**
     * Gets or adds a float constant to the pool.
     */
    private fun getFloatConstantIndex(value: Double): Int {
        return constantIndices.getOrPut(value) {
            val index = floatConstants.size
            floatConstants.add(value)
            index
        }
    }
}

