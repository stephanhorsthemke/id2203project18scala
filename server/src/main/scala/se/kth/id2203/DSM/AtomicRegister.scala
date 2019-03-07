package se.kth.id2203.DSM

import se.kth.id2203.BEB.Beb._
import se.kth.id2203.BEB._
import se.kth.id2203.PerfectLink.{PL_Deliver, PL_Send, PerfectLinkPort}
import se.kth.id2203.networking.NetAddress
import se.sics.kompics.sl._
import se.sics.kompics.{ComponentDefinition => _, Port => _}

import scala.collection.mutable

class AtomicRegister() extends ComponentDefinition {


  implicit def addComparators[A](x: A)(implicit o: math.Ordering[A]): o.Ops = o.mkOrderingOps(x);

  //subscriptions

  val nnar: NegativePort[AtomicRegisterPort] = provides[AtomicRegisterPort];
  val pLink: PositivePort[PerfectLinkPort] = requires[PerfectLinkPort];
  val beb: PositivePort[BebPort] = requires[BebPort];

  // size of  running partition
  var nRepl: Int = 3
  // size of building/new partition
  var nBuild :Int = 3

  val self: NetAddress = cfg.getValue[NetAddress]("id2203.project.address");
  val rank: Int = self.getPort();

  var store = mutable.Map.empty[String, StoreObject];

  //handlers

  nnar uponEvent {
    case AR_Read_Request(uuid, key, group) => handle {
      if (store.get(key).isEmpty) {
        store(key) = new StoreObject;
      }
      store(key).rid += 1;
      store(key).acks = 0;
      store(key).readlist = Map.empty;
      store(key).reading = true;
      store(key).idMap += (store(key).rid -> uuid);

      // Broadcast read request to all
      trigger(BEB_Broadcast(READ(store(key).rid, key), group) -> beb);
    }

    case AR_Write_Request(wval, key, uuid, group) => handle {
      if (store.get(key).isEmpty) {
        store(key) = new StoreObject;
      }

      store(key).rid += 1;
      store(key).acks = 0;
      store(key).readlist = Map.empty;
      store(key).writeval = Some(wval);
      store(key).idMap += (store(key).rid -> uuid);

      // Broadcast write request to all
      trigger(BEB_Broadcast(READ(store(key).rid, key), group) -> beb);
    }
  }

  beb uponEvent {
    //Broadcasted READ -> respond with local value and ts
    case BEB_Deliver(src, READ(readID, key), group) => handle {
      if (store.get(key).isEmpty) {
        store(key) = new StoreObject;
      }
      trigger(PL_Send(src, VALUE(readID, key, store(key).ts, store(key).wr, store(key).value, group)) -> pLink);
    }

    case BEB_Deliver(src, w: WRITE, _) => handle {
      if ((w.ts, w.wr) > (store(w.key).ts, store(w.key).wr)) {
        store(w.key).ts = w.ts;
        store(w.key).wr = w.wr;
        store(w.key).value = w.writeVal;
      }
      trigger(PL_Send(src, ACK(w.rid, w.key)) -> pLink);
    }
  }

  pLink uponEvent {
    // channel gets read request
    case PL_Deliver(src, v: VALUE) => handle {

      if (v.rid == store(v.key).rid) {
        store(v.key).readlist += (src -> (v.rid, v.ts, v.value));

        val n:Int = if (v.group == Replication) nRepl else nBuild
        if (store(v.key).readlist.size > n / 2) {
          val highest = store(v.key).readlist.valuesIterator.reduceLeft { (a, x) => if (a._2 > x._2) a else x };
          var maxts = highest._1;
          var rr = highest._2;
          store(v.key).readval = highest._3;
          store(v.key).readlist = Map.empty;

          var bcastval: Option[Any] = None;
          if (store(v.key).reading) {
            bcastval = store(v.key).readval;
          } else {
            rr = rank;
            maxts = maxts + 1;
            bcastval = store(v.key).writeval;
          }
          trigger(BEB_Broadcast(WRITE(store(v.key).rid, v.key, maxts, rr, bcastval), v.group) -> beb);

        }
      }
    }

    // an ACK from src arrives
    case PL_Deliver(src, v: ACK) => handle {


      //if register id is the current one (it is not an old ACK)
      if (v.rid == store(v.key).rid) {
        val uuid = store(v.key).idMap(store(v.key).rid);

        // increment ack
        store(v.key).acks += 1;

        val n:Int = if (v.group == Replication) nRepl else nBuild
        if (store(v.key).acks > n / 2) {
          store(v.key).acks = 0;
          if (store(v.key).reading) {
            store(v.key).reading = false;
            trigger(AR_Read_Response(store(v.key).readval, uuid) -> nnar);
          } else {
            trigger(AR_Write_Response(uuid) -> nnar)
          }
        }
      }
    }

    // sets the size of the partition
    case PL_Deliver(this.self, BEB_Topology(addr: Set[NetAddress], group)) => handle {
      if (group == Replication){
        nRepl = addr.size;
      } else if(group == Build){
        nBuild = addr.size;
      }
    }
  }
}