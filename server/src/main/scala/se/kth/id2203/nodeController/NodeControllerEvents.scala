package se.kth.id2203.nodeController

import se.kth.id2203.networking.NetAddress
import se.sics.kompics.KompicsEvent

import scala.collection.mutable

case class UpdateNodes(nodes: Set[NetAddress]) extends KompicsEvent;
