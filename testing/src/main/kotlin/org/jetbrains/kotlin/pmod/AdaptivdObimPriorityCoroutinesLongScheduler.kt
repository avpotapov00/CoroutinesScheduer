//package org.jetbrains.kotlin.number.scheduler
//
//import kotlinx.atomicfu.AtomicArray
//import kotlinx.atomicfu.AtomicRef
//import kotlinx.atomicfu.atomic
//import kotlinx.atomicfu.atomicArrayOfNulls
//import org.jetbrains.kotlin.generic.smq.IndexedThread
//import org.jetbrains.kotlin.graph.dijkstra.IntNode
//import org.jetbrains.kotlin.number.smq.StealingLongMultiQueue
//import org.jetbrains.kotlin.util.secondFromLong
//import org.jetbrains.kotlin.util.zip
//import java.io.Closeable
//import java.util.concurrent.Phaser
//import java.util.concurrent.locks.LockSupport
//import kotlin.random.Random
//
///**
// * В Long храним два значения: первое это приоритет, второе это индекс вершины
// */
//class AdaptivdObimPriorityCoroutinesLongScheduler(
//    private val nodes: List<IntNode>,
//    startIndex: Int,
//    poolSize: Int,
//    stealSize: Int = 3,
//    pSteal: Double = 0.04,
//    // The number of attempts to take a task from one thread
//    private val retryCount: Int = 100
//): Closeable {
//
//    /**
//     * End of work flag
//     */
//    @Volatile
//    private var terminated = false
//
//    /**
//     * Threads serving the scheduler
//     */
//    var threads: List<Worker> = (0 until poolSize).map { index -> Worker(index) }
//
//    /**
//     * Buffer for the freshest sleeping stream
//     */
//    private val sleepingBox: AtomicRef<Worker?> = atomic(null)
//
//    /**
//     * Array where sleeping threads are stored
//     */
//    private val sleepingArray: AtomicArray<Worker?> = atomicArrayOfNulls(poolSize * 2)
//
//    private val random = Random(0)
//
//    private val finishPhaser = Phaser(poolSize + 1)
//
//    init {
//        insertGlobal(0.zip(startIndex))
//
//
//        nodes[startIndex].distance = 0
//        threads.forEach { it.start() }
//    }
//
//
//    fun waitForTermination() {
//        finishPhaser.arriveAndAwaitAdvance()
//    }
//
//    private fun tryWakeThread() {
//        var recentWorker = sleepingBox.value
//
//        // if found a thread in sleeping box, trying to get it, or go further, if someone has taken it earlier
//        while (recentWorker != null) {
//            if (sleepingBox.compareAndSet(recentWorker, null)) {
//                LockSupport.unpark(recentWorker)
//                return
//            }
//            recentWorker = sleepingBox.value
//        }
//
//        // Try to get a thread from the array several times
//        for (i in 0 until WAKE_RETRY_COUNT) {
//            val index = random.nextInt(0, sleepingArray.size)
//            recentWorker = sleepingArray[index].value
//
//            if (recentWorker != null && sleepingArray[index].compareAndSet(recentWorker, null)) {
//                LockSupport.unpark(recentWorker)
//            }
//        }
//    }
//
//    inner class Worker(override val index: Int) : IndexedThread() {
//
//        private val random = Random(0)
//
//        override fun run() {
//            var attempts = 0
//            while (!terminated) {
//
//                // trying to get from local queue
//                var task = delete()
//
//                if (task != Long.MIN_VALUE) {
//                    attempts = 0
//                    tryUpdate(nodes[task.secondFromLong])
//                    continue
//                }
//
//                if (attempts < retryCount) {
//                    attempts++
//                    continue
//                }
//
//                // if it didn't work, we try to remove it from the global queue
//                task = stealAndDeleteFromGlobal()
//
//                if (task != Long.MIN_VALUE) {
//                    attempts = 0
//                    tryUpdate(nodes[task.secondFromLong])
//                    continue
//                }
//
//                // if it didn't work, we try to remove it from the self queue
//                task = stealAndDeleteFromSelf()
//
//                if (task != Long.MIN_VALUE) {
//                    attempts = 0
//                    tryUpdate(nodes[task.secondFromLong])
//                    continue
//                }
//
//                goWait()
//                attempts = 0
//            }
//        }
//
//        private fun goWait() {
//
//            var oldThread: Worker?
//
//            do {
//                oldThread = sleepingBox.value
//            } while (!sleepingBox.compareAndSet(oldThread, this))
//
//            do {
//                val index = random.nextInt(0, sleepingArray.size)
//                val cell = sleepingArray[index].value
//            } while (!sleepingArray[index].compareAndSet(cell, oldThread))
//
//            finishPhaser.arriveAndDeregister()
//            LockSupport.park()
//            finishPhaser.register()
//        }
//
//        fun checkWakeThread() {
//            // if the number of tasks in the local queue is more than the threshold, try to wake up a new thread
//            if (size() > TASKS_COUNT_WAKE_THRESHOLD) {
//                tryWakeThread()
//            }
//        }
//
//        private fun tryUpdate(cur: IntNode) {
//            for (e in cur.outgoingEdges) {
//
//                val to = nodes[e.to]
//
//                while (cur.distance + e.weight < to.distance) {
//                    val currDist = cur.distance
//                    val toDist = to.distance
//                    val nextDist = currDist + e.weight
//
//                    if (toDist > nextDist && to.casDistance(toDist, nextDist)) {
//                        val task = nextDist.zip(e.to)
//
//                        insert(task)
//                        checkWakeThread()
//                        break
//                    }
//                }
//            }
//        }
//
//    }
//
//
//    override fun close() {
//        terminated = true
//        threads.forEach { it.interrupt() }
//        threads.forEach { it.join() }
//    }
//
//}
//
//// The threshold of tasks in the thread queue after which other threads must be woken up
//private const val TASKS_COUNT_WAKE_THRESHOLD = 30
//
//// The number of cells that we will look at trying to wake up the thread
//private const val WAKE_RETRY_COUNT = 5