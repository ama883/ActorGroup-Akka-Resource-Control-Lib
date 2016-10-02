////////////////////////////////////////////////////////////////////////////////
//  Description :   This file defines the ActorGroup Dispatcher class. 
//                  In Akka, a message dispatcher is considered the core engine for the runtime system because it controls the processor cycles given to actors.  The dispatcher has access to the global message queue, actors' mailboxes, Mailbox is the dispatching unit in Akka, which contains one or more messages that can be processed in sequence during an interval, and the pool of threads which executes the actors.  One of the necessary configuration settings to Akka message dispatcher is throughput, which defines the number of messages delivered to an actor at one time. 
//  Author      :   Ahmed Abdel Moamen (ama883@mail.usask.ca)
//  Date        :   September 27th, 2016
//  Version     :   1.0   
////////////////////////////////////////////////////////////////////////////////


package akka.dispatch


import akka.event.Logging.Error
import akka.actor.ActorCell
import akka.event.Logging
import akka.dispatch.sysmsg.SystemMessage
import java.util.concurrent.{ ExecutorService, RejectedExecutionException }
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import java.lang.management._
import com.typesafe.config.Config
import java.util.concurrent.TimeUnit
import Actorgroups._
import akka.actor.ActorSystem
import java.util.ArrayList


/**
 * The event-based ``Dispatcher`` binds a set of Actors to a thread pool backed up by a
 * `BlockingQueue`.
 *
 * The preferred way of creating dispatchers is to define configuration of it and use the
 * the `lookup` method in [[akka.dispatch.Dispatchers]].
 *
 * @param throughput positive integer indicates the dispatcher will only process so much messages at a time from the
 *                   mailbox, without checking the mailboxes of other actors. Zero or negative means the dispatcher
 *                   always continues until the mailbox is empty.
 *                   Larger values (or zero or negative) increase throughput, smaller values increase fairness
 */
