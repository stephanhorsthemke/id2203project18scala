package se.kth.id2203.DSM

import se.sics.kompics.KompicsEvent;

case class AR_Read_Request() extends KompicsEvent
case class AR_Read_Response(value: Option[Any]) extends KompicsEvent
case class AR_Write_Request(value: Any) extends KompicsEvent
case class AR_Write_Response() extends KompicsEvent

