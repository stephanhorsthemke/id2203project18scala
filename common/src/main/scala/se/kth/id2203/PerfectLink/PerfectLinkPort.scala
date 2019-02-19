package se.kth.id2203.PerfectLink

import se.sics.kompics.sl.Port

class PerfectLinkPort extends Port {
  request[PL_Send]
  indication[PL_Deliver]
}