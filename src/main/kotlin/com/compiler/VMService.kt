package com.compiler

import com.compiler.bytecode.BytecodeModule
import com.compiler.vm.VirtualMachine
import com.compiler.vm.VMResult
import com.compiler.vm.jit.JITCompiler
import java.io.File

object VMService {

    fun run(filePath: String) {
        val file = File(filePath)

        if (!file.exists()) {
            println("Error: File not found: $filePath")
            return
        }

        // Compile to bytecode
        val module = BytecodeService.run(filePath)
        if (module == null) {
            // BytecodeService already printed error message
            return
        }

        // Execute on virtual machine
        val jit = JITCompiler(module)
        try {
            val vm = VirtualMachine(module, jit)
            val result = vm.execute()

            if (result != VMResult.SUCCESS) {
                println("VM Error: ${result.name}")
                return
            }
        } catch (e: Exception) {
            println("Unexpected error during execution:")
            println("  ${e.message}")
            e.printStackTrace()
        } finally {
            jit.shutdown()
        }
    }

    fun run(module: BytecodeModule) {
        try {
            val vm = VirtualMachine(module)
            val result = vm.execute()

            if (result != VMResult.SUCCESS) {
                println("VM Error: ${result.name}")
            }
        } catch (e: Exception) {
            println("Unexpected error during execution:")
            println("  ${e.message}")
            e.printStackTrace()
        }
    }
}

