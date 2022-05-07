package org.jetbrains.kotlin.util


internal class CircularQueue(private val size: Int) {

    var rear: Int = -1
    var front: Int = rear

    var circularQueue = LongArray(size)

    fun enQueue(queue_data: Long) //Insertion Function
    {
        if (front == 0 && rear == size - 1 ||
            rear == (front - 1) % (size - 1)
        ) // Condition if queue is full
        {
            val newArray = LongArray((size * 1.5).toInt())

            circularQueue.copyInto(newArray)
            circularQueue = newArray
        } else if (front == -1) // Condition for empty queue.
        {
            front = 0
            rear = 0
            circularQueue[rear] = queue_data
        } else if (rear == size - 1 && front != 0) {
            rear = 0
            circularQueue[rear] = queue_data
        } else {
            rear = rear + 1
            // Adding a new element if
            if (front <= rear) {
                circularQueue[rear] = queue_data
            } else {
                circularQueue[rear] = queue_data
            }
        }
    }

    fun deQueue(): Long //Dequeue Function
    {
        val temp: Long
        if (front == -1) //Checking for empty queue
        {
            return Long.MIN_VALUE
        }
        temp = circularQueue[front]
        if (front == rear) // For only one element
        {
            front = -1
            rear = -1
        } else if (front == size - 1) {
            front = 0
        } else {
            front = front + 1
        }
        return temp // Returns dequeued element
    }

    fun displayQueue() // Display the elements of queue
    {
        if (front == -1) // Check for empty queue
        {
            print("Queue is Empty")
            return
        }
        print(
            "Elements in the " +
                    "circular queue are: "
        )
        if (rear >= front) //if rear has not crossed the size limit
        {
            for (i in front..rear)  //print elements using loop
            {
                print(circularQueue[i])
                print(" ")
            }
            println()
        } else {
            for (i in front until size) {
                print(circularQueue[i])
                print(" ")
            }
            for (i in 0..rear)  // Loop for printing elements from 0th index till rear position
            {
                print(circularQueue[i])
                print(" ")
            }
            println()
        }
    }

    companion object {
        // Driver code
        @JvmStatic
        fun main(args: Array<String>) {
            val queue = CircularQueue(5) // Initialising new object of CircularQueue class.
            queue.enQueue(1)
            queue.enQueue(2)
            queue.enQueue(3)
            queue.enQueue(4)
            queue.displayQueue()
            var x = queue.deQueue()
            if (x != Long.MIN_VALUE) // Check for empty queue
            {
                print("Deleted value = ")
                println(x)
            }
            x = queue.deQueue()
            if (x != Long.MIN_VALUE) // Check for empty queue
            {
                print("Deleted value = ")
                println(x)
            }
            queue.displayQueue()
            queue.enQueue(5)
            queue.enQueue(6)
            queue.enQueue(7)
            queue.displayQueue()
            queue.enQueue(8)
        }
    }
}