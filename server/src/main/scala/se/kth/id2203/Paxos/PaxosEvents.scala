package se.kth.id2203.Paxos

import se.sics.kompics.KompicsEvent


case class C_Decide(value: Any) extends KompicsEvent;
case class C_Propose(value: Any) extends KompicsEvent;


case class Prepare(proposalBallot: (Int, Int)) extends KompicsEvent;
case class Promise(promiseBallot: (Int, Int), acceptedBallot: (Int, Int), acceptedValue: Option[Any]) extends KompicsEvent;
case class Accept(acceptBallot: (Int, Int), proposedValue: Any) extends KompicsEvent;
case class Accepted(acceptedBallot: (Int, Int)) extends KompicsEvent;
case class Nack(ballot: (Int, Int)) extends KompicsEvent;
case class Decided(decidedValue: Any) extends KompicsEvent;