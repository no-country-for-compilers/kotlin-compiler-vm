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
    
    // Operator to opcode mapping
    private data class OpcodePair(val intOp: Byte, val floatOp: Byte)
    
    private val arithmeticOps = mapOf(
        TokenType.PLUS to OpcodePair(Opcodes.ADD_INT, Opcodes.ADD_FLOAT),
        TokenType.MINUS to OpcodePair(Opcodes.SUB_INT, Opcodes.SUB_FLOAT),
        TokenType.STAR to OpcodePair(Opcodes.MUL_INT, Opcodes.MUL_FLOAT),
        TokenType.SLASH to OpcodePair(Opcodes.DIV_INT, Opcodes.DIV_FLOAT)
    )
    
    private val comparisonOps = mapOf(
        TokenType.EQ to OpcodePair(Opcodes.EQ_INT, Opcodes.EQ_FLOAT),
        TokenType.NE to OpcodePair(Opcodes.NE_INT, Opcodes.NE_FLOAT),
        TokenType.LT to OpcodePair(Opcodes.LT_INT, Opcodes.LT_FLOAT),
        TokenType.LE to OpcodePair(Opcodes.LE_INT, Opcodes.LE_FLOAT),
        TokenType.GT to OpcodePair(Opcodes.GT_INT, Opcodes.GT_FLOAT),
        TokenType.GE to OpcodePair(Opcodes.GE_INT, Opcodes.GE_FLOAT)
    )
    
    private val builtinFunctions = mapOf(
        "print" to Opcodes.PRINT,
        "printArray" to Opcodes.PRINT_ARRAY
    )
    
    private val arrayTypeToOpcode = mapOf(
        TypeNode.IntType::class to Opcodes.NEW_ARRAY_INT,
        TypeNode.FloatType::class to Opcodes.NEW_ARRAY_FLOAT,
        TypeNode.BoolType::class to Opcodes.NEW_ARRAY_BOOL
    )
    
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
                // Only POP if the expression leaves a value on the stack
                // (AssignExpr and void CallExpr don't leave values)
                if (expressionLeavesValueOnStack(stmt.expr)) {
                    ctx.builder.emit(Opcodes.POP)
                }
            }
            
            is IfStmt -> {
                generateExpression(stmt.condition)
                
                // Create labels for jump targets
                val elseLabel = if (stmt.elseBranch != null) {
                    ctx.builder.createLabel("else_${ctx.builder.currentAddress()}")
                } else {
                    null
                }
                val afterIfLabel = ctx.builder.createLabel("after_if_${ctx.builder.currentAddress()}")
                
                // Emit conditional jump - will resolve label when it's defined
                if (elseLabel != null) {
                    ctx.builder.emitJump(Opcodes.JUMP_IF_FALSE, elseLabel)
                } else {
                    ctx.builder.emitJump(Opcodes.JUMP_IF_FALSE, afterIfLabel)
                }
                
                // Generate then branch
                generateBlock(stmt.thenBranch, localVars, localIndex)
                
                // Check if then branch ends with return - if so, no need for JUMP
                val thenEndsWithReturn = ctx.builder.getLastOpcode()?.let { 
                    it == Opcodes.RETURN || it == Opcodes.RETURN_VOID 
                } ?: false
                
                // Emit unconditional jump to skip else branch (if needed)
                if (stmt.elseBranch != null && !thenEndsWithReturn) {
                    ctx.builder.emitJump(Opcodes.JUMP, afterIfLabel)
                }
                
                // Define else branch label and generate else branch
                if (stmt.elseBranch != null && elseLabel != null) {
                    ctx.builder.defineLabel(elseLabel.name)
                    generateBlock(stmt.elseBranch, localVars, localIndex)
                }
                
                // Define label after if statement
                ctx.builder.defineLabel(afterIfLabel.name)
            }
            
            is ForStmt -> {
                // Create labels for loop control
                val loopStartLabel = ctx.builder.createLabel("loop_start_${ctx.builder.currentAddress()}")
                
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
                        // Only POP if the expression leaves a value on the stack
                        if (expressionLeavesValueOnStack(init.expr)) {
                            ctx.builder.emit(Opcodes.POP)
                        }
                    }
                    is ForNoInit -> {
                        // nothing
                    }
                }
                
                // Define loop start label
                ctx.builder.defineLabel(loopStartLabel.name)
                
                // Condition
                if (stmt.condition != null) {
                    val afterLoopLabel = ctx.builder.createLabel("after_loop_${ctx.builder.currentAddress()}")
                    generateExpression(stmt.condition)
                    ctx.builder.emitJump(Opcodes.JUMP_IF_FALSE, afterLoopLabel)
                    
                    // Loop body
                    localIndex = generateBlock(stmt.body, localVars, localIndex)
                    
                    // Increment
                    if (stmt.increment != null) {
                        generateExpression(stmt.increment)
                        // Only POP if the expression leaves a value on the stack
                        if (expressionLeavesValueOnStack(stmt.increment)) {
                            ctx.builder.emit(Opcodes.POP)
                        }
                    }
                    
                    // Jump back to loop start
                    ctx.builder.emitJump(Opcodes.JUMP, loopStartLabel)
                    
                    // Define label after loop
                    ctx.builder.defineLabel(afterLoopLabel.name)
                } else {
                    // Infinite loop - no condition, no exit label needed
                    localIndex = generateBlock(stmt.body, localVars, localIndex)
                    
                    if (stmt.increment != null) {
                        generateExpression(stmt.increment)
                        // Only POP if the expression leaves a value on the stack
                        if (expressionLeavesValueOnStack(stmt.increment)) {
                            ctx.builder.emit(Opcodes.POP)
                        }
                    }
                    
                    // Jump back to loop start
                    ctx.builder.emitJump(Opcodes.JUMP, loopStartLabel)
                    // Note: No afterLoopLabel for infinite loops - they never exit
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
     * Checks if an expression leaves a value on the operand stack.
     * Returns false for expressions that consume their value (e.g., AssignExpr, void CallExpr).
     */
    private fun expressionLeavesValueOnStack(expr: Expression): Boolean {
        return when (expr) {
            is AssignExpr -> false // AssignExpr uses STORE_LOCAL/ARRAY_STORE which consume the value
            is CallExpr -> {
                val fnSymbol = globalScope.resolveFunction(expr.name)
                if (fnSymbol != null) {
                    // Built-in functions print/printArray are void and don't leave value
                    // Regular void functions also don't leave value (they use RETURN_VOID)
                    fnSymbol.returnType != com.compiler.semantic.Type.Void
                } else {
                    true // Unknown function, assume it returns a value
                }
            }
            else -> true // Other expressions (literals, variables, binary ops, etc.) leave values
        }
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
                when (expr.operator) {
                    TokenType.MINUS -> {
                        val operandType = inferExpressionType(expr.right)
                        when (operandType) {
                            Type.Int -> ctx.builder.emit(Opcodes.NEG_INT)
                            Type.Float -> ctx.builder.emit(Opcodes.NEG_FLOAT)
                            else -> throw IllegalArgumentException("Unary minus not supported for type: $operandType")
                        }
                    }
                    TokenType.PLUS -> {
                        // +x = x, do nothing
                    }
                    TokenType.NOT -> ctx.builder.emit(Opcodes.NOT)
                    else -> throw IllegalArgumentException("Unsupported unary operator: ${expr.operator}")
                }
            }
            
            is BinaryExpr -> {
                generateExpression(expr.left)
                generateExpression(expr.right)
                
                val leftType = inferExpressionType(expr.left)
                val isFloat = leftType == Type.Float
                
                when {
                    expr.operator == TokenType.PERCENT -> {
                        if (isFloat) {
                            throw IllegalArgumentException("Modulo operator % is not supported for float")
                        } else {
                            ctx.builder.emit(Opcodes.MOD_INT)
                        }
                    }
                    expr.operator in arithmeticOps -> {
                        val opcodes = arithmeticOps[expr.operator]!!
                        ctx.builder.emit(if (isFloat) opcodes.floatOp else opcodes.intOp)
                    }
                    expr.operator in comparisonOps -> {
                        val opcodes = comparisonOps[expr.operator]!!
                        ctx.builder.emit(if (isFloat) opcodes.floatOp else opcodes.intOp)
                    }
                    expr.operator == TokenType.AND -> ctx.builder.emit(Opcodes.AND)
                    expr.operator == TokenType.OR -> ctx.builder.emit(Opcodes.OR)
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
                val builtinOpcode = builtinFunctions[expr.name]
                if (builtinOpcode != null) {
                    ctx.builder.emit(builtinOpcode)
                } else {
                    val fnIndex = functionIndices[expr.name]
                        ?: throw IllegalStateException("Function ${expr.name} not found")
                    ctx.builder.emit(Opcodes.CALL, fnIndex)
                }
            }
            
            is ArrayAccessExpr -> {
                generateExpression(expr.array)
                generateExpression(expr.index)
                ctx.builder.emit(Opcodes.ARRAY_LOAD)
            }
            
            is ArrayInitExpr -> {
                generateExpression(expr.size)
                val opcode = arrayTypeToOpcode[expr.elementType::class]
                    ?: throw IllegalArgumentException("Unsupported array element type: ${expr.elementType}")
                ctx.builder.emit(opcode)
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

