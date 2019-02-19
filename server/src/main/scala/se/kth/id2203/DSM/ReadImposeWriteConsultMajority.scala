package se.kth.id2203.DSM

import se.kth.id2203.BEB._
import se.kth.id2203.PerfectLink.{PL_Deliver, PL_Send, PerfectLinkPort}
import se.kth.id2203.networking.{NetAddress, NetMessage}
import se.sics.kompics.sl.{Init, _}
import se.sics.kompics.{KompicsEvent, Start, ComponentDefinition => _, Port => _}

class ReadImposeWriteConsultMajority(init: Init[ReadImposeWriteConsultMajority]) extends ComponentDefinition {

  //The following events are to be used internally by the Atomic Register implementation below
  case class ACK(rid: Int) extends KompicsEvent;
  case class READ(rid: Int) extends KompicsEvent;
  case class VALUE(rid: Int, ts: Int, wr: Int, value: Option[Any]) extends KompicsEvent;
  case class WRITE(rid: Int, ts: Int, wr: Int, writeVal: Option[Any]) extends KompicsEvent;
  /**
    * This augments tuples with comparison operators implicitly, which you can use in your code.
    * examples: (1,2) > (1,4) yields 'false' and  (5,4) <= (7,4) yields 'true'
    */
  implicit def addComparators[A](x: A)(implicit o: math.Ordering[A]): o.Ops = o.mkOrderingOps(x);


  //subscriptions

  val nnar = provides[AtomicRegister];
  val pLink = requires[PerfectLinkPort];
  val beb = requires[BebPort];

  //state and initialization

  val (self: NetAddress, n: Int, selfRank: Int) = init match {
    case Init(selfAddr: NetAddress, n: Int) => (selfAddr, n, selfRank)
  };

  var (ts, wr) = (0, 0);
  var value: Option[Any] = None;
  var acks = 0;
  var readval: Option[Any] = None;
  var writeval: Option[Any] = None;
  var rid = 0;
  var readlist: Map[NetAddress, (Int, Int, Option[Any])] = Map.empty
  var reading = false;

  //handlers

  nnar uponEvent {
    case AR_Read_Request() => handle {
      rid = rid + 1;
      acks = 0;
      readlist = Map.empty;
      reading = true;

      println(selfRank + " :: " + System.currentTimeMillis() + ":: ReadRequest:  " + rid);

      //Broadcast read request to all
      trigger(BEB_Broadcast(READ(rid)) -> beb);

    }
    case AR_Write_Request(wval) => handle {
      rid = rid + 1;
      writeval = Some(wval);
      acks = 0;
      readlist = Map.empty;
      println(selfRank + " :: " + System.currentTimeMillis() + ":: WriteRequest:  " + rid);
      trigger(BEB_Broadcast(READ(rid)) -> beb);
    }
  }

  beb uponEvent {

    //Broadcasted READ -> respond with local value and ts
    case BEB_Deliver(src, READ(readID)) => handle {
      // it actually only needs value.value and value.ts
      //println(selfRank + " :: " + System.currentTimeMillis() + ":: ReadBroadCDelivered:  " + readID);
      trigger(PL_Send(src, VALUE(readID, ts, wr, value)) -> pLink);
    }


    case BEB_Deliver(src, w: WRITE) => handle {
      println(selfRank + " :: " + System.currentTimeMillis() + ":: WriteRequestDelivered:  " + w.rid);
      if((w.ts,w.wr) > (ts, wr)){
        ts = w.ts;
        wr = w.wr;
        value = w.writeVal;
        println(selfRank + " :: " + System.currentTimeMillis() + ":: New TS:  " + ts + ":: new VAlue " + value);
      }
      trigger(PL_Send(src, ACK(w.rid)) -> pLink);
    }
  }
  pLink uponEvent {
    // channel gets read request
    case PL_Deliver(src, v: VALUE) => handle {


      if (v.rid == rid) {


        readlist = readlist + (src -> (v.rid, v.ts, v.value));

        if(readlist.size > n/2 ){

          println(selfRank + " :: " + System.currentTimeMillis() + ":: Majority READs:  " + readlist);
          var highest = readlist.valuesIterator.reduceLeft{(a,x) => if(a._2>x._2) a else x};
          println(selfRank + " :: " + System.currentTimeMillis() + ":: Highest READ:  " + highest);
          var maxts = highest._1;
          var rr = highest._2;
          readval = highest._3;

          readlist = Map.empty;
          var bcastval: Option[Any] = None;
          if (reading == true){
            bcastval = readval;
          }
          else{
            rr = selfRank;
            maxts = maxts + 1;
            bcastval = writeval;
          }
          trigger(BEB_Broadcast(WRITE(rid, maxts, rr, bcastval)) -> beb);

        }
      }
    }

    // an ACK from src arrives
    case PL_Deliver(src, v: ACK) => handle {
      //if register id is the curreent one (it is not an old ACK)
      if (v.rid == rid) {
        // increment ack
        acks = acks + 1;
        if (acks > n/2){
          println(selfRank + " :: " + System.currentTimeMillis() + ":: Majority ACKs, reading =  " + reading);
          acks = 0;
          if (reading == true){
            reading = false;
            trigger(AR_Read_Response(readval) -> nnar);
          }else{
            trigger(AR_Write_Response() -> nnar)
          }
        }
      }
    }
  }
}