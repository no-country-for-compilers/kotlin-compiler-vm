package com.compiler.domain

/** 
 * Represents a position in source code (1-based).
 */
data class SourcePos(val line: Int, val column: Int)