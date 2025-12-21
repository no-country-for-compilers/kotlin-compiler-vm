package com.compiler.parser.ast.optimizations

import com.compiler.parser.ast.Program

interface AstOptimization {
    val name: String
    fun apply(program: Program): Program
}