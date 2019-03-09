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
        store(key) = new StoreObject(key);
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
        store(key) = new StoreObject(key);
      }

      store(key).rid += 1;
      store(key).acks = 0;
      store(key).readlist = Map.empty;
      store(key).writeval = Some(wval);
      store(key).idMap += (store(key).rid -> uuid);

      // Broadcast write request to all
      trigger(BEB_Broadcast(READ(store(key).rid, key), group) -> beb);
    }

    case AR_CAS_Request(cval, wval, key, uuid, group) => handle {
      if (store.get(key).isEmpty) {
        store(key) = new StoreObject(key);
      }

      store(key).rid += 1;
      store(key).acks = 0;
      store(key).readlist = Map.empty;
      store(key).writeval = Some(wval);
      store(key).compareval = Some(cval);
      store(key).idMap += (store(key).rid -> uuid);

      // Broadcast write request to all
      trigger(BEB_Broadcast(READ(store(key).rid, key), group) -> beb);
    }

    case AR_Range_Request(lowerBorder, upperBorder) => handle {
      var filtered = mutable.Map.empty[String, StoreObject];

      if (lowerBorder < upperBorder) {
        filtered = store.filter(x => x._1 >= lowerBorder && x._1 < upperBorder);
      } else {
        filtered = store.filter(x => x._1 >= lowerBorder || x._1 < upperBorder);
      }

      val values = mutable.Map.empty[String, Option[Any]];
      filtered.foreach(x => {
        values(x._2.key) = x._2.value;
      });

      trigger(AR_Range_Response(values) -> nnar)
    }
  }

  beb uponEvent {
    //Broadcasted READ -> respond with local value and ts
    case BEB_Deliver(src, READ(readID, key), group) => handle {
      if (store.get(key).isEmpty) {
        store(key) = new StoreObject(key);
      }
      trigger(PL_Send(src, VALUE(readID, key, store(key).ts, store(key).wr, store(key).value, group)) -> pLink);
    }

    case BEB_Deliver(src, w: WRITE, _) => handle {
//      log.debug("Received WRITE w.ts=" + w.ts + "; w.wr=" + w.wr + "; store.ts=" + store(w.key).ts + "; store.wr=" + store(w.key).wr + s" (${w.key})");

      if ((w.ts, w.wr) > (store(w.key).ts, store(w.key).wr)) {
//        log.debug(s"Doing WRITE (${w.key})");
        store(w.key).ts = w.ts;
        store(w.key).wr = w.wr;
        store(w.key).value = w.writeVal;
      } else {
//        log.debug(s"Not Doing WRITE (${w.key})");
      }
      trigger(PL_Send(src, ACK(w.rid, w.key)) -> pLink);
    }
  }

  pLink uponEvent {
    // channel gets read request
    case PL_Deliver(src, v: VALUE) => handle {

      if (v.rid == store(v.key).rid) {
        store(v.key).readlist += (src -> (v.ts, v.wr, v.value));

        val n:Int = if (v.group == Replication) nRepl else nBuild
        if (store(v.key).readlist.size > n / 2.0) {
//          log.debug("readlist: " + store(v.key).readlist);
          val highest = store(v.key).readlist.valuesIterator.reduceLeft { (a, b) => if ((a._1, a._2) > (b._1, b._2)) a else b };
//          log.debug("highest: " + highest);
          var maxts = highest._1;
          var rr = highest._2;
          store(v.key).readval = highest._3;
          store(v.key).readlist = Map.empty;

//          val uuid = store(v.key).idMap(store(v.key).rid);
//          if (store(v.key).compareval.isDefined) {
//            log.debug("Comparing c=" + store(v.key).compareval + " with r=" + store(v.key).readval + s"($uuid)");
//          }

          var bcastval: Option[Any] = None;
          if (store(v.key).reading // if read request
            || (store(v.key).compareval.isDefined && !store(v.key).readval.get.equals(store(v.key).compareval.get))) { // if cas request and not equal

            bcastval = store(v.key).readval;
          } else {
            rr = rank;
            maxts = maxts + 1;
            bcastval = store(v.key).writeval;
          }

//          if (store(v.key).compareval.isDefined) {
//            log.debug("Sending WRITE rid=" + store(v.key).rid + "; maxts=" + maxts + "; rr=" + rr + "; bcastval=" + bcastval + s" ($uuid)");
//          }
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
        if (store(v.key).acks > n / 2.0) {
          store(v.key).acks = 0;
          if (store(v.key).reading) {
            store(v.key).reading = false;
            trigger(AR_Read_Response(store(v.key).readval, uuid) -> nnar);
          } else if (store(v.key).compareval.isDefined) {
            store(v.key).compareval = None;
//            log.debug("CAS ready for response - value=" + store(v.key).value + " readval=" + store(v.key).readval + " writeval=" + store(v.key).writeval + " compareval=" + store(v.key).compareval + s"($uuid)");
            trigger(AR_CAS_Response(store(v.key).readval, uuid) -> nnar);
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