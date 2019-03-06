/*
 * The MIT License
 *
 * Copyright 2017 Lars Kroll <lkroll@kth.se>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package se.kth.id2203.simulation;

import java.util.UUID

import se.kth.id2203.PerfectLink._
import se.kth.id2203.bootstrapping.{Booted, CheckIn}
import se.kth.id2203.kvstore._
import se.kth.id2203.networking._
import se.kth.id2203.overlay.RouteMsg
import se.sics.kompics.sl._
import se.sics.kompics.{Init, Start}
import se.sics.kompics.network.Network
import se.sics.kompics.timer.{ScheduleTimeout, Timeout, Timer}
import se.sics.kompics.sl.simulator.SimulationResult

import collection.mutable;


class ScenarioParent extends ComponentDefinition {


  //******* Ports ******
  val net = requires[Network];
  val timer = requires[Timer];
  //******* Children ******
  val self:NetAddress = cfg.getValue[NetAddress]("id2203.project.address");

  val pLink = create(classOf[PerfectLink], Init.NONE);
  val sce = create(classOf[ScenarioClient], Init.NONE)




  {
    //Perfect Link
    connect[Timer](timer -> pLink);
    connect[Network](net -> pLink);
    //ScenarioClient
    connect[PerfectLinkPort](pLink -> sce);
    connect[Timer](timer -> sce)
  }
}




class ScenarioClient extends ComponentDefinition {

  case class GET(timeout: ScheduleTimeout) extends Timeout(timeout);
  case class CAS(timeout: ScheduleTimeout) extends Timeout(timeout);

  //******* Ports ******
  val timer = requires[Timer];
  val pLink = requires[PerfectLinkPort]
  //******* Fields ******
  val self = cfg.getValue[NetAddress]("id2203.project.address");
  val server = cfg.getValue[NetAddress]("id2203.project.bootstrap-address");
  private val pending = mutable.Map.empty[UUID, String];




  //******* Handlers ******
  ctrl uponEvent {
    case _: Start => handle {
      val messages = SimulationResult[Int]("messages");
      for (i <- 0 to messages) {
        val op = new Op(s"PUT", s"test$i", i);
        val routeMsg = RouteMsg(op.key, op); // don't know which partition is responsible, so ask the bootstrap server to forward it
        trigger(PL_Send(server, routeMsg) -> pLink);
        pending += (op.id -> s"PUT$i");
        logger.info("Sending {}", op);
        SimulationResult += (s"PUT$i" -> "Sent");
      }

      val CASTimeout = new ScheduleTimeout(10)
      CASTimeout.setTimeoutEvent(CAS(CASTimeout))
      trigger(CASTimeout -> timer)

      val GETTimeout = new ScheduleTimeout(20)
      GETTimeout.setTimeoutEvent(GET(GETTimeout))
      trigger(GETTimeout -> timer)
    }
  }


  timer uponEvent {

    case CAS(_) => handle {
      val messages = SimulationResult[Int]("messages");
      for (i <- 0 to messages) {
        val op = new Op("CAS", s"test$i", i+4, i);
        val routeMsg = RouteMsg(op.key, op); // don't know which partition is responsible, so ask the bootstrap server to forward it
        trigger(PL_Send(server, routeMsg) -> pLink);
        pending += (op.id -> s"CAS$i");
        logger.debug("Server: " + server);
        logger.info("Sending {}", op);
        SimulationResult += (s"CAS$i" -> "Sent");
      }
    }

    case GET(_) => handle {
      val messages = SimulationResult[Int]("messages");
      for (i <- 0 to messages) {
        val op = new Op("GET", s"test$i");
        val routeMsg = RouteMsg(op.key, op); // don't know which partition is responsible, so ask the bootstrap server to forward it
        trigger(PL_Send(server, routeMsg) -> pLink);
        pending += (op.id -> s"GET$i");
        logger.info("Sending {}", op);
        SimulationResult += (s"GET$i" -> "Sent");

      }
      trigger(PL_Send(server, CheckIn) -> pLink);
    }
  }

  pLink uponEvent {
    case PL_Deliver(src, or @ OpResponse(id, status, value)) => handle {
      logger.debug(s"Got OpResponse: $or");
      pending.remove(id) match {
        case Some(x) => SimulationResult += (x -> status.toString());
        case None      => logger.warn("ID $id was not pending! Ignoring response.");
      }
    }

    case PL_Deliver(_, b @ Booted(_)) => handle {
      val numberOfNodes = b.nodes.size - 1
      log.debug("Got Node number: " + numberOfNodes)
      SimulationResult += (s"NN" -> numberOfNodes.toString);
      log.debug("result: " + SimulationResult.get[String](s"NN"))
    }
  }
}
