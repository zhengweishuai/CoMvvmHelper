package com.kk.android.comvvmhelper.extension

import kotlinx.coroutines.*

/**
 * @author kuky.
 * @description CoroutineExtensions
 */
data class CoroutineCallback(
    var initDispatcher: CoroutineDispatcher? = null,
    var block: suspend () -> Unit = {},
    var onError: (Throwable) -> Unit = {}
)

/**
 * DSL for handle CoroutineScope throwable
 */
fun CoroutineScope.safeLaunch(init: CoroutineCallback.() -> Unit): Job {
    val callback = CoroutineCallback().apply { init() }
    return launch(CoroutineExceptionHandler { _, throwable ->
        callback.onError(throwable)
    } + (callback.initDispatcher ?: GlobalScope.coroutineContext)) {
        callback.block()
    }
}

/**
 * Simply withContext
 */
suspend fun <T> launchOnMain(block: suspend CoroutineScope.() -> T) {
    withContext(Dispatchers.Main) { block() }
}

suspend fun <T> launchOnIO(block: suspend CoroutineScope.() -> T) {
    withContext(Dispatchers.IO) { block() }
}

/**
 * Extension for delay actions by coroutine
 */
fun CoroutineScope.delayLaunch(timeMills: Long, init: CoroutineScope.() -> Unit): Job {
    check(timeMills >= 0) { "timeMills must be positive" }
    return launch {
        delay(timeMills)
        init()
    }
}

/**
 * @param interval task interval
 * @param repeatCount task repeat count
 * @param delayTime task star by delayed
 * Extension for repeat task
 */
fun CoroutineScope.repeatLaunch(
    interval: Long, init: CoroutineScope.(Int) -> Unit,
    repeatCount: Int = Int.MAX_VALUE, delayTime: Long = 0L
): Job {
    check(interval > 0) { "timeDelta must be positive" }
    check(repeatCount > 0) { "repeat count must be positive" }

    return launch {
        if (delayTime > 0) delay(delayTime)

        repeat(repeatCount) {
            init(it)
            delay(interval)
        }
    }
}