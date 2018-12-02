# Networks
School project that implements BUMP client in Java

You can test your program by contacting the server, ulam.cs.luc.edu, and requesting files.  
If you contact on port 4515/4715, you get a response from a new port (the standard behavior).  
This doesn't go through NAT firewalls, so if you contact ulam2 on port 4516/4716 you get a response from the same port.  
No matter what file you ask for, the file you get is always the same.  

## At this point, the only special filenames that work on the "sameport" ports (4516/4517/4716/4717) are:
* vanilla
* lose2 (losedata2) 
* dup2 (dupdata2)
* spray.

## By asking port 4715 for the following names, you get the indicated behavior:
* vanilla- Normal transfer
* lose- Lose everything after the first windowful (min 3). It will be retransmitted when you retransmit the previous ACK.
* spray- Constant barrage of data[1]. Implies LOSE too. In this case, no timeout events will occur; you must check for elapsed time.
* delay- Delays sending packet 1, prompting a duplicate REQ and thus results in multiple server instances on multiple ports.
* reorder- Sends the first windowful in the wrong order.  

## These behaviors are also available on 4515/4715:
* dupdata2- DATA[2] is sent twice
* losedata2- DATA[2] is lost on initial send, until you resend ACK[1]
* marspacket- A packet from the wrong port (a "martian" port) arrives
* badopcode- A packet arrives with an incorrect opcode
* nofile- You get an error packet with a NO FILE error code.
