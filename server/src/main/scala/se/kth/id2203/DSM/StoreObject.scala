package se.kth.id2203.DSM

import java.util.UUID

import se.kth.id2203.networking.NetAddress

class StoreObject {
  var (ts, wr) = (0, 0);
  var value: Option[Any] = None;
  var acks = 0;
  var readval: Option[Any] = None;
  var writeval: Option[Any] = None;
  var rid = 0;
  var readlist: Map[NetAddress, (Int, Int, Option[Any])] = Map.empty
  var reading = false;
  var compareval: Option[Any] = None;

  var idMap: Map[Int, UUID] = Map.empty;
}
