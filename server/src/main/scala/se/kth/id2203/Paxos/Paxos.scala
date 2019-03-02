package se.kth.id2203.Paxos

import se.kth.id2203.BEB.Beb.Global
import se.kth.id2203.BEB.{BEB_Broadcast, BEB_Deliver, BebPort}
import se.kth.id2203.PerfectLink.{PL_Deliver, PL_Send, PerfectLinkPort}
import se.kth.id2203.networking.NetAddress
import se.sics.kompics.sl.ComponentDefinition
import se.sics.kompics.sl._

import scala.collection.mutable
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

  // incremented after decision for new cycle of consensus
  var cycle = 1;


  //Proposer State
  var round = 0;
  var proposedValue: Option[Any] = None;
  var promises: ListBuffer[((Int, Int), Option[Any])] = ListBuffer.empty;
  var numOfAccepts = 0;

  //Acceptor State
  var promisedBallot = (0, 0);
  var acceptedBallot = (0, 0);
  var acceptedValue: Option[Any] = None;

  def propose() = {
    round = round + 1;
    numOfAccepts = 0;
    promises = ListBuffer.empty;
    log.debug("Sending Prepare Broadcast")
    trigger(BEB_Broadcast(Prepare((round, rank), cycle), Global) -> beb)
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

      log.debug("Cycle:" + cycle + " :: " + System.currentTimeMillis() + ":: new Propose:  " + value );
      proposedValue = Some(value);
      propose();
    }
  }


  beb uponEvent {

    case BEB_Deliver(src, prep: Prepare, Global) if prep.cycle == cycle || cycle == 1=> handle {
      if (cycle == 1){
        cycle = prep.cycle
      }
      var ballot = prep.proposalBallot
      log.debug("Cycle:" + cycle + " :: " + System.currentTimeMillis() + ":: Prepare; Ballot:  " + ballot);
      if(promisedBallot < ballot){
        promisedBallot = ballot;
        trigger(PL_Send(src, Promise(promisedBallot, acceptedBallot, acceptedValue, cycle)) -> plink);
      }else{
        trigger(PL_Send(src, Nack(ballot, cycle)) -> plink);
      }
    };

    case BEB_Deliver(src, acc: Accept, Global) if acc.cycle == cycle => handle {
      var ballot = acc.acceptBallot;
      if(promisedBallot <= ballot){
        promisedBallot = ballot;
        acceptedBallot = ballot;
        acceptedValue = Some(acc.proposedValue);
        log.debug("Cycle:" + cycle + " :: " + System.currentTimeMillis() + ":: Accepting BAllot:  " + ballot);
        trigger(PL_Send(src, Accepted(ballot, cycle)) -> plink);
      }else{
        trigger(PL_Send(src, Nack(ballot, cycle)) -> plink);
      }
    };

    case BEB_Deliver(src, Decided(decValue, c) ,Global) if c==cycle => handle {

      trigger(C_Decide(decValue) -> paxos)
      cycle += 1


      round = 0
      proposedValue = None
      promises = ListBuffer.empty
      numOfAccepts = 0
      promisedBallot = (0, 0)
      acceptedBallot = (0, 0)
      acceptedValue = None
    }

  }

  plink uponEvent {

    case PL_Deliver(src, prepAck: Promise) if prepAck.cycle == cycle => handle {
      if ((round, rank) == prepAck.promiseBallot) {
        promises = promises ++ ListBuffer((prepAck.acceptedBallot, prepAck.acceptedValue));

        //println(rank + " :: " + System.currentTimeMillis() + ":: Promised answer:  " + prepAck);
        if(promises.size == math.ceil((numProcesses+1)/2.0).toInt){
          var value = highestByBallot(promises);
          if (value.isDefined){
            proposedValue = value;
          }
          log.debug("Cycle:" + cycle + " :: " + System.currentTimeMillis() + ":: Majority Promises:  " + proposedValue + "promises.size: " + promises.size);
          trigger(BEB_Broadcast(Accept((round, rank), proposedValue.get, cycle), Global) -> beb);
        }
      }
    };

    case PL_Deliver(src, accAck: Accepted) if accAck.cycle == cycle => handle {
      if ((round, rank) == accAck.acceptedBallot) {
        numOfAccepts = numOfAccepts + 1;
        if(numOfAccepts  ==  math.ceil((numProcesses+1)/2.0).toInt){
          log.debug("Cycle:" + cycle + " :: " + System.currentTimeMillis() + ":: Decided:  " + proposedValue);
          trigger(BEB_Broadcast(Decided(proposedValue, cycle), Global) -> beb);
        }

      }
    };

    case PL_Deliver(src, nack:Nack) if nack.cycle==cycle => handle {
      if ((round, rank) == nack.ballot) {
        propose();
      }
    }

    case PL_Deliver(self, ProcessNumber(n)) => handle{
      numProcesses = n;
    }
  }
};