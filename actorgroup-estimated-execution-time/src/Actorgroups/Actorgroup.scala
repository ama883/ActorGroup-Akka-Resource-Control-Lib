////////////////////////////////////////////////////////////////////////////////
//  Description :   This file defines the Actorgroup class.
//                  Actorgroups are resource encapsulations for related groups of actors. Each of the actors encapsulated in an actorgroup is called as a managed actor. A managed actor must be registered in an actorgroup in order to consume its allocated resources. We manage the resource usage of an actorgroup by controlling the flow of messages sent to its managed actors.
//                  The resource we control is CPU time, counted in 1-millisecond ticks.  Ticks are consumed by managed actors to execute computations triggered by the arrival of messages.  Allocations are made to actorgroups within recurring time intervals.  If a tick available to an actorgroup in an interval is not consumed, it expires. Allocations to actorgroups are in the form of (ticks-per-interval, number-of-intervals) pairs, and are intended to be applied immediately.   Ticks allocated to (and consequently owned by) an actorgroup are shared among its managed actors. An actorgroup is marked as inactive after its assigned ticks are consumed by its managed actors.
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

import akka.actor.{ActorSystem, ActorLogging, Actor, Props, UntypedActor}
import akka.actor.ActorRef
import akka.event.Logging
import scala.collection.JavaConversions._
import java.util.ArrayList
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import akka.actor.Extension
import akka.actor.ActorSystem
import akka.actor.ExtensionId
import akka.actor.ExtensionIdProvider
import akka.actor.ExtendedActorSystem


object ActorgroupExtension extends ExtensionId[Actorgroup] with ExtensionIdProvider {
  // The lookup method is required by ExtensionIdProvider,
  // so we return ourselves here, this allows us
  // to configure our extension to be loaded when
  // the ActorSystem starts up
  override def lookup = ActorgroupExtension
 
  //This method will be called by Akka to instantiate the Extension
  override def createExtension(system: ExtendedActorSystem) = new Actorgroup
 
  /**
   * Java API: retrieve the Actorgroup extension for the given system
   */
  override def get(system: ActorSystem): Actorgroup = super.get(system)
  
  
}

class Actorgroup () extends Extension {
  
//
//  parameters
//
  @volatile
  protected var m_lTicks : Long = 0 // resources in this Actorgroup
  protected var m_lTicksCounter : Long = 0 // counter for the resources in this Actorgroup
  protected var m_rateUnit: TimeUnit = TimeUnit.MILLISECONDS // the unit of resources in this Actorgroup
  protected var m_numOfIntervals : Long = 0 // total number of intervals
  protected var m_name : String = "" // an optional name for the Actorgroup
  protected var m_llActors: ArrayList[ActorRef] = null // list of actors in this Actorgroup
  @volatile
  protected var m_bIsActive: Boolean = false // it is true if the Actorgroup still has some ticks 
 
  /**
   * A secondary constructor
   * This constructor creates a new instance of Actorgroup with known resource specification
   */
  def this(p_name: String , p_lTicks:Long ,  p_rateUnit: TimeUnit, p_numOfIntervals: Long) {
    this();
     //   Set the values of member variables
        m_name = p_name;
        m_lTicks = p_lTicks
        m_rateUnit = p_rateUnit;
        m_bIsActive = true;
        m_llActors = new ArrayList[ActorRef] ()
        
        // convert ticks to Nano seconds
        m_lTicks = TimeUnit.NANOSECONDS.convert(m_lTicks, p_rateUnit)
        m_lTicksCounter = m_lTicks
        m_numOfIntervals= p_numOfIntervals
        // register the Actorgroup with the ActorgroupManager
        ActorgroupManagerObject.getInstance().registerActorgroup(this)
  }
  
  /**
   * Create an instance of Actorgroup
   */
  def createActorgroup(p_name: String , p_lTicks:Long ,  p_rateUnit: TimeUnit, p_numOfIntervals: Long): Actorgroup = 
    new Actorgroup(p_name, p_lTicks, p_rateUnit, p_numOfIntervals)
  
