package de.mr_bigbang.net.ftp;

import java.io.IOException;
import java.net.ServerSocket;

public class Main {

    public static void main(String[] args) {
        /*
        If you see: `IOException: Eine bestehende Verbindung wurde softwaregesteuert durch den Hostcomputer abgebrochen`
        in passive mode you might wanna check your Firewall to allow access to high ports
         */
        try (var ss = new ServerSocket(21)) {
            System.out.printf("Awaiting connections on %s...%n", ss.getLocalSocketAddress());

            while (!ss.isClosed()) {
                var s = ss.accept();
                System.out.printf("Connection from %s accepted!%n", s.getRemoteSocketAddress());

                var ftp = new Thread(new FtpServer(s));
                ftp.start();
            }
        } catch (IOException ex) {
            System.err.println("IOException: " + ex.getMessage());
        }
    }
}
