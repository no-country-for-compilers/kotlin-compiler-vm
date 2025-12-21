package com.compiler

import com.compiler.parser.ast.Program

import com.compiler.parser.ast.optimizations.ConstantFolder
import com.compiler.parser.ast.optimizations.DeadCodeEliminator
import java.io.File
import kotlin.io.print
import kotlin.io.println

object AstOptimizationService {
    fun run(filePath: String): Program? {
        val file = File(filePath)

        if (!file.exists()) {
            println("Error: File not found: $filePath")
            return null
        }

        return try {
            val program = ParserService.run(filePath) ?: return null

            println()
            
            println("=== Constant Folding ===")
            val optimized_cf = ConstantFolder.fold(program)
            println("Constant Folding completed successfully.")

            println("=== Dead Code Elimination ===")
            val optimized_dce = DeadCodeEliminator.eliminate(optimized_cf)
            println("Dead Code Elimination completed successfully.")

            println()

            Printer.printOptimized(optimized_dce)

            optimized_dce
        } catch (e: Exception) {
            println("Unexpected error:")
            println("  ${e.message}")
            e.printStackTrace()
            null
        }
    }
}
