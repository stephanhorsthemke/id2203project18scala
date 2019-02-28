package se.kth.id2203.Paxos

import se.sics.kompics.sl._;


class PaxosPort extends Port{
  request[C_Propose];
  indication[C_Decide];
}

