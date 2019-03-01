package se.kth.id2203.BEB

import se.kth.id2203.networking.NetAddress
import se.sics.kompics.KompicsEvent;
import scala.collection.Set

// TODO NetAddress correct?
case class BEB_Deliver(source: NetAddress, payload: KompicsEvent, typ: Beb.BebType) extends KompicsEvent;
case class BEB_Broadcast(payload: KompicsEvent, typ: Beb.BebType) extends KompicsEvent;
case class BEB_Topology(addr: Set[NetAddress], typ: Beb.BebType) extends KompicsEvent;
