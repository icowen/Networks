/*
    WUMP (specifically BUMP) in java. starter file
 */
import java.lang.*;     //pld
import java.net.*;      //pld
import java.io.*;
//import wumppkt;         // be sure wumppkt.java is in your current directory
//import java.io.Externalizable;

// As is, this packet should receive data[1] and time out.
// If you send the ACK to the correct port, you should receive data[2]
// If you update expected_block, you should receive the entire file, for "vanilla"
// If you write the sanity checks, you should receive the entire file in all cases

public class wclient {

    //============================================================
    //============================================================

    static public void main(String args[]) {
        int srcport;
        int destport = wumppkt.SERVERPORT;
//        destport = wumppkt.SAMEPORT;		// 4716; server responds from same port
        String filename = "vanilla";
//        String filename = "lose2";
//        String filename = "spray";
//        String filename = "dup2";
//        String filename = "lose";
//        String filename = "delay";
//        String filename = "reorder";
//        String filename = "dupdata2";
//        String filename = "losedata2";
//        String filename = "marspacket";
//        String filename = "badopcode";
//        String filename = "nofile";

        String desthost = "ulam.cs.luc.edu";
        int winsize = 1;
        int latchport = 0;
        short THEPROTO = wumppkt.BUMPPROTO;
        wumppkt.setproto(THEPROTO);

        if (args.length > 0) filename = args[0];
        if (args.length > 1) winsize = Integer.parseInt(args[1]);
        if (args.length > 2) desthost = args[2];

        DatagramSocket s;
        try {
            s = new DatagramSocket();
        }
        catch (SocketException se) {
            System.err.println("no socket available");
            return;
        }

        try {
            s.setSoTimeout(wumppkt.INITTIMEOUT);       // time in milliseconds
        } catch (SocketException se) {
            System.err.println("socket exception: timeout not set!");
        }

        if (args.length > 3) {
            System.err.println("usage: wclient filename  [winsize [hostname]]");
            //exit(1);
        }

        // DNS lookup
        InetAddress dest;
        System.err.print("Looking up address of " + desthost + "...");
        try {
            dest = InetAddress.getByName(desthost);
        }
        catch (UnknownHostException uhe) {
            System.err.println("unknown host: " + desthost);
            return;
        }
        System.err.println(" got it!");

        // build REQ & send it
        wumppkt.REQ req = new wumppkt.REQ(winsize, filename); // ctor for REQ

        System.err.println("req size = " + req.size() + ", filename=" + req.filename());

        DatagramPacket lastSent;            // we don't set the address here!
        DatagramPacket reqDG
                = new DatagramPacket(req.write(), req.size(), dest, destport);
        try {   s.send(reqDG);
                lastSent = reqDG;
        } catch (IOException ioe) {
            System.err.println("send() failed");
            return;
        }
        long starttime = System.currentTimeMillis();
        long sendtime = starttime;

        //============================================================

        // now receive the response
        DatagramPacket replyDG            // we don't set the address here!
                = new DatagramPacket(new byte[wumppkt.MAXSIZE] , wumppkt.MAXSIZE);
        DatagramPacket ackDG = new DatagramPacket(new byte[0], 0);
        DatagramPacket errorDG = new DatagramPacket(new byte[0], 0);
        ackDG.setAddress(dest);
        ackDG.setPort(destport);		// this is wrong for wumppkt.SERVERPORT version
        errorDG.setAddress(dest);
        errorDG.setPort(destport);

        int expected_block = 1;

        wumppkt.DATA  data;
        wumppkt.ERROR error;
        wumppkt.ACK   ack;

        int proto = THEPROTO;        // for proto of incoming packets
        int opcode;
        int length;
        int blocknum = 0;

        //====== HUMP =====================================================

        // if you want to implement HUMP, for better NAT-firewall traversal,
        // this is where that part goes. You also need to set THEPROTO=HUMPPROTO,
        // and use SERVERPORT, not SAMEPORT, above.
        // s.receive(replyDG), in the usual try-catch
        // byte[] replybuf = replyDG.getData();
        // check that replybuf looks like a HANDOFF. If so:
        // wumppkt.HANDOFF handoff = new wumppkt.HANDOFF(replybuf);
        // int newport = handoff.newport();
        // create ACK[0] and send it. Copy the ACK code towards the end of the main loop.
        // The port to send it too should be newport, extracted above.

        //====== MAIN LOOP ================================================

        while (true) {
            // get packet
            if(System.currentTimeMillis() - sendtime > wumppkt.INITTIMEOUT && expected_block != blocknum) {
                try {
                    System.err.println("Soft timeout- retransmitting last sent packet");
                    s.send(lastSent);
                    sendtime = System.currentTimeMillis();
                }
                catch (IOException ioe) {
                    System.err.println("send() failed");
                }
            }

            try {
                s.receive(replyDG);
            }
            catch (SocketTimeoutException ste) {
                // what do you do here??; retransmit of previous packet here
                // Send previous ACK
                continue;
                //continue;
            }
            catch (IOException ioe) {
                System.err.println("receive() failed");
                return;
            }

            byte[] replybuf = replyDG.getData();
            proto   = wumppkt.proto(replybuf);
            opcode  = wumppkt.opcode(replybuf);
            length  = replyDG.getLength();
            srcport = replyDG.getPort();

            /* The new packet might not actually be a DATA packet.
             * But we can still build one and see, provided:
             *   1. proto =   THEPROTO
             *   2. opcode =  wumppkt.DATAop
             *   3. length >= wumppkt.DHEADERSIZE
             */

            data = null; error = null;
            blocknum = 0;

            // Proto Check
            if (proto != THEPROTO) {
                System.out.println("Wrong Proto");
                error = new wumppkt.ERROR(wumppkt.EBADPROTO, replybuf);
            }

            // Port check
            if( expected_block > 1 && replyDG.getPort() != latchport) {
                System.err.println("Wrong Port");
                error = new wumppkt.ERROR((short)proto, (short)wumppkt.EBADPORT);
            }

            // Packet size check
            if (length < wumppkt.DHEADERSIZE) {
                System.err.println("No File Error");
                error = new wumppkt.ERROR((short)proto, (short)wumppkt.ENOFILE);
            }

            // opcode Check
            if (  opcode == wumppkt.DATAop ) {              //Data packet
                data = new wumppkt.DATA(replybuf, length);
                blocknum = data.blocknum();
            } else if (  opcode == wumppkt.ERRORop ) {      //Error packet
                error = new wumppkt.ERROR(replybuf);
            } else {
                System.out.println("Bad op code");
                error = new wumppkt.ERROR((short)proto, (short)wumppkt.EBADOPCODE);
            }

            //Send error packet if there is one
            if (error != null) {
                errorDG.setData(error.write());
                errorDG.setLength(error.size());
                errorDG.setPort(srcport);
                try {
                    s.send(errorDG);
                } catch (IOException ioe) {
                    System.err.println("send() failed");
                    return;
                }
                System.err.println("Error packet rec'd; code " + error.errcode());
                if(error.errcode() == 4) return;    //End connection if no file error
                continue;
            }

            if (data == null) continue;		// typical error check, but you should
            printInfo(replyDG, data, starttime);

            // now check the packet for appropriateness
            // if it passes all the checks:
            // write data, increment expected_block
            // exit if data size is < 512

            // The following is for you to do:
            // check port, packet size, type, block, etc
            // latch on to port, if block == 1

            // block_num check
            if (blocknum != expected_block) {
                System.err.println("Received incorrect packet. Expecting: "+
                        expected_block+ ". Got: "+blocknum);
                continue;
            }

            // Latched
            if (data.blocknum() == 1) {
                latchport = replyDG.getPort();
            }

            // write data
            System.out.write(data.bytes(), 0, data.size() - wumppkt.DHEADERSIZE);

            // send ack
            ack = new wumppkt.ACK(expected_block);
            ackDG.setData(ack.write());
            ackDG.setLength(ack.size());
            ackDG.setPort(srcport);
            try {   s.send(ackDG);
                    lastSent = ackDG;
            } catch (IOException ioe) {
                System.err.println("send() failed");
                return;
            }
            sendtime = System.currentTimeMillis();
            expected_block++;

            //Dallying State
            if (length < wumppkt.MAXDATASIZE) {
                System.err.println("Packet received less then 512");
                System.err.println("Dallying State");
                while (System.currentTimeMillis()-sendtime < wumppkt.INITTIMEOUT *2) {
                    try {
                        s.receive(replyDG);
                    } catch (SocketTimeoutException ste) {
                        System.err.println("No packet received, checking again.");
                    } catch (IOException ioe) {
                        System.err.println("receive() failed");
                        return;
                    }

                    if(replyDG.getPort() == srcport &&
                            getblock(replyDG.getData()) == expected_block) {
                        try {
                            s.send(lastSent);
                            sendtime = System.currentTimeMillis();
                        } catch (IOException ioe) {
                            System.err.println("send() failed");
                            return;
                        }

                    }

                }
                System.err.println("No final data packet received- Connection ending...");
                return;
            }
        } // while
    }

