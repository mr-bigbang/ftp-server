package de.mr_bigbang.net.ftp;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class FtpClient {
    private Socket cmdConnection;

    public FtpClient(InetAddress server, int port) {
        try {
            this.cmdConnection = new Socket(server, port);
            // TODO Get Input-/Output-Streams
        } catch (IOException ex) {
            System.err.println("IOException: " + ex.getMessage());
        }
    }

    public FtpClient(String server, int port)
    throws UnknownHostException {
        this(InetAddress.getByName(server), port);
    }

    protected FtpReply sendCommand(FtpCommand cmd) {
        System.out.println("CLIENT: " + cmd.getCmd() + " " + cmd.getParam());
        var reply = new FtpReply(502); // Command not implemented;
        System.out.println("SERVER: " + reply.getCode() + " " + reply.getMessage());
        return reply;
    }

    /**
     * Anonymous login
     */
    public void login() {
        login("anonymous", "anonymous@example.com");
    }

    /**
     * Authenticate against FTP server
     *
     * @param username Name of the user
     * @param password Password of the user
     */
    public void login(String username, String password) {
        assert sendCommand(new FtpCommand("USER", username)).getCode() == 331;
        assert sendCommand(new FtpCommand("PASS", password)).getCode() == 230;
    }
}
