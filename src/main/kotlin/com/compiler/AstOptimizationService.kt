package com.compiler

import com.compiler.parser.ast.Program

import com.compiler.parser.ast.optimizations.ConstantFolder
import com.compiler.parser.ast.optimizations.DeadCodeEliminator
import java.io.File
import kotlin.io.print
import kotlin.io.println

object AstOptimizationService {

    private val optimizationMap =
        mapOf(
                OptimizationType.CF to ConstantFolder,
                OptimizationType.DCE to DeadCodeEliminator
        )

    fun run(
        filePath: String,
        optimizations: List<OptimizationType> = listOf(
            OptimizationType.CF,
            OptimizationType.DCE
        )
    ): Program? {
        val file = File(filePath)

        if (!file.exists()) {
            println("Error: File not found: $filePath")
            return null
        }

        return try {
            var program = ParserService.run(filePath) ?: return null

            println()

            for (optType in optimizations) {
                val opt = optimizationMap[optType] ?: continue
                println("=== ${opt.name} ===")
                program = opt.apply(program)
                println("${opt.name} completed successfully.")
            }

            println()

            Printer.printOptimized(program)

            program
        } catch (e: Exception) {
            println("Unexpected error:")
            println("  ${e.message}")
            e.printStackTrace()
            null
        }
    }
}
