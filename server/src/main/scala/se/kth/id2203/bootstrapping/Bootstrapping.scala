package se.kth.id2203.bootstrapping

import se.sics.kompics.KompicsEvent
import se.sics.kompics.sl._;
import se.kth.id2203.networking.NetAddress;
import scala.collection.mutable

object Bootstrapping extends Port {
  indication[UpdateNodes];
  indication[Booted];
  request[InitialAssignments];
}

case class UpdateNodes(nodes: mutable.Map[NetAddress, String]) extends KompicsEvent;
case class Booted(assignment: NodeAssignment, node: mutable.Map[NetAddress, String]) extends KompicsEvent;
case class InitialAssignments(assignment: NodeAssignment) extends KompicsEvent;