class ActorgroupDispatcher(config: Config, prerequisites: DispatcherPrerequisites)
    extends MessageDispatcherConfigurator(config, prerequisites) {
  
  private val instance = new DefaultDispatcher(
    this,
    config.getString("id"),
    config.getInt("throughput"),
    Duration(config.getDuration("throughput-deadline-time", TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS),
    configureExecutor(),
    Duration(config.getDuration("shutdown-timeout", TimeUnit.MILLISECONDS),TimeUnit.MILLISECONDS))

    override def dispatcher(): MessageDispatcher = instance
  
}

class DefaultDispatcher(
  _configurator: MessageDispatcherConfigurator,
  val id: String,
  val throughput: Int,
  val throughputDeadlineTime: Duration,
  executorServiceFactoryProvider: ExecutorServiceFactoryProvider,
  val shutdownTimeout: FiniteDuration)
  extends MessageDispatcher(_configurator) {

  import configurator.prerequisites._

  private class LazyExecutorServiceDelegate(factory: ExecutorServiceFactory) extends ExecutorServiceDelegate {
    lazy val executor: ExecutorService = factory.createExecutorService
    def copy(): LazyExecutorServiceDelegate = new LazyExecutorServiceDelegate(factory)
  }

  @volatile private var executorServiceDelegate: LazyExecutorServiceDelegate =
    new LazyExecutorServiceDelegate(executorServiceFactoryProvider.createExecutorServiceFactory(id, threadFactory))

  protected final def executorService: ExecutorServiceDelegate = executorServiceDelegate

  var m_DelayedMessage: ArrayList[Mailbox] = null // list of delayed messages
  
  val  system: ActorSystem = ActorSystem("tick")
  // Use system's dispatcher as ExecutionContext
  import system.dispatcher

// This will schedule to tick after 0ms repeating every interval
 val cancellable = system.scheduler.schedule(new FiniteDuration(0, TimeUnit.SECONDS), 
     new FiniteDuration(ActorgroupManagerObject.getInstance().getInterval(), ActorgroupManagerObject.getInstance().getIntervalUnit()))
     {
        println("Actorgroup-Tick")
        // reset the time for all Actorgroups
        ActorgroupManagerObject.getInstance().resetExecutionTime()
      
        // execute stored mailboxes first
        if((m_DelayedMessage ne null) && m_DelayedMessage.size >0)
        {
         for( i <- 0 to m_DelayedMessage.size - 1){
             val mbox = m_DelayedMessage.get(i)
             registerForExecution(mbox, true, false)
          } 
          m_DelayedMessage.clear() // clear the mailboxes after execution
        }
        
     }
  
  /**
   * INTERNAL API
   */
  protected[akka] def dispatch(receiver: ActorCell, invocation: Envelope): Unit = {
    val mbox = receiver.mailbox
    mbox.enqueue(receiver.self, invocation)
    registerForExecution(mbox, true, false)
  }

  /**
   * INTERNAL API
   */
  protected[akka] def systemDispatch(receiver: ActorCell, invocation: SystemMessage): Unit = {
    val mbox = receiver.mailbox
    mbox.systemEnqueue(receiver.self, invocation)
    registerForExecution(mbox, false, true)
  }

  /**
   * INTERNAL API
   */
  protected[akka] def executeTask(invocation: TaskInvocation) {
    try {
      executorService execute invocation
    } catch {
      case e: RejectedExecutionException =>
        try {
          executorService execute invocation
        } catch {
          case e2: RejectedExecutionException =>
            eventStream.publish(Error(e, getClass.getName, getClass, "executeTask was rejected twice!"))
            throw e2
        }
    }
  }

  /**
   * INTERNAL API
   */
  protected[akka] def createMailbox(actor: akka.actor.Cell, mailboxType: MailboxType): Mailbox = {
    new Mailbox(mailboxType.create(Some(actor.self), Some(actor.system))) with DefaultSystemMessageQueue
  }

  /**
   * INTERNAL API
   */
  protected[akka] def shutdown: Unit = {
    val newDelegate = executorServiceDelegate.copy() // Doesn't matter which one we copy
    val es = synchronized {
      val service = executorServiceDelegate
      executorServiceDelegate = newDelegate // just a quick getAndSet
      service
    }
    es.shutdown()
  }

  /**
   * Returns if it was registered
   *
   * INTERNAL API
   */
  protected[akka] override def registerForExecution(mbox: Mailbox, hasMessageHint: Boolean, hasSystemMessageHint: Boolean): Boolean = {  
    
    // if the actor is not schedullable, then insert the mailbox into the delayed mailbox queue 
    if((mbox.actor ne null) && !ActorgroupManagerObject.getInstance().isSchedullable(mbox.actor.self))
    {
      // store the mailbox
      if(m_DelayedMessage == null)
      {
         m_DelayedMessage = new ArrayList[Mailbox] ()
      }
        m_DelayedMessage.add(mbox)
      return false
    }
    
    else
    {
    
    if (mbox.canBeScheduledForExecution(hasMessageHint, hasSystemMessageHint)) { //This needs to be here to ensure thread safety and no races
      if (mbox.setAsScheduled()) {
        try {
          executorService execute mbox
          true
        } catch {
          case e: RejectedExecutionException =>
            try {
              executorService execute mbox
              true
            } catch { //Retry once
              case e: RejectedExecutionException =>
                mbox.setAsIdle()
                eventStream.publish(Error(e, getClass.getName, getClass, "registerForExecution was rejected twice!"))
                throw e
            }
        }
      } else false
    } else false
   }
    
  }   
  

  override val toString: String = Logging.simpleName(this) + "[" + id + "]"
}

object PriorityGenerator {
  /**
   * Creates a PriorityGenerator that uses the supplied function as priority generator
   */
  def apply(priorityFunction: Any => Int): PriorityGenerator = new PriorityGenerator {
    def gen(message: Any): Int = priorityFunction(message)
  }
}

/**
 * A PriorityGenerator is a convenience API to create a Comparator that orders the messages of a
 * PriorityDispatcher
 */
abstract class PriorityGenerator extends java.util.Comparator[Envelope] {
  def gen(message: Any): Int

  final def compare(thisMessage: Envelope, thatMessage: Envelope): Int =
    gen(thisMessage.message) - gen(thatMessage.message)
}
