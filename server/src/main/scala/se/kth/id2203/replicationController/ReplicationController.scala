package se.kth.id2203.replicationController

import javax.swing.plaf.BorderUIResource.BevelBorderUIResource
import se.kth.id2203.BEB.{BEB_Broadcast, BEB_Topology, BebPort}
import se.kth.id2203.BEB.Beb.{Global, Replication}
import se.kth.id2203.Paxos.{C_Decide, C_Propose, PaxosPort}
import se.kth.id2203.PerfectLink
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
  var nodes = mutable.Map.empty[NetAddress, String]

  // The proposed change of nodes
  var newNodes = mutable.Map.empty[NetAddress, String]

  //******* Handlers ******
  pLink uponEvent {
    case PL_Deliver(src, CheckIn(h)) => handle {
      if(newNodes == nodes){
        if(!nodes.contains(src)){
          trigger(C_Propose(newNodes) -> paxos)
          //trigger(PL_Send(src, Boot()))
        }
      }else{
        log.debug("Ongoing Connection - checkIn not possible:" + src )
      }
      //TODO
      //send to new node, to get started
      //consensus to add the node

    }
    case PL_Deliver(src, Ready) => handle {
      //TODO
      //If ready and consensus --> Add node
    }
  }


  paxos uponEvent{
    // TODO should we broadcast this globally?
    case C_Decide(n: mutable.Map[NetAddress, String]) => handle{
      nodes = n
      newNodes = n
      trigger(PL_Send(self, UpdateNodes(nodes)) -> pLink)
    }
  }


  boot uponEvent {
    case Booted(assignment: LookupTable, nodes) => handle {
      log.info("Got NodeAssignment, overlay ready.");
      // todo: filter node list for replication group
      this.nodes = nodes
      newNodes = nodes
      trigger(PL_Send(self, BEB_Topology(assignment.getNodes(), Replication)) -> pLink)
      trigger(PL_Send(self, BEB_Topology(assignment.getNodes(), Global)) -> pLink)
    }
  }

}
