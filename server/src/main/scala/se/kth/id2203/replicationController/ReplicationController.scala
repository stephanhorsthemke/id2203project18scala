package se.kth.id2203.replicationController

import se.kth.id2203.PerfectLink.{PL_Deliver, PerfectLinkPort}
import se.kth.id2203.bootstrapping._
import se.kth.id2203.networking.NetAddress
import se.kth.id2203.overlay.LookupTable
import se.sics.kompics.sl.{ComponentDefinition, handle}
import scala.collection.mutable.HashMap


/**
  * The Replication Controller is mainly responsible for the list of Nodes.
  * It adds new nodes, deletes failed nodes and enforces consensus with all the other nodes globally
  * to have one agreed list of nodes and therefore lookup table.
  *
  * INIT
  *   - set the initial list of nodes
  *   - send nodes to Overlay Manager
  *
  * FD
  *   - inform others --> Consensus
  *   - adjust replicas
  *
  * ADD
  *   - inform others --> Consensus
  *   - adjust replicas
  */
class ReplicationController extends ComponentDefinition{

  val pLink = requires[PerfectLinkPort]
  val boot = requires(Bootstrapping)

  // The current agreed set of nodes
  val nodes = HashMap.empty[String, NetAddress]

  // The proposed change of nodes
  val newNodes = HashMap.empty[String, NetAddress]

  //******* Handlers ******
  pLink uponEvent {
    case PL_Deliver(src, CheckIn) => handle {
      //TODO
      //update list of nodes
      //send to new node, to get started
      //consensus to add the node

    }
    case PL_Deliver(src, Ready) => handle {
      //TODO
      //If ready and consensus --> Add node
    }
  }
 /* boot uponEvent {
    case GetInitialAssignments(n) => handle {
      log.info("Setting the initial nodes");
      this.nodes = n
      logger.debug("Generated assignments:\n" + lut);
      trigger(new InitialAssignments(lut) -> boot);
    }
  }


*/

}
