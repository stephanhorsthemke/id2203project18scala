package se.kth.id2203.nodeController

import javax.swing.plaf.BorderUIResource.BevelBorderUIResource
import se.kth.id2203.BEB.{BEB_Broadcast, BEB_Topology, BebPort}
import se.kth.id2203.BEB.Beb.{Global, Replication}
import se.kth.id2203.EPFD.{EPFDPort, Restore, Suspect}
import se.kth.id2203.Paxos.{C_Decide, C_Propose, PaxosPort, ProcessNumber}
import se.kth.id2203.{PerfectLink, bootstrapping}
import se.kth.id2203.PerfectLink.{PL_Deliver, PL_Send, PerfectLinkPort}
import se.kth.id2203.bootstrapping._
import se.kth.id2203.networking.NetAddress
import se.kth.id2203.overlay.LookupTable
import se.sics.kompics.sl.{ComponentDefinition, handle}

import scala.collection.mutable


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

object NodeUpdate {
  sealed trait NodeUpdate;
  case object Boot extends NodeUpdate;
  case object Update extends NodeUpdate;
}
class NodeController extends ComponentDefinition{

  val pLink = requires[PerfectLinkPort]
  val beb = requires[BebPort]
  val paxos = requires[PaxosPort]
  val fd = requires[EPFDPort]


  val self = cfg.getValue[NetAddress]("id2203.project.address");

  var booted = false

  // The current agreed set of nodes
  var nodes = mutable.HashSet.empty[NetAddress]

  // The proposed change of nodes
  var newNodes = mutable.HashSet.empty[NetAddress]


  def newNode(src: NetAddress)={
    if(newNodes == nodes){
      if(!nodes(src)){
        newNodes += src
        trigger(C_Propose(newNodes) -> paxos)
        trigger(PL_Send(src, Booted(newNodes)) -> pLink)
        log.debug("Send updateNodes(" + newNodes.toSet + " to: " + src)
      }else{
        log.debug("Deleting duplicate checkin")
      }
    }else{
      log.debug("Ongoing Connection - checkIn not possible:" + src )
    }
  }


  //******* Handlers ******
  pLink uponEvent {

    case PL_Deliver(_, Booted(n)) => handle {
      booted = true;
      nodes = n;
      newNodes = n;
      trigger(PL_Send(self, UpdateNodes(nodes.toSet, NodeUpdate.Boot)) -> pLink)
    }

    case PL_Deliver(src, CheckIn) if booted =>  handle {
      log.debug("CHECKIN processing..")
      newNode(src)
    }
  }

  paxos uponEvent{
    case C_Decide(n: Option[mutable.HashSet[NetAddress]]) => handle{
      trigger(PL_Send(self, ProcessNumber(nodes.size)) -> pLink)
      nodes = n.get
      newNodes = n.get
      log.info("Send decision to overlay manager: " + n)
      trigger(PL_Send(self, UpdateNodes(nodes.toSet, NodeUpdate.Update)) -> pLink)
    }
  }

  fd uponEvent{
    case Suspect(p:NetAddress) => handle{
      if(nodes.contains(p) && newNodes == nodes){
        log.debug("Suspecting " + p + " triggering deletion proposal")
        trigger(C_Propose(nodes - p) -> paxos)
        newNodes -= p
      }
    }

    case Restore(p:NetAddress) => handle{
      if(nodes.contains(p) && newNodes == nodes){
        log.debug("Restoring " + p + " triggering addition proposal")
        trigger(C_Propose(nodes + p) -> paxos)
        newNode(p)
      }
    }
  }

}
