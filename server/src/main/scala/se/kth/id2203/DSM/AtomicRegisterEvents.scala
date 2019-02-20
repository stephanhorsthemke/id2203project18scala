package se.kth.id2203.DSM

import se.sics.kompics.KompicsEvent;

case class AR_Read_Request() extends KompicsEvent
case class AR_Read_Response(value: Option[Any]) extends KompicsEvent
case class AR_Write_Request(value: Any) extends KompicsEvent
case class AR_Write_Response() extends KompicsEvent

//The following events are to be used internally by the Atomic Register implementation below
case class ACK(rid: Int) extends KompicsEvent;
case class READ(rid: Int) extends KompicsEvent;
case class VALUE(rid: Int, ts: Int, wr: Int, value: Option[Any]) extends KompicsEvent;
case class WRITE(rid: Int, ts: Int, wr: Int, writeVal: Option[Any]) extends KompicsEvent;