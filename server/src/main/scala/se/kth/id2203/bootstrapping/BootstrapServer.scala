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

import se.kth.id2203.PerfectLink
import se.kth.id2203.PerfectLink.{PL_Deliver, PL_Send, PerfectLinkPort}
import se.kth.id2203.networking._
import se.kth.id2203.replicationController.UpdateNodes
import se.sics.kompics.sl._
import se.sics.kompics.Start

import collection.mutable

class BootstrapServer extends ComponentDefinition {
  //******* Ports ******
  val pLink = requires[PerfectLinkPort];
  //******* Fields ******
  val self = cfg.getValue[NetAddress]("id2203.project.address");
  val bootThreshold = cfg.getValue[Int]("id2203.project.bootThreshold");
  private var nodes = mutable.HashSet.empty[NetAddress]
  //******* Handlers ******
  ctrl uponEvent {
    case _: Start => handle {
      log.info("Starting bootstrap server on {}, waiting for {} nodes...", self, bootThreshold);
      nodes += self
    }
  }


  pLink uponEvent {
    case PL_Deliver(src, CheckIn) => handle {
      nodes += src
      log.info("{} hosts in active set.", nodes.size);
      if (nodes.size >= bootThreshold) {
        log.info("Threshold reached.");
        nodes.foreach {
          n => trigger(PL_Send(n, Booted(nodes)) -> pLink)
        }
        suicide()
      }
    }
  }

}