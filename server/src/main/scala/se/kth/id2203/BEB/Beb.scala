package se.kth.id2203.BEB;

import se.kth.id2203.BEB.Beb.{BebType, Replication}
import se.kth.id2203.PerfectLink.{PL_Deliver, PL_Send, PerfectLinkPort}
import se.kth.id2203.networking.{NetAddress, NetMessage}
import se.sics.kompics.sl.{Init, _}
import se.sics.kompics.{KompicsEvent, ComponentDefinition => _, Port => _}

import scala.collection.immutable.Set;

object Beb {
  sealed trait BebType;
  case object Replication extends BebType;
  case object Global extends BebType;
}


class Beb() extends ComponentDefinition {

  //subscriptions
  val pLink = requires[PerfectLinkPort];
  val beb = provides[BebPort];
  var topologyRepl: Set[NetAddress] = Set.empty;
  var topologyGlobal: Set[NetAddress] = Set.empty;

  //handlers
  beb uponEvent {
    case x: BEB_Broadcast => handle {
      val topology = if (x.typ == Replication) topologyRepl else topologyGlobal;

      for (p <- topology) {
        trigger(PL_Send(p, x), pLink);
      }
    }
  }

  pLink uponEvent {
    case PL_Deliver(src, BEB_Broadcast(payload, typ)) => handle {

      trigger(BEB_Deliver(src, payload, typ) -> beb);

    }
    case PL_Deliver(_, BEB_Topology(addr: Set[NetAddress], sc)) => handle {

      if (sc == Replication) {
        topologyRepl = addr;
      } else {
        topologyGlobal = addr;
      }
    }
  }
}