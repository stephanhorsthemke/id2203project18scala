package se.kth.id2203.bootstrapping

import se.kth.id2203.networking.NetAddress
import se.sics.kompics.KompicsEvent
import scala.collection.mutable

case class Booted(nodes: mutable.HashSet[NetAddress]) extends KompicsEvent;
