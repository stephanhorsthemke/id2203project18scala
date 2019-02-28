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
package se.kth.id2203.kvstore;

import java.util.UUID

import se.kth.id2203.DSM._
import se.kth.id2203.PerfectLink.{PL_Deliver, PL_Send, PerfectLinkPort}
import se.kth.id2203.networking._
import se.kth.id2203.overlay.Routing
import se.sics.kompics.sl._
import se.sics.kompics.network.Network;

class KVService extends ComponentDefinition {

  //******* Ports ******
  val route = requires(Routing);
  val pLink = requires[PerfectLinkPort]
  val nnar = requires[AtomicRegisterPort];
  //******* Fields ******
  val self = cfg.getValue[NetAddress]("id2203.project.address");
  var srcMap = scala.collection.mutable.Map.empty[UUID, (NetAddress, Op)];


  //******* Hop.idandlers ******
  pLink uponEvent {
    case PL_Deliver(src, op :Op) if op.opName == "GET" => handle {
      log.info("Got operation GET! from: " + src);
      srcMap += (op.id -> (src, op));
      trigger(AR_Read_Request(op.id, op.key) -> nnar);
    }

    case PL_Deliver(src, op :Op) if op.opName == "PUT" => handle {
      log.info("Got operation PUT!");
      srcMap += (op.id -> (src, op));
      trigger(AR_Write_Request(op.value, op.key, op.id) -> nnar);
    }

    case PL_Deliver(src, op :Op) if op.opName == "CAS" => handle {
      log.info("Got operation CAS!");
      trigger(PL_Send(src, op.response(OpCode.NotImplemented)) -> pLink);
    }
  }

  nnar uponEvent {

    case AR_Read_Response(value: Option[Any], uuid: UUID) => handle {

      val srcOp = srcMap.remove(uuid);
      if(srcOp != None){
        val (src: NetAddress, op: Op) = srcOp.get
        if (value.nonEmpty) {
          log.info("Value found: " + value + " from: " + src)
          trigger(PL_Send(src, op.response(OpCode.Ok, value)) -> pLink);
        } else {
          log.debug("Value not found: " + src)
          trigger(PL_Send(src, op.response(OpCode.NotFound)) -> pLink);
        }
      }else{
        log.debug("No entry for uuid, either duplicate or wrong message: " + uuid)
      }

    }

    case AR_Write_Response(uuid: UUID) => handle {
      val (src: NetAddress, op: Op) = srcMap(uuid);
      log.info("Value written")
      trigger(PL_Send(src, op.response(OpCode.Ok)) -> pLink);
    }

  }
}
