package se.kth.id2203.EPFD

//import se.kth.id2203.validation._
import se.kth.id2203.PerfectLink
import se.kth.id2203.PerfectLink.{PL_Deliver, PL_Send, PerfectLinkPort}
import se.kth.id2203.networking._
import se.kth.id2203.nodeController.UpdateNodes
import se.sics.kompics.network._
import se.sics.kompics.sl.{Init, _}
import se.sics.kompics.timer.{ScheduleTimeout, Timeout, Timer}
import se.sics.kompics.{KompicsEvent, Start, ComponentDefinition => _, Port => _}


case class CheckTimeout(timeout: ScheduleTimeout) extends Timeout(timeout);

case class HeartbeatReply(seq: Int) extends KompicsEvent;
case class HeartbeatRequest(seq: Int) extends KompicsEvent;

//Define EPFD Implementation
class EPFD() extends ComponentDefinition {

  //EPFD subscriptions
  val timer = requires[Timer];
  val pLink = requires[PerfectLinkPort];
  val fd = provides[EPFDPort];

  // EPDF component state and initialization

  //configuration parameters
  val self = cfg.getValue[NetAddress]("id2203.project.address");

  // all other nodes?
  var nodes = Set.empty[NetAddress]

  val delta = 1000;

  //mutable state
  var period = delta;
  var alive = nodes;
  var suspected = Set.empty[NetAddress];
  var seqnum = 0;

  def startTimer(delay: Long): Unit = {
    val scheduledTimeout = new ScheduleTimeout(period);
    scheduledTimeout.setTimeoutEvent(CheckTimeout(scheduledTimeout));
    trigger(scheduledTimeout -> timer);
  }

  //EPFD event handlers
  ctrl uponEvent {
    case _: Start => handle {
      startTimer(0)
    }
  }

  timer uponEvent {
    case CheckTimeout(delay) => handle {
      // If a suspected process is alive
      if (!alive.intersect(suspected).isEmpty) {
        /* Increase the delay, suspected was alive */
        period = period + delta;
      }

      seqnum = seqnum + 1;

      for (p <- nodes) {

        // if p is neither alive nor suspected
        if (!alive.contains(p) && !suspected.contains(p)) {
          /*SUSPECT P  */
          suspected = suspected + p;
          log.debug("Suspecting " + p)
          trigger(Suspect(p) -> fd);

        } else if (alive.contains(p) && suspected.contains(p)) {
          suspected = suspected - p;
          log.debug("Restored: " + p)
          trigger(Restore(p) -> fd);
        }
        trigger(PerfectLink.PL_Send(p, HeartbeatRequest(seqnum)) -> pLink);
      }
      alive = Set.empty[NetAddress];
      startTimer(period);
    }
  }

  pLink uponEvent {
    case PL_Deliver(_, UpdateNodes(n, _)) => handle{
      if(n != nodes){
        nodes = n
        alive = nodes
        suspected = Set.empty[NetAddress]
      }
    }
    case PL_Deliver(src, HeartbeatRequest(seq)) => handle {

      trigger(PL_Send(src, HeartbeatReply(seq)) -> pLink);

    }
    case PL_Deliver(src, HeartbeatReply(seq)) => handle {
      if (seq == seqnum || suspected.contains(src)){
        alive = alive + src;
        //println("Process " + src + " is alive");
      }
    }
  }
};