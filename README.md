# ActorGroup-Akka-Resource-Control-Lib

Although there are models and prototype implementations for controlling resource use in Actor system, they are difficult to implement for production implementations of Actors such as Akka.  This is because the messaging and scheduling infrastructures of runtime systems are increasingly complex and significantly different from one system to another. 

This is a library for supporting resource control for Actor Systems in Akka. Particularly, given the lack of support in Akka for direct scheduling of actors, we compare two different ways of approximating actor-level control support.  The first implementation expects messages to actors to provide estimates of resources likely to be consumed for processing them; these estimates are then relied upon to make scheduling decisions.  In the second implementation, resource use of scheduled actors is tracked, and compared against allocations to decide when they should be scheduled next.
