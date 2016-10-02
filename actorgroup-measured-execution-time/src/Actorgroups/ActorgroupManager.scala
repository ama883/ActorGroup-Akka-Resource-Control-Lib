////////////////////////////////////////////////////////////////////////////////
//  Description :   This file defines the ActorgroupManager class.     
//                  The ActorGroup Manager is responsible for book-keeping about ticks consumed by managed actors as a result of delivery of messages.  A new actorgroup interested in receiving resources registers itself with the ActorGroup Manager. Once the registration request is received, the ActorGroup Manager instantly adds the actorgroup to its resource scheduling, and the actorgroup begins receiving ticks from the next interval on.  Although the current implementation does not have the ability to reserve resources not beginning immediately, a new request can be made at runtime for an actorgroup, overriding previous allocations.
//  Author      :   Ahmed Abdel Moamen (ama883@mail.usask.ca)
//  Date        :   September 27th, 2016
//  Version     :   1.0   
////////////////////////////////////////////////////////////////////////////////

//
//  package.
//
package Actorgroups

//
//  import
//

import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import java.util.ArrayList
import akka.actor.ActorRef
import scala.collection.JavaConverters._
import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorSystem
import scala.concurrent.duration.FiniteDuration
import java.io.File
import com.typesafe.config.ConfigFactory


object ActorgroupManagerObject
{
  
  var m_instance: ActorgroupManager = null;
 
  
   def getInstance(): ActorgroupManager= {
    
      if (m_instance == null) {
         m_instance = new ActorgroupManager();
         val myConfigFile = new File("src/resources/actorgroup.conf")
         val fileConfig = ConfigFactory.parseFile(myConfigFile).getConfig("actorgroup")
         val config = ConfigFactory.load(fileConfig)
         // load the interval time
          m_instance.m_interval = config.getLong("interval")
         
    }
    
    return m_instance;
  }
   
  
 class ActorgroupManager {
 
// 
//  parameters
//
   var m_lActorgroups: ArrayList[Actorgroup] = null // list of Actorgroups in this ActorSystem
   var m_interval : Long = 1 // the time interval (rate) for all Actorgroups in the system
   var m_intervalUnit: TimeUnit = TimeUnit.SECONDS // the unit of resources in this Actorgroup
   
  
   
   def getInterval(): Long =
   {
     return m_interval;
   }
   
   def getIntervalUnit(): TimeUnit =
   {
     return m_intervalUnit;
   }
   
    /**
     *  register a Actorgroup
     *  
     * @param p_Actorgroup: The Actorgroup which is added to the current Actorgroup list
     */
   def registerActorgroup(p_Actorgroup: Actorgroup)
   {
      if (m_lActorgroups == null)
      {
        // initialize the array for Actorgroups
        m_lActorgroups = new ArrayList[Actorgroup]()
      }
      m_lActorgroups.add(p_Actorgroup)
   }
   
    /** 
     * Check if an Actor has more ticks for execution
     * Called by the dispatcher before scheduling an actor for execution    
     * Returns true if the actor has can be scheduled 
     * @param p_anActor: The name of the actor to be scheduled
     */
   def isSchedullable(p_Actor: ActorRef): Boolean=
   {

     if(m_lActorgroups ne null)
     {
       for (cyber <- m_lActorgroups.asScala) {
         // if the Actorgroup which belongs to the actor is active, it returns true
            if(cyber.isActorFound(p_Actor))  
            {
              if(cyber.isActive()) 
              {
                return true
              }
              else
              {
                return false
              }
            }
       }
     }
     // return true if actor is not found to be belonging to any Actorgroup
      return true 
   }
   
   
  /**
    * Update the number ticks of a Actorgroup  
    * Called by the dispatcher in order to report an execution time by an actor belongs to this Actorgroup
    * @p_time in nanosecnds
  */
   def reportExecutionTime(p_Actor: ActorRef, p_time: Long)
   {
     if(m_lActorgroups ne null)
     {
       for (cyber <- m_lActorgroups.asScala) {
            if(cyber.isActorFound(p_Actor))  
            {
              cyber.consumeTicks(p_time) // report execution time
            }
       }
     }
   }
   
   def resetExecutionTime()
   {
     if(m_lActorgroups ne null)
     {
       for (cyber <- m_lActorgroups.asScala) {
         cyber.consumeInterval()  
           cyber.resetTicks()
       }
     }
   }
   
  
 }
 
}