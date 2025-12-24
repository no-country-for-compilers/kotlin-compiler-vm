package com.compiler

import com.compiler.semantic.DefaultSemanticAnalyzer
import com.compiler.semantic.SemanticException
import com.compiler.semantic.AnalysisResult
import com.compiler.AstOptimizationService
import java.io.File

object SemanticService {

    fun run(filePath: String): AnalysisResult? {
        val file = File(filePath)

        if (!file.exists()) {
            println("Error: File not found: $filePath")
            return null
        }

        return try {
            val program = AstOptimizationService.run(filePath) ?: return null

            val analyzer = DefaultSemanticAnalyzer()
            val result = analyzer.analyze(program)

            println()

            Printer.printSemanticAnalysis(result)

            result
        } catch (e: SemanticException) {
            println("Semantic Error:")
            println("  ${e.message}")
            null
        } catch (e: Exception) {
            println("Unexpected error:")
            println("  ${e.message}")
            e.printStackTrace()
            null
        }
    }
}



