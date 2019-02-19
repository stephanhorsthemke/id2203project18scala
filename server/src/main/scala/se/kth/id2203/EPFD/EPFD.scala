package se.kth.id2203.EPFD

//import se.kth.id2203.validation._
import se.kth.id2203.networking._
import se.sics.kompics.network._
import se.sics.kompics.sl.{Init, _}
import se.sics.kompics.timer.{ScheduleTimeout, Timeout, Timer}
import se.sics.kompics.{KompicsEvent, Start, ComponentDefinition => _, Port => _}


case class CheckTimeout(timeout: ScheduleTimeout) extends Timeout(timeout);

case class HeartbeatReply(seq: Int) extends KompicsEvent;
case class HeartbeatRequest(seq: Int) extends KompicsEvent;

//Define EPFD Implementation
class EPFD(epfdInit: Init[EPFD]) extends ComponentDefinition {

  //EPFD subscriptions
  val timer = requires[Timer];

  // TODO We need a perfect link, is it a perfect link?
  val net = requires[Network];
  val epfd = provides[EventuallyPerfectFailureDetector];

  // EPDF component state and initialization

  //configuration parameters
  val self = epfdInit match {case Init(s: NetAddress) => s};

  // all other nodes?
  val topology = cfg.getValue[List[NetAddress]]("epfd.simulation.topology");
  val delta = 20;

  //mutable state
  var period = delta;
  var alive = Set(topology: _*);
  var suspected = Set[NetAddress]();
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

      for (p <- topology) {

        // if p is neither alive nor suspected
        if (!alive.contains(p) && !suspected.contains(p)) {
          /*SUSPECT P  */
          suspected = suspected + p;
          trigger(Suspect(p) -> epfd);

        } else if (alive.contains(p) && suspected.contains(p)) {
          suspected = suspected - p;
          trigger(Restore(p) -> epfd);
        }
        trigger(NetMessage(self, p, HeartbeatRequest(seqnum)) -> net);
      }
      alive = Set[NetAddress]();
      startTimer(period);
    }
  }

  net uponEvent {
    case NetMessage(header, HeartbeatRequest(seq)) => handle {

      trigger(NetMessage(self, header.src, HeartbeatReply(seq)) -> net);

    }
    case NetMessage(header, HeartbeatReply(seq)) => handle {
      if (seq == seqnum || suspected.contains(header.src)){
        alive = alive + header.src;
        println("Process " + header.src + " is alive");
      }
    }
  }
};