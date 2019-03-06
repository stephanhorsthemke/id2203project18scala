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
import se.kth.id2203.nodeController.UpdateNodes
import se.kth.id2203.overlay.RouteMsg
import se.sics.kompics.sl._
import se.sics.kompics.{Init, Start}
import se.sics.kompics.network.Network
import se.sics.kompics.sl.simulator.SimulationResult
import se.sics.kompics.timer.Timer

import collection.mutable;


class NodesTestParent extends ComponentDefinition {


  //******* Ports ******
  val net = requires[Network];
  val timer = requires[Timer];

  //******* Children ******

  val pLink = create(classOf[PerfectLink], Init.NONE);
  val nt = create(classOf[NodesTest], Init.NONE)




  {
    //Perfect Link
    connect[Timer](timer -> pLink);
    connect[Network](net -> pLink);
    //ScenarioClient
    connect[PerfectLinkPort](pLink -> nt);
    connect[Timer](timer -> nt)

  }
}



// Just gets the list of Nodes From a server
class NodesTest extends ComponentDefinition {

  //******* Ports ******
  val pLink = requires[PerfectLinkPort]
  val timer = requires[Timer];

  //******* Fields ******
  val server = cfg.getValue[NetAddress]("id2203.project.bootstrap-address");




  //******* Handlers ******
  ctrl uponEvent {
    case _: Start => handle {
      log.debug("Lets send a CHECKIN to " + server)
      trigger(PL_Send(server, CheckIn) -> pLink);
    }
  }

  pLink uponEvent {
    case PL_Deliver(_, Booted(n)) => handle {
      val numberOfNodes = n.size -1
      log.debug("Got Node numner: " + numberOfNodes)
      SimulationResult += ("Node number" -> numberOfNodes);
    }

  }
}
