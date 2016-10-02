////////////////////////////////////////////////////////////////////////////////////////////////////
//  File        :   ActorgroupRunnable.scala
//  Description :   This file defines the class ActorgroupRunnable which works as a wrapper of the
//                  mailbox of an Akka actor. The mailbox is wrapped so that the CPU time consumed
//                  for processing all the messages from the mailbox can be measured.
//                  When a message is sent to an actor, the dispatcher first places it in the global message queue.  When it is that actor's turn to be executed, the dispatcher queries the ActorGroup Manager about whether the receiving actor is schedulable, by checking whether its owner actorgroup is still active (i.e., still owns ticks in the current interval). If the receiving actor is schedulable, its mailbox is wrapped into an idle thread from the thread pool to create an ActorGroup Runnable. The dispatcher then moves the right number of messages for that actor -- as determined by the throughput setting -- from the global queue to the actor's mailbox, and finally tells the runnable to execute the actor for those messages.
//  Version     :   1.0.0
//  Author      :   Sander Wang
//  Date        :   2015/12/23
////////////////////////////////////////////////////////////////////////////////////////////////////

//
//  It has to be a system package because some internal classes of Akka are used.
//
package akka.dispatch

import java.lang.management._
import Actorgroups.ActorgroupManagerObject


//
//  Name        : class ActorgroupRunnable
//  Description : A wrapper of the mailbox of an Akka actor, which is used for measuring the
//                messaging processing time of the mailbox.
//  Arguments   : mailbox  - reference to the mailbox of an Akka actor.
//
class ActorgroupRunnable(val mailbox: Mailbox) extends Runnable {
    //
    //  Name        :   run
    //  Description :   The method that is called by the executing thread.
    //  Arguments   :   N/A
    //  Return      :   N/A
    //
    override def run(): Unit = {
        var executionTime: Long = -1
        
        //
        //  If the Java virtual machine supports CPU time measurement for the current thread, try
        //  to calculate the CPU time which could be more accurate. However, due to the precision
        //  limitation of the system clock, the calculated CPU time could be zero when the runnable
        //  is small, in which case the absolute system time passed is used.
        //
        val bean: ThreadMXBean = ManagementFactory.getThreadMXBean()
        if (bean.isCurrentThreadCpuTimeSupported()) {
            var cpuTimeBefore: Long = bean.getCurrentThreadCpuTime()
            var absTimeBefore: Long = System.nanoTime()
            mailbox.run()
            var usedAbsTime: Long = System.nanoTime() - absTimeBefore
            var usedCpuTime: Long = bean.getCurrentThreadCpuTime() - cpuTimeBefore
            
            if (usedCpuTime > 0) {
                executionTime = usedCpuTime
            } else {
                executionTime = usedAbsTime
            }
        }
        //
        //  If the CPU time measurement is not supported, calculate the absolute system time passed
        //  during the execution. This may be inaccurate especially when there are some blocking
        //  involved during the execution.
        //
        else {
            var absTimeBefore: Long = System.nanoTime()
            mailbox.run()
            executionTime = System.nanoTime() - absTimeBefore;
        }
        
        //
        //  Report the execution time.
        //
        if ((mailbox.actor ne null)) {
            ActorgroupManagerObject.getInstance().reportExecutionTime(mailbox.actor.self, executionTime);

        }
    }
}
