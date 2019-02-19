package se.kth.id2203.BEB;

import se.kth.id2203.PerfectLink.{PL_Deliver, PL_Send, PerfectLinkPort}
import se.kth.id2203.networking.{NetAddress, NetMessage}
import se.sics.kompics.sl.{Init, _}
import se.sics.kompics.{KompicsEvent, ComponentDefinition => _, Port => _}

import scala.collection.immutable.Set;




class Beb() extends ComponentDefinition {

  //subscriptions
  val pLink = requires[PerfectLinkPort];
  val beb = provides[BebPort];

/*  //configuration
  val (self, topology) = init match {
    case Init(s: NetAddress, t: Set[NetAddress]@unchecked) => (s, t)
  };*/

  var topology: Set[NetAddress] = Set.empty;

  def setTopology(newTopology: Set[NetAddress]){
    topology = newTopology
  }

  //handlers
  beb uponEvent {
    case x: BEB_Broadcast => handle {

      for (p <- topology) {
        trigger(PL_Send(p, x), pLink);
      }
    }
  }

  pLink uponEvent {
    case PL_Deliver(src, BEB_Broadcast(payload)) => handle {

      trigger(BEB_Deliver(src, payload) -> beb);

    }
  }
}