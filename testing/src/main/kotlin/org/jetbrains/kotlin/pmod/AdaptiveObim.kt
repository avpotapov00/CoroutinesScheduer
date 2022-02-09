package org.jetbrains.kotlin.pmod

import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock
import org.jetbrains.kotlin.generic.smq.IndexedThread
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max

class AdaptiveObim<T>(
    threads: Int,
    private val chunkSize: Int = 64,
) {

    private var counter = chunkSize

    private var lmf: Int = 0

    private var perThreadStorage = MultiThreadStorage(threads) { index -> PerThreadStorage(index) }

    private val masterLog = Array<Pair<DeltaIndex, Queue<T>>?>(10_000) { null }

    private val masterLock = ReentrantLock()

    private val masterVersion = atomic(0)

    fun pushOuter(value: T, priority: Int) {
        val perItem = perThreadStorage.storages.first()

        pushLocal(perItem, priority, value)
    }

    fun push(value: T, priority: Int) {
        val perItem = perThreadStorage.get()

        pushLocal(perItem, priority, value)
    }

    private fun pushLocal(
        perItem: PerThreadStorage<T>,
        priority: Int,
        value: T
    ) {
        perItem.lock.withLock {

            perItem.maxPriority = perItem.maxPriority.coerceAtLeast(priority)
            perItem.minPriority = perItem.minPriority.coerceAtMost(priority)

            perItem.pushesLastPeriod++

            val index = DeltaIndex(priority, lmf)

            // Fast path
            val currentQueue = perItem.currentQueue
            if (index == perItem.currentIndex && currentQueue != null) {
                currentQueue.add(value)
                return
            }

            // Slow path
            val queue = updateLocalOrCreate(perItem, index)

            if (index < perItem.currentIndex) {
                perItem.popsFromSameQueue = 0

                perItem.currentIndex = index
                perItem.currentQueue = queue
            }

            queue.add(value)
        }
    }

    fun pop(): T? {
        val perItem = perThreadStorage.get()

        perItem.lock.withLock {
            perItem.sinceLastFix++
            perItem.pmodAllDeq++

            val element = perItem.currentQueue?.poll()

            if (element != null) {
                perItem.popsFromSameQueue++
                return element
            }

            return slowPop(perItem)
        }
    }

    private fun slowPop(perItem: PerThreadStorage<T>): T? {
        perItem.slowPopsLastPeriod++

        if (perItem.id == 0 &&
            perItem.sinceLastFix > counter &&
            (perItem.slowPopsLastPeriod / perItem.sinceLastFix.toDouble()) > 1.0 / chunkSize
        ) {
            merge(perItem)
        } else if (perItem.id == 0 &&
            lmf > 0 &&
            perItem.sinceLastFix > counter &&
            perItem.popsFromSameQueue > 4 * chunkSize
        ) {
            unmerge(perItem)
        }
        perItem.popsFromSameQueue = 0

        updateLocal(perItem)

        return regularPop(perItem)
    }

    private fun regularPop(perItem: PerThreadStorage<T>): T? {
        val (iterator, entry) = findTailIterator(perItem) ?: return null

        var nextEntry = entry as Map.Entry<DeltaIndex, Queue<T>>

        while (true) {
            val element = nextEntry.value.poll()

            if (element != null) {
                perItem.currentIndex = nextEntry.key
                perItem.currentQueue = nextEntry.value

                return element
            }

            if (!iterator.hasNext()) break
            nextEntry = iterator.next()
        }

        return null
    }

    private fun findTailIterator(
        perItem: PerThreadStorage<T>
    ): Pair<MutableIterator<MutableMap.MutableEntry<DeltaIndex, Queue<T>>>, Map.Entry<DeltaIndex, Queue<T>>?>? {
        val deltaIndex = DeltaIndex(0, 0)

        val iterator = perItem.local.iterator()
        var entry: Map.Entry<DeltaIndex, Queue<T>>? = null

        while (iterator.hasNext()) {
            entry = iterator.next()

            if (entry.key >= deltaIndex) {
                return Pair(iterator, entry)
            }
        }

        return null
    }

    private fun unmerge(perItem: PerThreadStorage<T>) {
        val maxShift = perItem.maxPriority ushr lmf
        val minShift = perItem.minPriority ushr lmf
        val shiftDelta = maxShift - minShift
        if ((shiftDelta < 16) && (perItem.pushesLastPeriod.toDouble() / shiftDelta > 4 * chunkSize)) {
            val delta = if (shiftDelta >= 1) shiftDelta else 1

            val xx = 16 / delta.toDouble()
            if (lmf > floor(log2(xx))) {
                lmf -= floor(log2(xx)).toInt()
            } else {
                lmf = 0
            }

            perThreadStorage.withLockAll {
                for (i in 0 until perThreadStorage.size) {
                    val other = perThreadStorage.getOther(i)
                    other.sinceLastFix = 0
                    other.slowPopsLastPeriod = 0
                    other.pushesLastPeriod = 0
                    other.priosLastPeriod = 0

                    other.minPriority = Int.MAX_VALUE
                    other.maxPriority = Int.MIN_VALUE
                }
            }
        }
        perItem.popsFromSameQueue = 0
    }

    private fun merge(perItem: PerThreadStorage<T>) {
        perThreadStorage.withLockAll {
            var priosCreatedThisPeriod = perItem.priosLastPeriod
            var numPushesThisStep = perItem.pushesLastPeriod
            var allPmodDeqCounts = perItem.pmodAllDeq

            var minOfMin = perItem.minPriority
            var maxOfMax = perItem.maxPriority

            for (i in 0 until perThreadStorage.size) {
                val other = perThreadStorage.getOther(i)
                minOfMin = minOf(minOfMin, other.minPriority)
                maxOfMax = maxOf(maxOfMax, other.maxPriority)

                priosCreatedThisPeriod += other.priosLastPeriod
                numPushesThisStep += other.pushesLastPeriod
                allPmodDeqCounts += other.pmodAllDeq

                other.sinceLastFix = 0
                other.slowPopsLastPeriod = 0
                other.pushesLastPeriod = 0
                other.priosLastPeriod = 0

                other.minPriority = Int.MAX_VALUE
                other.maxPriority = Int.MIN_VALUE
            }

            if (numPushesThisStep != 0) {
                val denominator = ((maxOfMax ushr lmf) - (minOfMin ushr lmf)).toDouble()
                val ratio = (numPushesThisStep.toDouble()) / denominator
                if (ratio < chunkSize / 2) {
                    val xx = (chunkSize.toDouble()) / (numPushesThisStep.toDouble() / denominator)

                    lmf += floor(log2(xx)).toInt()
                    counter *= 2
                }
            }
        }
    }

    private fun updateLocalOrCreate(perThreadStorage: PerThreadStorage<T>, deltaIndex: DeltaIndex): Queue<T> {
        val queue: Queue<T>? = perThreadStorage.local[deltaIndex]

        if (queue != null) {
            return queue
        }

        return slowUpdateLocalOrCreate(perThreadStorage, deltaIndex)
    }

    private fun slowUpdateLocalOrCreate(
        perThreadStorage: PerThreadStorage<T>,
        deltaIndex: DeltaIndex
    ): Queue<T> {
        //update local until we find it or we get the write lock
        do {
            updateLocal(perThreadStorage)
            val queue = perThreadStorage.local[deltaIndex]

            if (queue != null) {
                return queue
            }
        } while (!masterLock.tryLock())

        //we have the write lock, update again then create
        updateLocal(perThreadStorage)
        var queue = perThreadStorage.local[deltaIndex]

        if (queue == null) {
            queue = ConcurrentLinkedQueue()
            perThreadStorage.lastMasterVersion = masterVersion.value + 1

            masterLog[masterVersion.value] = (deltaIndex to queue)
            masterVersion.getAndIncrement()

            perThreadStorage.local[deltaIndex] = queue

            perThreadStorage.priosLastPeriod++
        }

        masterLock.unlock()
        return queue
    }

    private fun updateLocal(perItem: PerThreadStorage<T>): Boolean {
        if (perItem.lastMasterVersion != masterVersion.value) {
            while (perItem.lastMasterVersion < masterVersion.value) {

                val (deltaIndex, queue) = masterLog[perItem.lastMasterVersion]!!

                perItem.local[deltaIndex] = queue

                perItem.lastMasterVersion++
            }
            return true
        }
        return false
    }


    class PerThreadStorage<T>(
        val id: Int
    ) {

        var currentQueue: Queue<T>? = null

        val lock = ReentrantLock()

        val local: TreeMap<DeltaIndex, Queue<T>> = TreeMap()

        var lastMasterVersion: Int = 0

        var pushesLastPeriod: Int = 0

        var popsFromSameQueue: Int = 0

        var priosLastPeriod: Int = 0

        var sinceLastFix: Int = 0

        var pmodAllDeq: Int = 0

        var slowPopsLastPeriod: Int = 0

        var maxPriority: Int = Int.MIN_VALUE

        var minPriority: Int = Int.MAX_VALUE

        var currentIndex: DeltaIndex = DeltaIndex(0, 0)

    }

    inner class MultiThreadStorage(
        threads: Int,
        initial: (Int) -> PerThreadStorage<T>,
    ) {

        val size = threads

        val storages = Array(threads) { initial(it) }

        fun get(): PerThreadStorage<T> {
            return storages[(Thread.currentThread() as IndexedThread).index]
        }

        fun getOther(index: Int): PerThreadStorage<T> {
            return storages[index]
        }

        inline fun withLockAll(action: () -> Unit) {
            try {
                storages.forEach { it.lock.lock() }
                action()
            } finally {
                storages.forEach { it.lock.unlock() }
            }
        }

    }

}

class DeltaIndex(
    priority: Int,
    private val l: Int
) : Comparable<DeltaIndex> {

    private val p: Int = priority ushr l

    override fun compareTo(other: DeltaIndex): Int {
        val maxL = max(l, other.l)
        val myShift = p ushr (maxL - l)
        val otherShift = other.p ushr (maxL - other.l)

        if (myShift < otherShift) return -1
        if (myShift > otherShift) return 1
        if (l < other.l) return -1
        if (l > other.l) return 1

        return 0
    }


    override fun toString(): String {
        return "TaskPriority(p=$p, l=$l)"
    }

    override fun hashCode(): Int {
        var result = l
        result = 31 * result + p
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DeltaIndex

        return compareTo(other) == 0
    }

}