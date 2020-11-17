package de.mr_bigbang.net.tftp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;

enum Type {
    /*
    opcode  operation
    1     Read request (RRQ)
    2     Write request (WRQ)
    3     Data (DATA)
    4     Acknowledgment (ACK)
    5     Error (ERROR)
     */
    RRQ((byte)0x01),
    WRQ((byte)0x02),
    DATA((byte)0x03),
    ACK((byte)0x04),
    ERROR((byte)0x05);

    private byte opcode;
    public byte getOpcode() {
        return opcode;
    }

    protected void setOpcode(byte opcode) {
        if (opcode <= 0 || opcode > 5) {
            throw new IllegalArgumentException();
        }
        this.opcode = opcode;
    }

    Type(byte opcode) {
        setOpcode(opcode);
    }
}

enum Mode {
    NETASCII,
    OCTET,
    MAIL, // Obsolete as of RFC1350 Section 1 P2 ("The mail mode is obsolete and should not be implemented or used.")
}

abstract class TFTP {
    public Mode mode = Mode.NETASCII;
    protected void setMode(String mode) {
        if (mode.equalsIgnoreCase("netascii")) {
            this.mode = Mode.NETASCII;
        } else if (mode.equalsIgnoreCase("octet")) {
            this.mode = Mode.OCTET;
        } else {
            throw new IllegalArgumentException();
        }
    }

    public Mode getMode() { return mode; }
}

class RRQ extends TFTP {
    private String filename;
    protected void setFilename(String filename) { this.filename = filename; }
    public String getFilename() { return this.filename; }

    public RRQ(byte[] packet) {
        super();
        var filenameBuilder = new StringBuilder();

        int marker = 0;
        for (var i = 2; i < packet.length; ++i) {
            marker = i;
            if (packet[i] == 0x00) break; // End of filename
            filenameBuilder.append((char)packet[i]);
        }
        setFilename(filenameBuilder.toString());

        var modeBuilder = new StringBuilder();
        for (var i = marker + 1; i < packet.length; ++i) {
            if (packet[i] == 0x00) break;
            modeBuilder.append((char)packet[i]);
        }
        setMode(modeBuilder.toString());
    }

    @Override
    public String toString() {
        return "RRQ " + getFilename() + " " + getMode().name();
    }
}

class WRQ extends TFTP {
    private String filename;
    protected void setFilename(String filename) { this.filename = filename; }
    public String getFilename() { return filename; }

    public WRQ(byte[] packet) {
        var filenameBuilder = new StringBuilder();
        int marker = 0;
        for (var i = 2; i < packet.length; ++i) {
            marker = i;
            if (packet[i] == 0x00) break;
            filenameBuilder.append((char)packet[i]);
        }
        setFilename(filenameBuilder.toString());
    }

    @Override
    public String toString() {
        return "WRQ " + getFilename() + " " + getMode().name();
    }
}

class ERROR extends TFTP {
    private String message;
    public String getMessage() { return message; }
    protected void setMessage(String message) { this.message = message; }

    private ErrorCode error;
    public ErrorCode getError() { return error; }
    protected void setError(ErrorCode error) { this.error = error; }

    public ERROR() {
        setError(ErrorCode.NOT_DEFINED);
        setMessage("Benis :-DDD");
    }
}

class DATA extends TFTP {

}

class ACK extends TFTP {


}

class TftpServer {
    public void send(InetAddress recp, int port, TFTP packet) {
        try (var ds = new DatagramSocket()) {
            ds.connect(recp, port);
            ds.send(new DatagramPacket(new byte[] {0x00, 0x05, 0x00, 0x01, 0x33, 0x34, 0x00 }, 0, 7));
        } catch (SocketException ex) {

        } catch (IOException ex) {

        }
    }

    private void debug(byte[] data) {
        for (byte datum : data) {
            System.out.printf("0x%02X ", datum);
        }
        System.out.println();
    }

    public void parse(DatagramPacket packet) {
        System.out.println("Received:");
        debug(packet.getData());

        // Parse received data
        Type t = switch (packet.getData()[0] + packet.getData()[1]) {
            case 1 -> Type.RRQ;
            case 2 -> Type.WRQ;
            case 3 -> Type.DATA;
            case 4 -> Type.ACK;
            case 5 -> Type.ERROR;
            default -> throw new IllegalArgumentException();
        };

        System.out.println("Type: " + t.name());

        TFTP ffff;
        switch (t) {
            case RRQ:
                ffff = new RRQ(packet.getData());
                send(packet.getAddress(), packet.getPort(), new ERROR());
                break;
            case WRQ:
                ffff = new WRQ(packet.getData());
                break;
            case DATA:
            case ACK:
            case ERROR:
            default:
                ffff = new RRQ(packet.getData());
                break;
        }

        System.err.println(ffff.toString());
    }
}

public class Main {
    public static void main(String[] args) {
        // This module aims to implement RFC1350 (THE TFTP PROTOCOL (REVISION 2))

        System.err.println("This is a work in progress and I have no idea what I'm doing...");

        var server = new TftpServer();

        try (var ss = new DatagramSocket(69)) {
            while (!ss.isClosed()) {
                byte[] buf = new byte[512];
                var p = new DatagramPacket(buf, buf.length);
                ss.receive(p);

                server.parse(p);
            }
        } catch (IOException ex) {
            System.err.println("IOException: " + ex.getMessage());
        }
    }
}
