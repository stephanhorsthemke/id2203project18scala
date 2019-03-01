package se.kth.id2203.PerfectLink


import se.sics.kompics.KompicsEvent
import se.kth.id2203.networking.NetAddress
import se.sics.kompics.timer.{ScheduleTimeout, Timeout}


case class PL_Send(dest: NetAddress, payload: KompicsEvent) extends KompicsEvent

case class PL_Deliver(src: NetAddress, payload: KompicsEvent) extends KompicsEvent


case class Resend(timeout: ScheduleTimeout) extends Timeout(timeout);
case class UniqueMessage(msgCount: Int, payload: KompicsEvent) extends KompicsEvent;
case class ACK(msgCount: Int) extends KompicsEvent;