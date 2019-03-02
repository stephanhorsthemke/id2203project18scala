package se.kth.id2203.Paxos

import se.kth.id2203.networking.NetAddress
import se.sics.kompics.KompicsEvent

import scala.collection.mutable


case class C_Decide(value: Any) extends KompicsEvent;
case class C_Propose(value: Any) extends KompicsEvent;


case class Prepare(proposalBallot: (Int, Int), cycle:Int) extends KompicsEvent;
case class Promise(promiseBallot: (Int, Int), acceptedBallot: (Int, Int), acceptedValue: Option[Any], cycle:Int) extends KompicsEvent;
case class Accept(acceptBallot: (Int, Int), proposedValue: Any, cycle:Int) extends KompicsEvent;
case class Accepted(acceptedBallot: (Int, Int), cycle:Int) extends KompicsEvent;
case class Nack(ballot: (Int, Int), cycle:Int ) extends KompicsEvent;
case class Decided(decidedValue: Option[Any], cycle:Int) extends KompicsEvent;