    // print packet length, protocol, opcode, source address/port, time, blocknum
    static private void printInfo(DatagramPacket pkt, wumppkt.DATA data, long starttime) {
        byte[] replybuf = pkt.getData();
        int proto = wumppkt.proto(replybuf);
        int opcode = wumppkt.opcode(replybuf);
        int length = data.data().length;
        // the following seven items we can print always
        System.err.print("rec'd packet: len=" + length);
        System.err.print("; proto=" + proto);
        System.err.print("; opcode=" + opcode);
        System.err.print("; src=(" + pkt.getAddress().getHostAddress() + "/" + pkt.getPort()+ ")");
        System.err.print("; time=" + (System.currentTimeMillis()-starttime));
        System.err.println();
        if (data==null)
            System.err.println("         packet does not seem to be a data packet");
        else
            System.err.println("         DATA packet blocknum = " + data.blocknum());
    }

    // extracts blocknum from raw packet
    // blocknum is laid out in big-endian order in b[4]..b[7]
    static public int getblock(byte[] buf) {
        //if (b.length < 8) throw new IOException("buffer too short");
        return  (((buf[4] & 0xff) << 24) |
                ((buf[5] & 0xff) << 16) |
                ((buf[6] & 0xff) <<  8) |
                ((buf[7] & 0xff)      ) );
    }


}