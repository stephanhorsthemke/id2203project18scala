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
package se.kth.id2203.overlay;

import java.util.UUID

import se.kth.id2203.BEB.Beb.{Build, Global, Replication}
import se.kth.id2203.BEB.{BEB_Topology, BebPort}
import se.kth.id2203.PerfectLink.{PL_Deliver, PL_Send, PerfectLinkPort}
import se.kth.id2203.kvstore.OpCode.OpCode
import se.kth.id2203.kvstore.{Op, OpResponse}
import se.kth.id2203.networking._
import se.kth.id2203.nodeController.NodeUpdate.{Boot, NodeUpdate, Update}
import se.kth.id2203.nodeController.UpdateNodes
import se.sics.kompics.sl._
import se.sics.kompics.timer.Timer

import scala.util.Random;

/**
 * The V(ery)A(dvanced)OverlayManager.
 * <p>
 * Keeps all nodes in a single partition in one replication group.
 * <p>
 * Note: This implementation does not fulfill the project task. You have to
 * support multiple partitions!
 * <p>
 * @author Lars Kroll <lkroll@kth.se>
 */
class ReplicationController extends ComponentDefinition {

  //******* Ports ******
  val route = provides(Routing);
  val timer = requires[Timer];
  val pLink = requires[PerfectLinkPort]
  val bebRepl = requires[BebPort]
  //******* Fields ******
  val self = cfg.getValue[NetAddress]("id2203.project.address");
  private var lut: Option[LookupTable] = None;
  private var buildLut: Option[LookupTable] = None;
  var srcMap = scala.collection.mutable.Map.empty[UUID, NetAddress]


  //******* Handlers ******
  pLink uponEvent {
    case PL_Deliver(_, UpdateNodes(n: Set[NetAddress], event: NodeUpdate)) => handle{
      log.debug("Updated Nodes: " + n)

      if (event == Boot) {
        lut = Some(LookupTable.generate(n));

        trigger(PL_Send(self, BEB_Topology(n, Global)) -> pLink);
        trigger(PL_Send(self, BEB_Topology(lut.get.getNodes(self), Replication)) -> pLink);

        log.info("Fresh LookupTable..." + lut.getOrElse(Set.empty).toString);

      } else if (event == Update) {
        // TODO add DSM reconfig here
        assert(lut.isDefined, "Update was triggered before Boot was completed");

        // create new lut
        buildLut = Some(LookupTable.generate(n));

        trigger(PL_Send(self, BEB_Topology(n, Global)) -> pLink);
        trigger(PL_Send(self, BEB_Topology(buildLut.get.getNodes(self), Build)) -> pLink);

        if (lut.get.isFirst(self)) {
          // send all values from oldRepl to newRepl

          // send globalBcast that all values were sent

        }
      }
    }


    // forwards a message to responsible node for the key
    case PL_Deliver(src, RouteMsg(key,op:Op)) => handle {
      log.debug("Received RouteMessage")
      srcMap += (op.id -> src);
      // gets responsible node
      val nodes = lut.get.lookup(key);
      log.info(s"Choose from $nodes");
      // throws assertionException when nodes is empty
      assert(nodes.nonEmpty);
      val i = Random.nextInt(nodes.size);
      val target = nodes.drop(i).head;
      log.info(s"Forwarding message for key $key to $target with $op");
      trigger(PL_Send(target, op) -> pLink);
    }

    // sending the handles OP back to where it came from
    case PL_Deliver(_ , opResp @ OpResponse(uuid,opCode: OpCode,_)) => handle{
      var src: Option[NetAddress] = None
      if(srcMap.contains(uuid)){
        src = srcMap.remove(uuid)
        trigger(PL_Send(src.get, opResp) -> pLink)
        log.info("Sent OpResponse")
      }else{
        log.debug("No src for uuid known: either duplicate message or lost: " + opResp)
      }


    }
    // Connectionrequest: send ACK when the system is ready to requester
    case PL_Deliver(src, msg: Connect) => handle {
      lut match {
        // do we already have a lookuptable?
        case Some(l) => {
          log.debug("Accepting connection request from " + src);
          val size = l.getNodes.size;
          trigger (PL_Send(src, msg.ack(size)) -> pLink);
        }
        case None => log.info(s"Rejecting connection request from $src, as system is not ready, yet.");
      }
    }
  }

  route uponEvent {
    // sends message to responsible node for the key
    case RouteMsg(key, msg) => handle {
      val nodes = lut.get.lookup(key);
      assert(nodes.nonEmpty);
      val i = Random.nextInt(nodes.size);
      val target = nodes.drop(i).head;
      log.info(s"ROUTING MESSAGE for key $key to $target");
      trigger(PL_Send(target, msg) -> pLink);

    }
  }
}
