package se.kth.id2203.overlay

import se.kth.id2203.kvstore.Op
import se.sics.kompics.KompicsEvent

case class ReplicationWrite(op: Op) extends KompicsEvent;

// The following events are to be used internally by the Replication Controller
case class ReplicationWriteComplete(partitionCount: Int) extends KompicsEvent;
