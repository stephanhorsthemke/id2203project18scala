package se.kth.id2203.Paxos

import se.kth.id2203.BEB.Beb.Global
import se.kth.id2203.BEB.{BEB_Broadcast, BEB_Deliver, BebPort}
import se.kth.id2203.PerfectLink.{PL_Deliver, PL_Send, PerfectLinkPort}
import se.kth.id2203.networking.NetAddress
import se.sics.kompics.sl.ComponentDefinition
import se.sics.kompics.sl._

import scala.collection.mutable.ListBuffer


class Paxos() extends ComponentDefinition {


  implicit def addComparators[A](x: A)(implicit o: math.Ordering[A]): o.Ops = o.mkOrderingOps(x);

  //HINT: After you execute the latter implicit ordering you can compare tuples as such within your component implementation:
  (1,2) <= (1,4);
  //Port Subscriptions for Paxos

  val paxos = provides[PaxosPort];
  val beb = requires[BebPort];
  val plink = requires[PerfectLinkPort];


  val self:NetAddress = cfg.getValue[NetAddress]("id2203.project.address");

  // TODO Does this work here?
  var rank: Int = self.getPort()
  var numProcesses: Int = 3

  //Proposer State
  var round = 0;
  var proposedValue: Option[Any] = None;
  var promises: ListBuffer[((Int, Int), Option[Any])] = ListBuffer.empty;
  var numOfAccepts = 0;
  var decided = false;

  //Acceptor State
  var promisedBallot = (0, 0);
  var acceptedBallot = (0, 0);
  var acceptedValue: Option[Any] = None;

  def propose() = {
    if (!decided){
      round = round + 1;
      numOfAccepts = 0;
      promises = ListBuffer.empty;
      trigger(BEB_Broadcast(Prepare(round, rank), Global) -> beb);
    }
  }

  // return the highest tuple
  def highestByBallot(prs:ListBuffer[((Int, Int), Option[Any])]) : Option[Any] = {

    var dummyValue: Option[Any] = None;
    return prs.foldLeft(((0,0), dummyValue)) {
      (n, m) => if(n._1 > m._1) n else m;
    }._2
  }

  paxos uponEvent {
    case C_Propose(value) => handle {

      println(rank + " :: " + System.currentTimeMillis() + ":: new Propose:  " + value);
      proposedValue = Some(value);
      propose();
    }
  }


  beb uponEvent {

    case BEB_Deliver(src, prep: Prepare, _) => handle {
      var ballot = prep.proposalBallot
      if(promisedBallot < ballot){
        promisedBallot = ballot;
        println(rank + " :: " + System.currentTimeMillis() + ":: Promise Ballot:  " + ballot);
        trigger(PL_Send(src, Promise(promisedBallot, acceptedBallot, acceptedValue)) -> plink);
      }else{
        trigger(PL_Send(src, Nack(ballot)) -> plink);
      }
    };

    case BEB_Deliver(src, acc: Accept, _) => handle {
      var ballot = acc.acceptBallot;
      if(promisedBallot <= ballot){
        promisedBallot = ballot;
        acceptedBallot = ballot;
        acceptedValue = Some(acc.proposedValue);
        println(rank + " :: " + System.currentTimeMillis() + ":: Accepting BAllot:  " + ballot);
        trigger(PL_Send(src, Accepted(ballot)) -> plink);
      }else{
        trigger(PL_Send(src, Nack(ballot)) -> plink);
      }
    };

    case BEB_Deliver(src, dec : Decided , _) => handle {
      if(!decided){
        trigger(C_Decide(dec.decidedValue) -> paxos);
        decided = true;
      }
    }
  }

  plink uponEvent {

    case PL_Deliver(src, prepAck: Promise) => handle {
      if ((round, rank) == prepAck.promiseBallot) {
        promises = promises ++ ListBuffer((prepAck.acceptedBallot, prepAck.acceptedValue));

        //println(rank + " :: " + System.currentTimeMillis() + ":: Promised answer:  " + prepAck);
        if(promises.size == math.ceil((numProcesses+1)/2.0).toInt){
          var value = highestByBallot(promises);
          if (value.isDefined){
            proposedValue = value;
          }
          println(rank + " :: " + System.currentTimeMillis() + ":: Majority Promises:  " + proposedValue + "promises.size: " + promises.size);
          trigger(BEB_Broadcast(Accept((round, rank), proposedValue), Global) -> beb);
        }
      }
    };

    case PL_Deliver(src, accAck: Accepted) => handle {
      if ((round, rank) == accAck.acceptedBallot) {
        numOfAccepts = numOfAccepts + 1;
        if(numOfAccepts  ==  math.ceil((numProcesses+1)/2.0).toInt){
          println(rank + " :: " + System.currentTimeMillis() + ":: Decided:  " + proposedValue);
          trigger(BEB_Broadcast(Decided(proposedValue), Global) -> beb);
        }

      }
    };

    case PL_Deliver(src, nack: Nack) => handle {
      println(rank + " :: " + System.currentTimeMillis() + ":: NACK:  " + nack);
      if ((round, rank) == nack.ballot) {
        propose();
      }
    }
  }
};