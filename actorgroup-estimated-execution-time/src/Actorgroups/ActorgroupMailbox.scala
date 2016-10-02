////////////////////////////////////////////////////////////////////////////////
//  Description :   This file defines the custom mailbox for cyberorg's message
//  Author      :   Ahmed Abdel Moamen (ama883@mail.usask.ca)
//  Date        :   September 27th, 2016
//  Version     :   1.0   
////////////////////////////////////////////////////////////////////////////////

//
//  package
//
package Actorgroups


//
//  import
//
import akka.actor.{ ActorRef, ActorSystem }
import com.typesafe.config.Config
import akka.dispatch.{Envelope, MessageQueue}
import java.util.concurrent.ConcurrentLinkedQueue
import akka.dispatch.sysmsg.Suspend
import akka.dispatch.sysmsg.Suspend
import akka.dispatch.Dispatcher
import java.util.ArrayList

class ActorgroupMailbox extends akka.dispatch.MailboxType {

  // This constructor signature must exist, it will be called by Akka
  def this(settings: ActorSystem.Settings, config: Config) = this()

  // The create method is called to create the MessageQueue
  final override def create(owner: Option[ActorRef], system: Option[ActorSystem]): MessageQueue =
    new MessageQueue {
    
     // randomly access queue
      private final val queue = new ArrayList[Envelope]()

      /**
     *  clean up the messages from the queue
     *  
     * @param owner: The owner of the queue
     * @param deadLetters: dead letter's message queue 
     */
      final def cleanUp(owner: ActorRef, deadLetters: MessageQueue): Unit =
      {
              while (hasMessages) {
                  deadLetters.enqueue(owner, dequeue())
              }
      }
      
      /**
     *  insert an envelope into the queue
     *  
     * @param receiver: the receiver of the message
     * @param handle: the envelope to be inserted
     */
      def enqueue(receiver: ActorRef, handle: Envelope): Unit =
      { 
         queue.add(handle)
      }
      
       /**
     *  dequeue an envelope from the queue
     */
      def dequeue(): Envelope = 
      {
        var envelope: Envelope = null
        // try to schedule the top message for execution if there is enough resource
        // if not, move to the next message 
        for( i <- 0 to queue.size - 1){
          var envelope: Envelope =  queue.get(i)
          envelope.message match  
          {
            case msg: ActorgroupMessage => // case of a cyberorg message
              {
                   // check if the current message is schedullable
                   if(ActorgroupManagerObject.getInstance().isSchedullable(msg.getReceiver(), msg.getExecutionTime()))
                   {
                      queue.remove(i) // remove it from the queue
                      return envelope  // return the message and exit and loop
                   }
              }
            case _ => // case of a system message
              {
                if(queue.size() > 0)
                {
                  queue.remove(i)
                }
                return envelope
              }
          } // end match
      } // end loop
        return envelope
    }
      
       /**
     *  return the queue size
     */
      def numberOfMessages: Int = 
      {
        queue.size
      }
      
      
       /**
     *  check if the queue is empty
     */
      def hasMessages: Boolean = 
      {
        !queue.isEmpty
      }
 
    }
}