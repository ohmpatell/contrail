package com.contrail.contrail.core.common

/** Wall-clock source, injectable so time-based logic is testable. */
fun interface Clock {
    fun nowMs(): Long

    companion object {
        val System = Clock { java.lang.System.currentTimeMillis() }
    }
}
