package se.kth.id2203.EPFD

import se.kth.id2203.networking.NetAddress
import se.sics.kompics.KompicsEvent

case class Suspect(process: NetAddress) extends KompicsEvent;
case class Restore(process: NetAddress) extends KompicsEvent;
