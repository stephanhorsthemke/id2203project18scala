package se.kth.id2203.BEB

import se.kth.id2203.networking.NetAddress
import se.sics.kompics.KompicsEvent;

// TODO NetAddress correct?
case class BEB_Deliver(source: NetAddress, payload: KompicsEvent) extends KompicsEvent;
case class BEB_Broadcast(payload: KompicsEvent) extends KompicsEvent;
