package se.kth.id2203.EPFD

import se.sics.kompics.sl._;

class EPFDPort extends Port {
  indication[Suspect];
  indication[Restore];
}
