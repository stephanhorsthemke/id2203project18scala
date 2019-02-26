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
package se.kth.id2203.bootstrapping;

import java.util.UUID

import se.kth.id2203.PerfectLink.{PL_Deliver, PL_Send, PerfectLinkPort}
import se.kth.id2203.networking._
import se.sics.kompics.sl._
import se.sics.kompics.Start
import se.sics.kompics.network.Network
import se.sics.kompics.timer._

import collection.mutable;

object BootstrapServer {
  sealed trait State;
  case object Collecting extends State;
  case object Seeding extends State;
  case object Done extends State;
InitialAssignments
}

class BootstrapServer extends ComponentDefinition {
  import BootstrapServer._;

  //******* Ports ******
  val boot = provides(Bootstrapping);
  val pLink = requires[PerfectLinkPort];
  val timer = requires[Timer];
  //******* Fields ******
  val self = cfg.getValue[NetAddress]("id2203.project.address");
  val bootThreshold = cfg.getValue[Int]("id2203.project.bootThreshold");
  private var state: State = Collecting;
  private var timeoutId: Option[UUID] = None;
  private val active = mutable.HashSet.empty[NetAddress];
  private val ready = mutable.HashSet.empty[NetAddress];
  private var initialAssignment: Option[NodeAssignment] = None;
  //******* Handlers ******
  ctrl uponEvent {
    case _: Start => handle {
      log.info("Starting bootstrap server on {}, waiting for {} nodes...", self, bootThreshold);
      active += self;
    }
  }


  boot uponEvent {
    case InitialAssignments(assignment) => handle {
      initialAssignment = Some(assignment);
      log.info("Seeding assignments..." + initialAssignment);
      active foreach { node =>
        trigger(PL_Send(node, Boot(assignment)) -> pLink);
      }
      ready += self;
    }
  }

  pLink uponEvent {
    case PL_Deliver(src, CheckIn) => handle {
      active += src;
      log.info("{} hosts in active set.", active.size);
      if (active.size >= bootThreshold) {
        bootUp();
      }
    }
    case PL_Deliver(src, Ready) => handle {
      ready += src;
      log.info("{} hosts in ready set.", ready.size);
      if (ready.size >= bootThreshold) {
        log.info("Finished seeding. Bootstrapping complete.");
        initialAssignment match {
          case Some(assignment) => {
            trigger(Booted(assignment) -> boot);
            suicide()
          }
          case None => {
            logger.error(s"No initial assignment received at bootThreshold. Ready nodes: $ready");
            suicide();
          }
        }
      }
    }
  }

  private def bootUp(): Unit = {
    log.info("Threshold reached. Generating assignments...");
    state = Seeding;
    trigger(GetInitialAssignments(active.toSet) -> boot);
  }
}
