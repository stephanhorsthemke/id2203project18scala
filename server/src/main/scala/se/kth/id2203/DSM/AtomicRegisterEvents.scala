package se.kth.id2203.DSM

import java.util.UUID

import se.kth.id2203.BEB.Beb.{BebType, Replication}
import se.sics.kompics.KompicsEvent;

case class AR_Read_Request(id: UUID, key: String, group: BebType = Replication) extends KompicsEvent
case class AR_Read_Response(value: Option[Any], id: UUID) extends KompicsEvent
case class AR_Write_Request(value: Any, key: String, id: UUID, group: BebType = Replication) extends KompicsEvent
case class AR_Write_Response(id: UUID) extends KompicsEvent
case class AR_CAS_Request(refValue: Any, value: Any, key: String, id: UUID, group: BebType = Replication) extends KompicsEvent
case class AR_CAS_Response(value: Option[Any], id: UUID) extends KompicsEvent
case class AR_Range_Request(lowerBorder: String, upperBorder: String) extends KompicsEvent
case class AR_Range_Response(values: collection.Map[String, Any]) extends KompicsEvent

//The following events are to be used internally by the Atomic Register
case class ACK(rid: Int, key: String, group : BebType = Replication) extends KompicsEvent;
case class READ(rid: Int, key: String) extends KompicsEvent;
case class VALUE(rid: Int, key: String, ts: Int, wr: Int, value: Option[Any], group : BebType = Replication) extends KompicsEvent;
case class WRITE(rid: Int, key: String, ts: Int, wr: Int, writeVal: Option[Any]) extends KompicsEvent;