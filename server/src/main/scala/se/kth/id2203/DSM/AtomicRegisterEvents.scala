package se.kth.id2203.DSM

import java.util.UUID

import se.sics.kompics.KompicsEvent;

case class AR_Read_Request(id: UUID) extends KompicsEvent
case class AR_Read_Response(value: Option[Any], id: UUID) extends KompicsEvent
case class AR_Write_Request(value: Any, id: UUID) extends KompicsEvent
case class AR_Write_Response(id: UUID) extends KompicsEvent

//The following events are to be used internally by the Atomic Register implementation below
case class ACK(rid: Int) extends KompicsEvent;
case class READ(rid: Int) extends KompicsEvent;
case class VALUE(rid: Int, ts: Int, wr: Int, value: Option[Any]) extends KompicsEvent;
case class WRITE(rid: Int, ts: Int, wr: Int, writeVal: Option[Any]) extends KompicsEvent;