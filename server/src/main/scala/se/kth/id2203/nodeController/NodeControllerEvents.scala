package se.kth.id2203.nodeController

import se.kth.id2203.networking.NetAddress
import se.kth.id2203.nodeController.NodeUpdate.NodeUpdate
import se.sics.kompics.KompicsEvent

case class UpdateNodes(nodes: Set[NetAddress], event: NodeUpdate) extends KompicsEvent;
