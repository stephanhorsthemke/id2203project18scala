package se.kth.id2203.BEB;

import se.kth.id2203.BEB.Beb.{BebType, Replication}
import se.kth.id2203.PerfectLink.{PL_Deliver, PL_Send, PerfectLinkPort}
import se.kth.id2203.networking.{NetAddress, NetMessage}
import se.sics.kompics.sl._
import se.sics.kompics.{KompicsEvent, ComponentDefinition => _, Port => _}

import scala.collection.immutable.Set;

object Beb {
  sealed trait BebType;
  case object Replication extends BebType;
  case object Global extends BebType;
}


class Beb() extends ComponentDefinition {


  val self:NetAddress = cfg.getValue[NetAddress]("id2203.project.address");

  //subscriptions
  val pLink = requires[PerfectLinkPort];
  val beb = provides[BebPort];
  var topologyRepl: Set[NetAddress] = Set.empty;
  var topologyGlobal: Set[NetAddress] = Set.empty;

  //handlers
  beb uponEvent {
    case x: BEB_Broadcast => handle {
      val topology = if (x.typ == Replication) topologyRepl else topologyGlobal;

      if (topology.isEmpty) {
        log.debug("Topology is empty and first has to be set!")
      }
      else {
        for (p <- topology) {
          trigger(PL_Send(p, x), pLink);
        }
      }
    }
  }

  pLink uponEvent {
    case PL_Deliver(src, BEB_Broadcast(payload, typ)) => handle {
      trigger(BEB_Deliver(src, payload, typ) -> beb)

    }

    case PL_Deliver(this.self, BEB_Topology(addr: Set[NetAddress], sc)) => handle {
      if (sc == Replication) {
        topologyRepl = addr;
      } else {
        topologyGlobal = addr;
      }
    }
  }
}