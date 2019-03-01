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

import se.kth.id2203.BEB.Beb.{Global, Replication}
import se.kth.id2203.BEB.{BEB_Broadcast, BEB_Topology, BebPort}
import se.kth.id2203.PerfectLink.{PL_Deliver, PL_Send, PerfectLinkPort}
import se.kth.id2203.bootstrapping._
import se.kth.id2203.kvstore.OpCode.OpCode
import se.kth.id2203.networking._
import se.sics.kompics.sl._
import se.sics.kompics.network.Network
import se.sics.kompics.timer.Timer
import se.kth.id2203.kvstore.{Op, OpCode, OpResponse}

import util.Random;

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
class VAOverlayManager extends ComponentDefinition {

  //******* Ports ******
  val route = provides(Routing);
  val boot = requires(Bootstrapping);
  val timer = requires[Timer];
  val pLink = requires[PerfectLinkPort]
  val bebRepl = requires[BebPort]
  //******* Fields ******
  val self = cfg.getValue[NetAddress]("id2203.project.address");
  private var lut: Option[LookupTable] = None;
  var srcMap = scala.collection.mutable.Map.empty[UUID, NetAddress]


  //******* Handlers ******
  boot uponEvent {
    case UpdateNodes(nodes) => handle {
      log.info("Generating LookupTable...");
      val lut = LookupTable.generate(nodes.keySet);
      logger.debug("Generated assignments:\n" + lut);
      trigger (new InitialAssignments(lut) -> boot);
    }
    case Booted(assignment: LookupTable, _) => handle {
      log.info("Got NodeAssignment, overlay ready.");
      lut = Some(assignment);
    }
  }

  pLink uponEvent {
    case PL_Deliver(self, UpdateNodes(nodes)) => handle{
      log.info("Generating new LookupTable...");
      lut = Some(LookupTable.generate(nodes.keySet));
    }


    // forwards a message to responsible node for the key
    case PL_Deliver(src, RouteMsg(key,op:Op)) => handle {

      srcMap += (op.id -> src);
      // gets responsible node
      val nodes = lut.get.lookup(key);
      // throws assertionException when nodes is empty
      assert(!nodes.isEmpty);
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
          val size = l.getNodes().size;
          trigger (PL_Send(src, msg.ack(size)) -> pLink);
        }
        case None => log.info(s"Rejecting connection request from ${src}, as system is not ready, yet.");
      }
    }
  }

  route uponEvent {
    // TODO DO we even need this
    // sends message to responsible node for the key
    case RouteMsg(key, msg) => handle {
      val nodes = lut.get.lookup(key);
      assert(!nodes.isEmpty);
      val i = Random.nextInt(nodes.size);
      val target = nodes.drop(i).head;
      log.info(s"ROUTING MESSAGE for key $key to $target");
      trigger(PL_Send(target, msg) -> pLink);

    }
  }
}
