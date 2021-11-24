package org.jetbrains.kotlin.priority

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

class Priority(val priority: Int) : AbstractCoroutineContextElement(Priority) {
    companion object Key : CoroutineContext.Key<Priority>
}