  /**
   * Create an instance of ActorgroupMessage
   */
  def createActorgroupMessage(p_message: Any, p_receiver: ActorRef, p_executionTime: Long, p_unit: TimeUnit):ActorgroupMessage = 
    new ActorgroupMessage(p_message, p_receiver , p_executionTime, p_unit)
  
  
    /**
     *  Insert an actor to the actors list
     *  
     * @param p_anToInsert: The name of the actor which is added to the current actor list
     */
    def insertActor(p_anToInsert: ActorRef){
        m_llActors.add(p_anToInsert);
    }
    
     /** Reset the total number of ticks (resources) for this Actorgroup
      */
    def resetTicks(){
        m_lTicksCounter = m_lTicks;
    }
    
   /**
    * Update the number ticks and deactivate the Actorgroup if it ran out of resources 
    * Called by the ActorgroupManager when reporting execution time by an actor belongs to this Actorgroup
    * @p_time in nanosecnds
   */
    def consumeTicks(p_time: Long){
      synchronized{
        m_lTicksCounter -= p_time
      }   
    }
    
    
     /**
    * Update the number intervals and deactivate the Actorgroup if it ran out of intervals 
    * Called by the ActorgroupManager when reporting an interval has ellapsed
   */
    def consumeInterval(){
      synchronized{
        m_numOfIntervals -= 1
      }   
   }
     
    /** 
     * Delete an Actor when it is distroied
     * 
     * @param p_anActor The name of the actor to be deleted
     */
    
    def deleteActor(p_anActor: ActorRef){
       m_llActors.remove(p_anActor);
    }
    
    /** 
     * Check if an Actor belongs to this Actorgroup
     * Returns true if the actor is found
     * @param p_anActor: The name of the actor to be searched
     */
    def isActorFound(p_anActor: ActorRef): Boolean ={ 
      for( i <- 0 to m_llActors.size - 1){
            if(m_llActors(i).equals(p_anActor))
              return true
        }
      return false
    }
     
    
    /**
     * Returns the actor list
     * @return The actor list of the current Actorgroup
     */
    def getActors(): ArrayList[ActorRef] ={
      return m_llActors
    }
    
    
     /**
     * Returns the amount of resources the current Actorgroup has
     * @return The amount of resources
     */
    def getTicks(): Long ={
      return m_lTicks
    }
    
    /**
     * Returns the amount of resources the current Actorgroup has
     * @return The amount of resources
     */
    def getTickCounter(): Long ={
      return m_lTicksCounter
    }
    
   /** 
     * Check weather the Actorgroup is active or not
     * Returns true if the Actorgroup is active
     */
    def isActive(): Boolean ={  
      synchronized{
      if(m_numOfIntervals <=0 || m_lTicksCounter <= 0)
           return false
      else
        return true
      }
    }
   
 
}

class ActorgroupMessage {
  
//  parameters
  private var message : Any = null 
  private var receiver : ActorRef = null 
  private var executionTime : Long = -1 // resources in this message
  private var unit: TimeUnit = TimeUnit.MILLISECONDS
  
  def this(p_message: Any, p_receiver: ActorRef, p_executionTime: Long, p_unit: TimeUnit)
  {
   this()
    message = p_message
    receiver = p_receiver
    unit = p_unit
    // convert resources to Nano seconds
    executionTime = TimeUnit.NANOSECONDS.convert(p_executionTime, unit)
  }
  
   /** 
     * Returns the message's content
     */
  def getMessage(): Any={
    return message
  }
  
  /** 
     * Returns the message's receiver
  */
  def getReceiver(): ActorRef={
    return receiver
  }
  
   /** 
     * Returns the message's execution time
     */
  def getExecutionTime(): Long={
    return executionTime
  }
  

}