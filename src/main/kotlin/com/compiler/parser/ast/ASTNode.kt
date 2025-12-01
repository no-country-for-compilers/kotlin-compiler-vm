package com.compiler.parser.ast

import com.compiler.parser.ast.Statement

/**
 * Базовый класс для всех узлов AST. Используется как общий суперкласс для выражений, операторов и
 * типов.
 */
sealed class ASTNode

// --- Program ---
/**
 * Корневой узел разобранной программы.
 *
 * @property statements список верхнеуровневых операторов и объявлений в порядке появления в
 * исходнике
 */
data class Program(val statements: List<Statement>) : ASTNode()
