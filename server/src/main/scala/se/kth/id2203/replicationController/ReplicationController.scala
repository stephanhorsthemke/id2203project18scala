package se.kth.id2203.replicationController

import javax.swing.plaf.BorderUIResource.BevelBorderUIResource
import se.kth.id2203.BEB.{BEB_Broadcast, BEB_Topology, BebPort}
import se.kth.id2203.BEB.Beb.{Global, Replication}
import se.kth.id2203.Paxos.{C_Decide, C_Propose, PaxosPort}
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

// TODO: generate the Lookuptable anew in the OverlayManager -> Is the generation deterministic with a specific set of nodes?
class ReplicationController extends ComponentDefinition{

  val pLink = requires[PerfectLinkPort]
  val beb = requires[BebPort]
  val boot = requires(Bootstrapping)
  val paxos = requires[PaxosPort]


  val self = cfg.getValue[NetAddress]("id2203.project.address");


  // The current agreed set of nodes
  var nodes = mutable.HashSet.empty[NetAddress]

  // The proposed change of nodes
  var newNodes = mutable.HashSet.empty[NetAddress]

  //******* Handlers ******
  pLink uponEvent {
    case PL_Deliver(src, CheckIn) => handle {
      if(newNodes == nodes){
        if(!nodes(src)){
          newNodes += src
          trigger(C_Propose(newNodes) -> paxos)
          trigger(PL_Send(src, UpdateNodes(nodes)) -> pLink)
          log.debug("Send updatesNodes topology to: " + src)
        }else{
          log.debug("Deleting duplicate checkin")
        }
      }else{
        log.debug("Ongoing Connection - checkIn not possible:" + src )
      }
    }

    case PL_Deliver(src, Ready) => handle {
      log.debug("new Node is ready")
    }
  }

  paxos uponEvent{
    case C_Decide(n: Option[mutable.HashSet[NetAddress]]) => handle{
      nodes = n.get
      newNodes = n.get
      log.info("Send decision to overlay manager: " + n)
      trigger(PL_Send(self, UpdateNodes(nodes)) -> pLink)
    }
  }


  boot uponEvent {
    case Booted(assignment: LookupTable, nodes) => handle {
      this.nodes = nodes
      newNodes = nodes
    }
  }

}
