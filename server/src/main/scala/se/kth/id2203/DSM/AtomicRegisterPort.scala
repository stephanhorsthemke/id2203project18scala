package se.kth.id2203.DSM

import se.sics.kompics.sl._;

class AtomicRegisterPort extends Port {
  request[AR_Read_Request]
  request[AR_Write_Request]
  request[AR_Range_Request]
  indication[AR_Read_Response]
  indication[AR_Write_Response]
  indication[AR_Range_Response]
}
