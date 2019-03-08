package se.kth.id2203.overlay

import se.sics.kompics.KompicsEvent


// The following events are to be used internally by the Replication Controller
case class ReplicationWriteComplete(partitionCount: Int) extends KompicsEvent;