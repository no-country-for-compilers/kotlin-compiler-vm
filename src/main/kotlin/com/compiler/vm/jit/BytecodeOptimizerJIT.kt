package com.compiler.vm.jit

import com.compiler.bytecode.BytecodeModule
import com.compiler.bytecode.CompiledFunction
import com.compiler.vm.CallFrame
import com.compiler.vm.CompiledFunctionExecutor
import com.compiler.vm.JITCompilerInterface
import com.compiler.vm.VMResult
import com.compiler.vm.VirtualMachine
import com.compiler.memory.MemoryManager
import com.compiler.memory.RcOperandStack
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*

/**
 * JIT compiler that optimizes bytecode instead of generating JVM bytecode.
 * 
 * Profiles function calls and when a function reaches the threshold,
 * optimizes its bytecode in the background. The optimized bytecode is then
 * used for subsequent calls.
 * 
 * @param module bytecode module containing functions to optimize
 * @param threshold number of calls after which a function should be optimized
 * @param maxParallelOptimizations maximum parallel optimizations (default: min(availableProcessors, 4))
 */
class BytecodeOptimizerJIT(
    private val module: BytecodeModule,
    private val threshold: Int = 1000,
    maxParallelOptimizations: Int = Runtime.getRuntime().availableProcessors().coerceAtMost(4)
) : JITCompilerInterface, AutoCloseable {

    private val callCounts = ConcurrentHashMap<String, AtomicInteger>()
    private val optimizedFunctions = ConcurrentHashMap<String, CompiledFunction>()
    private val inProgress = ConcurrentHashMap<String, CompletableDeferred<CompiledFunction?>>()
    
    private val functionMap: Map<String, CompiledFunction> = module.functions.associateBy { it.name }
    
    // Thread-safe map of function indices to optimized functions
    private val optimizedModuleFunctions = ConcurrentHashMap<Int, CompiledFunction>()

    private val executorService = Executors.newFixedThreadPool(maxParallelOptimizations)
    private val dispatcher = executorService.asCoroutineDispatcher()
    private val semaphore = Semaphore(maxParallelOptimizations)

    private val scope = CoroutineScope(SupervisorJob() + dispatcher + CoroutineName("bytecode-optimizer-scope"))

    @Volatile private var enabled: Boolean = true

    /**
     * Records a function call for profiling.
     * When threshold is reached, schedules background optimization.
     */
    override fun recordCall(functionName: String) {
        if (!enabled) return

        // Fast path: if already optimized, skip profiling overhead
        if (optimizedFunctions.containsKey(functionName)) return

        val counter = callCounts.computeIfAbsent(functionName) { AtomicInteger(0) }
        val newCount = counter.incrementAndGet()

        if (newCount < threshold) return

        // Double-check: function might have been optimized while we were counting
        if (optimizedFunctions.containsKey(functionName)) return

        val existing = inProgress[functionName]
        if (existing != null) return

        val deferred = CompletableDeferred<CompiledFunction?>()
        val placed = inProgress.putIfAbsent(functionName, deferred)
        if (placed != null) return

        scope.launch {
            try {
                val fn = functionMap[functionName]
                if (fn == null) {
                    deferred.complete(null)
                    return@launch
                }

                if (!semaphore.tryAcquire(5, TimeUnit.SECONDS)) {
                    deferred.completeExceptionally(
                        CancellationException("Couldn't acquire optimization slot")
                    )
                    return@launch
                }

                try {
                    val optimized = withContext(dispatcher + CoroutineName("optimize-$functionName")) {
                        optimizeFunction(fn, module)
                    }

                    if (optimized != null) {
                        val prev = optimizedFunctions.putIfAbsent(functionName, optimized)
                        if (prev == null) {
                            // Find function index in module
                            val functionIndex = module.functions.indexOfFirst { it.name == functionName }
                            if (functionIndex >= 0) {
                                optimizedModuleFunctions[functionIndex] = optimized
                            }
                        }
                    }

                    deferred.complete(optimized)
                } catch (ce: CancellationException) {
                    deferred.cancel(ce)
                } catch (t: Throwable) {
                    deferred.completeExceptionally(t)
                } finally {
                    semaphore.release()
                }
            } finally {
                inProgress.remove(functionName)
            }
        }
    }

    /**
     * Optimizes a function's bytecode.
     */
    private fun optimizeFunction(function: CompiledFunction, module: BytecodeModule): CompiledFunction? {
        try {
            return BytecodeOptimizer.optimizeFunction(function, module)
        } catch (t: Throwable) {
            System.err.println("Bytecode optimization failed for function '${function.name}': ${t.message}")
            t.printStackTrace(System.err)
            return null
        }
    }

    /**
     * Returns optimized function if available.
     * This is used by VM to get optimized bytecode for a function.
     */
    fun getOptimizedFunction(functionIndex: Int): CompiledFunction? {
        return optimizedModuleFunctions[functionIndex]
    }

    /**
     * Returns optimized executor for function if available.
     * Not used in this implementation - we replace bytecode directly.
     */
    override fun getCompiled(functionName: String): CompiledFunctionExecutor? {
        // Not used - we replace bytecode directly in module
        return null
    }

    /**
     * Returns whether JIT is enabled.
     */
    override fun isEnabled(): Boolean = enabled

    /**
     * Enables or disables JIT.
     */
    fun setEnabled(value: Boolean) {
        enabled = value
    }

    /**
     * Shuts down the optimizer.
     */
    fun shutdown() {
        scope.cancel()
        try {
            executorService.shutdown()
            executorService.awaitTermination(1, TimeUnit.SECONDS)
        } catch (ignored: InterruptedException) {}
    }

    override fun close() = shutdown()
}

