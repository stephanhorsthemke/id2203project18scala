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
package se.kth.id2203;

import se.kth.id2203.BEB.{Beb, BebPort}
import se.kth.id2203.DSM.{AtomicRegister, AtomicRegisterPort}
import se.kth.id2203.PerfectLink._
import se.kth.id2203.Paxos._
import se.kth.id2203.EPFD._
import se.kth.id2203.bootstrapping._
import se.kth.id2203.kvstore.KVService
import se.kth.id2203.networking.{NetAddress, NetAddressConverter, ScallopConverters}
import se.kth.id2203.overlay._
import se.kth.id2203.nodeController.NodeController
import se.sics.kompics.sl._
import se.sics.kompics.Init
import se.sics.kompics.network.Network
import se.sics.kompics.timer.Timer;

class ParentComponent extends ComponentDefinition {


  //******* Ports ******
  val net = requires[Network];
  val timer = requires[Timer];
  //******* Children ******
  val self:NetAddress = cfg.getValue[NetAddress]("id2203.project.address");

  val beb = create(classOf[Beb], Init.NONE)
  val fd = create(classOf[EPFD], Init.NONE)
  val paxos = create(classOf[Paxos], Init.NONE)
  val pLink = create(classOf[PerfectLink], Init.NONE);
  val rc = create(classOf[ReplicationController], Init.NONE);
  val kv = create(classOf[KVService], Init.NONE);
  val ar = create(classOf[AtomicRegister], Init.NONE);
  val nc = create(classOf[NodeController], Init.NONE);
  val boot = cfg.readValue[NetAddress]("id2203.project.bootstrap-address") match {
    case Some(_) => create(classOf[BootstrapClient], Init.NONE); // start in client mode
    case None    => create(classOf[BootstrapServer], Init.NONE); // start in server mode
  }




  {
    //Perfect Link
    connect[Timer](timer -> pLink);
    connect[Network](net -> pLink);
    //BEB
    connect[PerfectLinkPort](pLink -> beb);
    //FD
    connect[PerfectLinkPort](pLink -> fd)
    connect[Timer](timer -> fd)
    //Paxos
    connect[PerfectLinkPort](pLink -> paxos);
    connect[BebPort](beb -> paxos);
    //AR
    connect[PerfectLinkPort](pLink -> ar)
    connect[BebPort](beb -> ar)
    connect[PerfectLinkPort](pLink -> boot);
    //NodeController
    connect[PerfectLinkPort](pLink -> nc);
    connect[PaxosPort](paxos -> nc)
    connect[BebPort](beb -> nc)
    connect[EPFDPort](fd -> nc)
    // ReplicationController
    connect[PerfectLinkPort](pLink -> rc);
    // KV
    connect(Routing)(rc -> kv);
    connect[PerfectLinkPort](pLink -> kv);
    connect[AtomicRegisterPort](ar -> kv);

  }
}
