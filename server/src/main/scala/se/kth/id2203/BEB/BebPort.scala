package se.kth.id2203.BEB

import se.sics.kompics.sl._;

class BebPort extends Port {
  indication[BEB_Deliver];
  request[BEB_Broadcast];
}