////////////////////////////////////////////////////////////////////////////////
//  Description :   This is the main file. It illustrates how an actorgroup would be created and allocated processing time in terms of ticks per interval and number of intervals
//  Author      :   Ahmed Abdel Moamen (ama883@mail.usask.ca)
//  Date        :   September 27th, 2016
//  Version     :   1.0   
////////////////////////////////////////////////////////////////////////////////


package Actorgroups

import akka.actor._
import akka.actor.Actor._
import scala.collection.JavaConversions._
import scala.util._
import java.util._
import java.util.concurrent._
import scala.concurrent._
import scala.concurrent.duration._
import com.typesafe.config.ConfigFactory
import akka.dispatch.MessageDispatcher
import akka.dispatch.Dispatcher
import akka.dispatch.Mailbox
import java.io.File


import akka.dispatch.Dispatcher

object main extends scala.App {  
 
 // load the config file
  val myConfigFile = new File("src/resources/actorgroup.conf")
  val fileConfig = ConfigFactory.parseFile(myConfigFile).getConfig("actorgroup")
  val config = ConfigFactory.load(fileConfig)

  val  actorSystem: ActorSystem = ActorSystem("default", config)
   
  // initialize four actors for testing 
  val heavyActor1 = actorSystem.actorOf(Props[HeavyActor])
  val heavyActor2 = actorSystem.actorOf(Props[HeavyActor])
  val lightActor1 = actorSystem.actorOf(Props[LightActor])
  val lightActor2 = actorSystem.actorOf(Props[LightActor])
  
  // ActorGroupExtension object is used to initialize a new ActorGroup object through the createActorGroup method
  // Two ActorGroup objects are initialized: 
  // The first actorgroup, group1, is assigned 100 ticks per interval for 5 intervals; while the second group, group2, is assigned 300 ticks per interval for 10 intervals.  
  var group1: Actorgroup = ActorgroupExtension(actorSystem).createActorgroup("group1", 100, TimeUnit.MILLISECONDS,5)
  var group2: Actorgroup = ActorgroupExtension(actorSystem).createActorgroup("group2", 300, TimeUnit.MILLISECONDS,10)

  // In this example, I define two types of managed actors: lightActor and heavyActor. 
  // lightActor executes light-weight computations which would take less execution time than the heavyActor does. 
  // Two instances of lightActor and heavyActor are registered to group1 and group2, respectively. 
  
  // add lightActor1 to group1
  group1.insertActor(lightActor1)
  // add lightActor2 to group1
  group1.insertActor(lightActor2)
  // add heavyActor1 to group2
  group2.insertActor(heavyActor1)
  // add heavyActor2 to group2
  group2.insertActor(heavyActor2)
  

  // send 10 messages to the light and heavy actors
  for {i <- 1 to 10} {
    lightActor1 ! i
    lightActor2 ! i
    heavyActor1 ! i
    heavyActor2 ! i
  }

}

class HeavyActor extends Actor {
    def receive = {
      case m => {  
        var before: Long = System.currentTimeMillis();
        var after: Long = before;
        while (after - before < 50) {
          after = System.currentTimeMillis();
        }
        println("HeavyActor --> " + m) 
      }
      
    }
  }
 
class LightActor () extends Actor {
   def receive = {
     case m => {  
        var before: Long = System.currentTimeMillis();
        var after: Long = before;
        while (after - before < 10) {
          after = System.currentTimeMillis();
        }
        println("lightActor --> " + m) 
      }
  } 
}