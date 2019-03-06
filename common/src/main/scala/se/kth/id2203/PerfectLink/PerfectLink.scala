package se.kth.id2203.PerfectLink


import se.sics.kompics.KompicsEvent
import se.sics.kompics.network.{Network, Transport}
import se.sics.kompics.sl._
import se.kth.id2203.networking.{NetAddress, NetHeader, NetMessage}
import se.sics.kompics.timer.{ScheduleTimeout, Timeout, Timer}


class PerfectLink() extends ComponentDefinition {


  val pLink = provides[PerfectLinkPort]
  val net = requires[Network]
  val timer = requires[Timer]


  val self = cfg.getValue[NetAddress]("id2203.project.address");

  // sender
  var timeDelay = 300
  var sent: Map[Int, NetMessage[UniqueMessage]] = Map.empty
  var messageCount = 0

  //  receiving part
  var delivered: Map[NetAddress, List[Int]] = Map.empty


  def startTimer(delay: Long): Unit = {
    val scheduledTimeout = new ScheduleTimeout(delay)
    scheduledTimeout.setTimeoutEvent(Resend(scheduledTimeout))
    trigger(scheduledTimeout -> timer)
  }

  // whenever the timeout is triggered, resend all non acked messages
  timer uponEvent {
    case Resend(delay) => handle {
      if(!sent.isEmpty)
      log.debug("timeout, resending: " + sent)
      sent.keys.foreach {
        i =>
          var netmsg = sent(i)
          trigger(netmsg -> net)
      }
    }
  }


  pLink uponEvent {

    // Send message with added unique counter value
    case PL_Send(dest: NetAddress, payload: KompicsEvent) => handle {

      messageCount += 1
      val message = NetMessage(self, dest, UniqueMessage(messageCount, payload))
      sent = sent + (messageCount -> message)
      //log.info("send message number: " + messageCount + " to: " + dest + " with message: " + payload)
      trigger(message -> net)
      startTimer(timeDelay)
    }
  }

  net uponEvent {

    // Deliver arrived message when not already delivered and send ACk
    case NetMessage(header: NetHeader, msg: UniqueMessage) => handle {

      val deliveredIDs:List[Int] = delivered.getOrElse(header.src, List.empty)
      if(!deliveredIDs.contains(msg.msgCount)){
        delivered = delivered.updated(header.src, msg.msgCount :: deliveredIDs)
        //log.debug("deliver message: " + msg.payload)

        trigger(PL_Deliver(header.src, msg.payload) -> pLink)
      }
      trigger(NetMessage(self, header.src, ACK(msg.msgCount)) -> net)

    }

    // message was delivered, so delete it out of the sent messages
    case NetMessage(_, ACK(msgCount)) => handle{
      sent -= msgCount
    }
  }
}