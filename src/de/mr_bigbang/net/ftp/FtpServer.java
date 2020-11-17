package de.mr_bigbang.net.ftp;

import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

// TODO Flags for RFC support level?
// if (RFC2389) { FEAT(); } else { sendReply(new FtpReply(502, "Command not implemented.")); }

public class FtpServer implements Runnable {
    //region Properties
    /*
     *  Config
     */
    private final Map<String, String> users = new HashMap<>();

    private boolean strictMode = true; // Prevent non-compliant behaviour (like LIST -a)
    public boolean getStrictMode() { return strictMode; }
    protected void setStrictMode(final boolean strictMode) { this.strictMode = strictMode; }

    private boolean anonymousLogin = false;
    public boolean getAnonymousLogin() { return anonymousLogin; }
    protected void setAnonymousLogin(final boolean anonymousLogin){ this.anonymousLogin = anonymousLogin; }

    /*
     *  State
     */
    private TypeCode typeCode = TypeCode.ASCII; // RFC959 P. 42
    private FormCode formCode = FormCode.NON_PRINT; // RFC959 P. 42
    private TransmissionMode transmissionMode = TransmissionMode.STREAM; // RFC959 P. 42
    private DataStructure dataStructure = DataStructure.FILE; // RFC959 P. 42

    private String username;
    public String getUsername() { return username; }
    protected void setUsername(@NotNull final String username) { this.username = username; }

    private boolean authorised = false;
    public boolean getAutorised() { return authorised; }
    protected void setAuthorised(final boolean authorised) { this.authorised = authorised; }

    boolean passiveMode = true;
    private void setPassiveMode(final boolean passiveMode) { this.passiveMode = passiveMode; }
    public boolean getPassiveMode() { return this.passiveMode; }

    private InetAddress clientAddress;
    protected InetAddress getClientAddress() { return clientAddress; }
    protected void setClientAddress(final InetAddress clientAddress) { this.clientAddress = clientAddress; }

    private int clientPort;
    protected int getClientPort() { return clientPort; }
    protected void setClientPort(final int clientPort) { this.clientPort = clientPort; }

    private long startPosition = 0;
    protected long getStartPosition() { return startPosition; }
    protected void setStartPosition(final long startPosition) {
        if (startPosition < 0) {
            throw new IllegalArgumentException();
        }
        this.startPosition = startPosition;
    }
    //endregion

    // Default directory = Location from which the Server was started
    File currentDirectoryPath = new File(System.getProperty("user.dir"));
    public String getCurrentDirectoryPath() {
        try {
            // Some clients (like WinSCP) don't recognise Windows-style paths
            return currentDirectoryPath.getCanonicalPath().replace('\\', '/');
        } catch (IOException ex) {
            System.err.println("IOException: " + ex.getMessage());
            return new File(System.getProperty("user.dir")).getPath();
        }
    }
    /**
     * Change current directory
     *
     * @param pathname Pathname may be absolute or relative
     */
    protected void setCurrentDirectoryPath(@NotNull final String pathname) {
        var cwd = new File(pathname);
        if (cwd.isAbsolute()) {
            currentDirectoryPath = cwd;
        } else {
            currentDirectoryPath = new File(currentDirectoryPath.getPath(), pathname);
        }
    }

    protected File[] getCurrentDirectoryFiles() {
        var files = currentDirectoryPath.listFiles();
        return Objects.requireNonNullElseGet(files, () -> new File[0]);
    }

    /**
     * Connection established by client to send FTP commands
     */
    private final Socket cmdConnection;
    private BufferedReader br;
    private PrintWriter bw;

    /**
     * Socket for passive FTP.
     *
     * Initialised with PASV command.
     */
    private ServerSocket dataConnection;

    public FtpServer(final Socket s) {
        cmdConnection = s;

        // TODO Read from config / commandline
        users.put("michael", "123456");
        setStrictMode(false);
        setAnonymousLogin(true);

        try {
            // Connection has been established, get In-/Output-Streams
            var os = cmdConnection.getOutputStream();
            var is = cmdConnection.getInputStream();

            this.br = new BufferedReader(new InputStreamReader(is));
            this.bw = new PrintWriter(new OutputStreamWriter(os));
        } catch (IOException ex) {
            System.err.println("IOException: " + ex.getMessage());
        }
    }

    /**
     * Parse FTP commands received from client and take appropriate actions.
     *
     * @param command FTP command sent by client
     */
    protected void parseCommand(final String command) {
        System.out.println("CLIENT: " + command);

        if (command == null) {
            System.out.println("CLIENT sent empty data, closing connection.");
            try {
                cmdConnection.close();
            } catch (IOException ex) {
                System.err.println("IOException: " + ex.getMessage());
            }
            return;
        }

        String[] splitCommand = command.split(" ");
        switch (splitCommand[0].toUpperCase(Locale.ROOT)) { // FTP commands are case insensitive (RFC959 4.3 P45)
            case "ACCT":
                if (splitCommand.length == 1) {
                    sendReply(new FtpReply(501)); // Syntax error in parameters or arguments.
                } else {
                    ACCT(splitCommand[1]);
                }
                break;
            case "XMKD": // Alias for MKD as of RFC1123 4.1.3.1 P35
            case "MKD":
                if (splitCommand.length == 1) {
                    sendReply(new FtpReply(501)); // Syntax error in parameters or arguments.
                } else if (splitCommand.length == 2) {
                    MKD(splitCommand[1]);
                } else {
                    // pathname has spaces
                    StringBuilder filename = new StringBuilder(splitCommand[1]);
                    for (byte i = 2; i < splitCommand.length; ++i) {
                        filename.append(" ").append(splitCommand[i]);
                    }
                    MKD(filename.toString());
                }
                break;
            case "XRMD": // Alias for RMD as of RFC1123 4.1.3.1 P35
            case "RMD":
                if (splitCommand.length == 1) {
                    sendReply(new FtpReply(501)); // Syntax error in parameters or arguments.
                } else if (splitCommand.length == 2) {
                    RMD(splitCommand[1]);
                } else {
                    // pathname has spaces
                    StringBuilder filename = new StringBuilder(splitCommand[1]);
                    for (byte i = 2; i < splitCommand.length; ++i) {
                        filename.append(" ").append(splitCommand[i]);
                    }
                    RMD(filename.toString());
                }
                break;
            case "XPWD": // Alias for PWD as of RFC1123 4.1.3.1 P35
            case "PWD":
                PWD();
                break;
            case "XCWD": // Alias for CWD as of RFC1123 4.1.3.1 P35
            case "CWD":
                if (splitCommand.length == 1) {
                    sendReply(new FtpReply(501)); // Syntax error in parameters or arguments.
                } else if (splitCommand.length == 2) {
                    CWD(splitCommand[1]);
                } else {
                    // pathname has spaces
                    StringBuilder filename = new StringBuilder(splitCommand[1]);
                    for (byte i = 2; i < splitCommand.length; ++i) {
                        filename.append(" ").append(splitCommand[i]);
                    }
                    CWD(filename.toString());
                }
                break;
            case "XCUP": // Alias for CDUP as of RFC1123 4.1.3.1 P35
            case "CDUP":
                CDUP();
                break;
            case "DELE":
                if (splitCommand.length == 1) {
                    sendReply(new FtpReply(501)); // Syntax error in parameters or arguments.
                } else if (splitCommand.length == 2) {
                    DELE(splitCommand[1]);
                } else {
                    // pathname has spaces
                    StringBuilder filename = new StringBuilder(splitCommand[1]);
                    for (byte i = 2; i < splitCommand.length; ++i) {
                        filename.append(" ").append(splitCommand[i]);
                    }
                    DELE(filename.toString());
                }
                break;
            case "LIST":
                if (splitCommand.length == 1) {
                    LIST();
                } else if (splitCommand.length == 2) {
                    LIST(splitCommand[1]);
                } else {
                    // pathname has spaces
                    StringBuilder filename = new StringBuilder(splitCommand[1]);
                    for (byte i = 2; i < splitCommand.length; ++i) {
                        filename.append(" ").append(splitCommand[i]);
                    }
                    LIST(filename.toString());
                }
                break;
            case "NLST":
                if (splitCommand.length == 1) {
                    NLST();
                } else if(splitCommand.length == 2) {
                    NLST(splitCommand[1]);
                } else {
                    // pathname has spaces
                    StringBuilder filename = new StringBuilder(splitCommand[1]);
                    for (byte i = 2; i < splitCommand.length; ++i) {
                        filename.append(" ").append(splitCommand[i]);
                    }
                    NLST(filename.toString());
                }
                break;
            case "SYST":
                SYST();
                break;
            case "PASV":
                PASV();
                break;
            case "REST":
                if (splitCommand.length != 1) {
                    REST(Long.parseLong(splitCommand[1]));
                } else {
                    sendReply(new FtpReply(501)); // Syntax error in parameters or arguments.
                }
                break;
            case "PASS":
                if (splitCommand.length == 2) {
                    PASS(splitCommand[1]);
                } else {
                    sendReply(new FtpReply(501)); // Syntax error in parameters or arguments
                }
                break;

            // RFC2389
            case "FEAT":
                if (splitCommand.length <= 1) {
                    FEAT();
                } else {
                    sendReply(new FtpReply(501)); // Syntax error in parameters or arguments.
                }
                break;
            case "OPTS":
                // TODO Implement
                sendReply(new FtpReply(502));
                break;

            // Minimum implementation
            // RFC959 5.1 P43
            case "USER":
                if (splitCommand.length != 1) {
                    USER(splitCommand[1]);
                } else {
                    sendReply(new FtpReply(501)); // Syntax error in parameters or arguments.
                }
                break;
            case "QUIT":
                QUIT();
                break;
            case "PORT":
                if (splitCommand.length != 1) {
                    String[] octets = splitCommand[1].split(",");
                    PORT(Integer.parseInt(octets[0]),
                            Integer.parseInt(octets[1]),
                            Integer.parseInt(octets[2]),
                            Integer.parseInt(octets[3]),
                            Integer.parseInt(octets[4]),
                            Integer.parseInt(octets[5]));
                } else {
                    // No parameter
                    sendReply(new FtpReply(501)); // Syntax error in parameters or arguments.
                }
                break;
            case "TYPE":
                if (splitCommand.length != 1) {
                    switch (splitCommand[1]) {
                        case "A":
                            if (splitCommand.length == 3) {
                                // Second parameter given
                                // Unknown FormCode
                                switch (splitCommand[2]) {
                                    case "N" -> TYPE(TypeCode.ASCII, FormCode.NON_PRINT);
                                    case "T" -> TYPE(TypeCode.ASCII, FormCode.TELNET_FORMAT_CONTROLS);
                                    case "C" -> TYPE(TypeCode.ASCII, FormCode.CARRIAGE_CONTROL_ASA);
                                    default -> sendReply(new FtpReply(501)); // Syntax error in parameters or arguments.
                                }
                            } else {
                                // RFC959 4.1.2 P. 29
                                // If the Format parameter is changed, and later just the first
                                // argument is changed, Format then returns to the Non-print
                                // default.
                                TYPE(TypeCode.ASCII, FormCode.NON_PRINT);
                            }
                            break;
                        case "E":
                            if (splitCommand.length == 3) {
                                // Second parameter given
                                // Unknown FormCode
                                switch (splitCommand[2]) {
                                    case "N" -> TYPE(TypeCode.EBCDIC, FormCode.NON_PRINT);
                                    case "T" -> TYPE(TypeCode.EBCDIC, FormCode.TELNET_FORMAT_CONTROLS);
                                    case "C" -> TYPE(TypeCode.EBCDIC, FormCode.CARRIAGE_CONTROL_ASA);
                                    default -> sendReply(new FtpReply(501)); // Syntax error in parameters or arguments.
                                }
                            } else {
                                // RFC959 4.1.2 P. 29
                                // If the Format parameter is changed, and later just the first
                                // argument is changed, Format then returns to the Non-print
                                // default.
                                TYPE(TypeCode.EBCDIC, FormCode.NON_PRINT);
                            }
                            break;
                        case "I":
                            TYPE(TypeCode.IMAGE);
                            break;
                        case "L":
                            if (splitCommand.length != 3) {
                                sendReply(new FtpReply(501));  // Syntax error in parameters or arguments.
                                return;
                            }
                            TYPE(TypeCode.LOCAL, Integer.parseInt(splitCommand[2]));
                            break;
                        default:
                            sendReply(new FtpReply(501)); // Syntax error in parameters or arguments.
                            return;
                    }
                } else {
                    // No parameter
                    sendReply(new FtpReply(501)); // Syntax error in parameters or arguments.
                }
                break;
            case "MODE":
                if (splitCommand.length != 1) {
                    switch (splitCommand[1]) {
                        case "S" -> MODE(TransmissionMode.STREAM);
                        case "B" -> MODE(TransmissionMode.BLOCK);
                        case "C" -> MODE(TransmissionMode.COMPRESSED);
                        default -> {
                            sendReply(new FtpReply(501)); // Syntax error in parameters or arguments.
                            return;
                        }
                    }
                } else {
                    // No parameter
                    sendReply(new FtpReply(501)); // Syntax error in parameters or arguments.
                    return;
                }
                break;
            case "STRU":
                if (splitCommand.length != 1) {
                    switch (splitCommand[1]) {
                        case "F" -> STRU(DataStructure.FILE);
                        case "R" -> STRU(DataStructure.RECORD);
                        case "P" -> STRU(DataStructure.PAGE);
                        default -> {
                            sendReply(new FtpReply(501)); // Syntax error in parameters or arguments.
                            return;
                        }
                    }
                } else {
                    // No parameter
                    sendReply(new FtpReply(501)); // Syntax error in parameters or arguments.
                }
                break;
            case "RETR":
                if (splitCommand.length == 1) {
                    // No parameter
                    sendReply(new FtpReply(501)); // Syntax error in parameters or arguments.
                } else if (splitCommand.length == 2) {
                    RETR(splitCommand[1]);
                } else {
                    // pathname has spaces
                    StringBuilder filename = new StringBuilder(splitCommand[1]);
                    for (byte i = 2; i < splitCommand.length; ++i) {
                        filename.append(" ").append(splitCommand[i]);
                    }
                    RETR(filename.toString());
                }
                break;
            case "STOR":
                if (splitCommand.length == 1) {
                    sendReply(new FtpReply(501)); // Syntax error in parameters or arguments.
                } else if (splitCommand.length == 2) {
                    STOR(splitCommand[1]);
                } else {
                    // pathname has spaces
                    StringBuilder filename = new StringBuilder(splitCommand[1]);
                    for (byte i = 2; i < splitCommand.length; ++i) {
                        filename.append(" ").append(splitCommand[i]);
                    }
                    STOR(filename.toString());
                }
                break;
            case "NOOP":
                NOOP();
                break;
            default:
                sendReply(new FtpReply(502)); // Command not implemented.
                break;
        }
    }

    /**
     * Send FTP response to client.
     *
     * @param reply The FTP response to send.
     */
    protected void sendReply(@NotNull final FtpReply reply) {
        if (reply.getCode() < 400) {
            System.out.println("SERVER: " + reply.getCode() + " " + reply.getMessage());
        } else {
            System.err.println("SERVER: " + reply.getCode() + " " + reply.getMessage());
        }

        bw.write(String.format("%d %s\r\n", reply.getCode(), reply.getMessage()));
        bw.flush();
    }

    protected void sendLine(@NotNull final String line) {
        System.out.println("SERVER: " + line);
        bw.println(line);
        bw.flush();
    }

    //region RFC959 FILE TRANSFER PROTOCOL (FTP)
    //region Login
    public void USER(String username) {
        if (username.isBlank()) {
            sendReply(new FtpReply(332)); // Need account for login.
        } else if (getAnonymousLogin() && username.equalsIgnoreCase("anonymous")) {
            setUsername(username);
            setAuthorised(true);
            sendReply(new FtpReply(230)); // User logged in, proceed.
        } else if (users.containsKey(username)) {
            setUsername(username);
            sendReply(new FtpReply(331)); // User name okay, need password.
        } else {
            sendReply(new FtpReply(530)); // Not logged in.
        }
    }
    public void PASS(@NotNull final String password) {
        if (getAnonymousLogin() && getUsername().equalsIgnoreCase("anonymous")) {
            setAuthorised(true);
            sendReply(new FtpReply(230)); // User logged in, proceed.
        } else if (users.get(getUsername()).equals(password)) {
            setAuthorised(true);
            sendReply(new FtpReply(230)); // User logged in, proceed.
        } else {
            sendReply(new FtpReply(530)); // Not logged in.
        }
    }
    public void ACCT(@NotNull final String accountInformation) {
        /*
          230 // User logged in, proceed.
          202 // Command not implemented, superfluous at this site.
          530 // Not logged in.
          500, 501, 503, 421
         */
        // We don't support multiple accounts and just use the USER and PASS credentials
        sendReply(new FtpReply(202)); // Command not implemented, superfluous at this site.
    }
    public void CWD(@NotNull final String pathname) {
        /*
            250
            500, 501, 502, 421, 530, 550
         */
        if (getAutorised()) {
            setCurrentDirectoryPath(pathname);
            sendReply(new FtpReply(250)); // Requested file action okay, completed.
        } else {
            sendReply(new FtpReply(530)); // Not logged in.
        }
    }

    /**
     * Change to the parent of the current working directory.
     */
    public void CDUP() {
        /*
        The CDUP command is a special case of CWD, and is included to
        simplify the implementation of programs for transferring directory
        trees between operating systems having different syntaxes for
        naming the parent directory.  The reply codes for CDUP be
        identical to the reply codes of CWD.
         */
        CWD("..");
    }
    public void SMNT() {}
    //endregion

    //region Logout
    public void REIN() {}
    public void QUIT() {
        /*
            221
            500
         */
        sendReply(new FtpReply(221));
        try {
            cmdConnection.close();
        } catch (IOException e) {
            System.err.println("IOException: " + e.getMessage());
        }
    }
    //endregion

    //region Transfer parameters
    public void PORT(final int h1, final int h2, final int h3, final int h4, final int p1, final int p2) {
        /*
        200
        500, 501, 421, 530
         */
        if (getAutorised()) {
            try {
                setClientAddress(InetAddress.getByName(String.format("%d.%d.%d.%d", h1, h2, h3, h4)));
                setClientPort((p1 * 256) + p2);
                setPassiveMode(false);
                sendReply(new FtpReply(200)); // Command okay.
            } catch (UnknownHostException ex) {
                System.err.println("UnknownHostException: " + ex.getMessage());
            }
        } else {
            sendReply(new FtpReply(530)); // Not logged in.
        }
    }
    public void PASV() {
        /*
            227
            500, 501, 502, 421, 530
         */
        if (getAutorised()) {
            try {
                // Close existing connection
                if (dataConnection != null) {
                    dataConnection.close();
                    dataConnection = null;
                }

                // Create new ServerSocket on random port
                dataConnection = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
                byte[] ip = dataConnection.getInetAddress().getAddress();
                var p1 = dataConnection.getLocalPort() / 256;
                var p2 = dataConnection.getLocalPort() % 256;
                setPassiveMode(true);
                // Format recommended by DJB
                // https://cr.yp.to/ftp/retr.html
                sendReply(new FtpReply(227, String.format("=%d,%d,%d,%d,%d,%d", ip[0], ip[1], ip[2], ip[3], p1, p2))); // Entering Passive Mode (h1,h2,h3,h4,p1,p2).
            } catch (IOException ex) {
                System.err.println("IOException: " + ex.getMessage());
            }
        } else {
            sendReply(new FtpReply(530)); // Not logged in.
        }
    }
    public void MODE(final TransmissionMode mode) {
    /*
        200
        500, 501, 504, 421, 530
     */
        if (getAutorised()) {
            transmissionMode = mode;
            sendReply(new FtpReply(200)); // Command okay.
        } else {
            sendReply(new FtpReply(530)); // Not logged in.
        }
    }
    public void TYPE(final TypeCode code) {
    /*
        200
        500, 501, 504, 421, 530
     */
        if (getAutorised()) {
            typeCode = code;
            sendReply(new FtpReply(200)); // Command okay.
        } else {
            sendReply(new FtpReply(530)); // Not logged in.
        }
    }
    public void TYPE(final TypeCode type, final FormCode form) {
        if (getAutorised()) {
            if (type == TypeCode.ASCII || type == TypeCode.EBCDIC) {
                typeCode = type;
                formCode = form;
                sendReply(new FtpReply(200)); // Command okay.
            } else {
                // Type doesn't support form-code
                // TODO Return error code
                sendReply(new FtpReply(501));
            }
        } else {
            sendReply(new FtpReply(530)); // Not logged in.
        }
    }
    public void TYPE(final TypeCode type, final int byteSize) {
        if (getAutorised()) {
            if (type == TypeCode.LOCAL) {
                typeCode = type;

                // TODO Implement
                sendReply(new FtpReply(200)); // Command okay.
            } else {
                // Type doesn't support byte-size
                // TODO Return error code
                sendReply(new FtpReply(501));
            }
        } else {
            sendReply(new FtpReply(530)); // Not logged in.
        }
    }
    public void STRU(final DataStructure structureCode) {
        /*
            200
            500, 501, 504, 421, 530
         */
        if (getAutorised()) {
            dataStructure = structureCode;
            sendReply(new FtpReply(200)); // Command okay.
        } else {
            sendReply(new FtpReply(530)); // Not logged in.
        }
    }
    //endregion

    //region File action commands
    public void ALLO() {}
    public void REST(@NotNull final long marker) {
        /*
        500, 501, 502, 421, 530
        350
         */
        if (getAutorised()) {
            try {
                setStartPosition(marker);
                sendReply(new FtpReply(350)); // Requested file action pending further information.
            } catch (NumberFormatException ex) {
                sendReply(new FtpReply(501)); // Syntax error in parameters or arguments.
            }
        } else {
            sendReply(new FtpReply(530)); // Not logged in.
        }
    }
    public void STOR(@NotNull final String pathname) {
        /*
            125, 150
               (110)
               226, 250
               425, 426, 451, 551, 552
            532, 450, 452, 553
            500, 501, 421, 530
         */

        if (getAutorised()) {
            var f = new File(pathname);
            if (getPassiveMode()) {
                STOR_PASSIVE(f);
            } else {
                STOR_ACTIVE(f);
            }
        } else {
            sendReply(new FtpReply(530)); // Not logged in.
        }
    }

    /**
     * STOR via active FTP
     *
     * @param f File to store the data in
     */
    protected void STOR_ACTIVE(@NotNull File f) {
        sendReply(new FtpReply(150)); // File status okay; about to open data connection.
        try (var s = new Socket(getClientAddress(), getClientPort(), new InetSocketAddress(0).getAddress(), 20);
             var os = s.getInputStream();
             var dis = new DataInputStream(new BufferedInputStream(os));
             var fos = new FileOutputStream(f)) {

            byte[] buffer = new byte[4096];
            int count;
            while ((count = dis.read(buffer, 0, buffer.length)) != -1) {
                fos.write(buffer, 0, count);
            }
            sendReply(new FtpReply(226)); // Closing data connection.
        } catch (IOException ex) {
            System.err.println("IOException: " + ex.getMessage());
            // TODO Is this the correct error?
            sendReply(new FtpReply(451)); // Requested action aborted: local error in processing.
        }
    }

    /**
     * STOR via passive FTP
     *
     * @param f File to store the data in
     */
    protected void STOR_PASSIVE(@NotNull File f) {
        sendReply(new FtpReply(150)); // File status okay; about to open data connection.

        try (var s = dataConnection.accept();
             var is = s.getInputStream();
             var dis = new DataInputStream(new BufferedInputStream(is));
             var fos = new FileOutputStream(f)) {

            byte[] buffer = new byte[4096];
            int count;
            while ((count = dis.read(buffer, 0, buffer.length)) != -1) {
                fos.write(buffer, 0, count);
            }
            sendReply(new FtpReply(226)); // Closing data connection.
        } catch (IOException ex) {
            System.err.println("IOException: " + ex.getMessage());
            // TODO Is this the correct error?
            sendReply(new FtpReply(451)); // Requested action aborted: local error in processing.
        }
    }

    public void STOU() {}
    public void RETR(final String pathname) {
        /*
        // 110 Restart marker reply.
        // 125 Data connection already open; transfer starting.
        // 150 File status okay; about to open data connection.
        // 226 Closing data connection.
        // 250 Requested file action okay, completed.
        // 421 Service not available, closing control connection.
        // 425 Can't open data connection.
        // 426 Connection closed; transfer aborted.
        // 450 Requested file action not taken.
        // 451 Requested action aborted: local error in processing.
        // 500 Syntax error, command unrecognized.
        // 501 Syntax error in parameters or arguments.
        // 530 Not logged in.
        // 550 Requested action not taken.

            125, 150
              (110)
              226, 250
              425, 426, 451
            450, 550
            500, 501, 421, 530
         */
        if (getAutorised()) {
            var f = new File(pathname);
            if (getPassiveMode()) {
                RETR_PASSIVE(f);
            } else {
                RETR_ACTIVE(f);
            }
        } else {
            sendReply(new FtpReply(530)); // Not logged in.
        }
    }

    protected void RETR_ACTIVE(@NotNull File f) {
        sendReply(new FtpReply(150)); // File status okay; about to open data connection.
        try (var s = new Socket(getClientAddress(), getClientPort(), new InetSocketAddress(0).getAddress(), 20);
             var os = s.getOutputStream();
             var bos = new BufferedOutputStream(os);
             var fis = new FileInputStream(f)) {

            bos.write(fis.readAllBytes());

            sendReply(new FtpReply(226)); // Closing data connection.
        } catch (IOException ex) {
            System.err.println("IOException: " + ex.getMessage());
        }
    }

    protected void RETR_PASSIVE(@NotNull File f) {
        sendReply(new FtpReply(150)); // File status okay; about to open data connection.
        try (var s = dataConnection.accept();
             var os = s.getOutputStream();
             var dos = new DataOutputStream(new BufferedOutputStream(os));
             var fis = new FileInputStream(f)) {

            dos.write(fis.readAllBytes());
            sendReply(new FtpReply(226)); // Closing data connection.
        } catch (IOException ex) {
            System.err.println("IOException: " + ex.getMessage());
        }
    }

    public void LIST() {
        /*
            125, 150 // 125 Data connection already open; transfer starting./150 File status okay; about to open data connection.
              226, 250 // 226 Closing data connection./250 Requested file action okay, completed.
              425, 426, 451
            450 // Requested file action not taken.
            500, 501, 502, 421, 530
         */

        if (getAutorised()) {
            if (getPassiveMode()) {
                sendReply(new FtpReply(150));
                try (var s = dataConnection.accept();
                     var os = s.getOutputStream();
                     var ps = new PrintWriter(new BufferedOutputStream(os))) {

                    for (var f : getCurrentDirectoryFiles()) {
                        ps.write(new EasyParsableListFormat(f).toString());
                    }

                    sendReply(new FtpReply(226)); // Closing data connection.
                } catch (IOException ex) {
                    System.err.println("IOException: " + ex.getMessage());
                }
            } else {
                // Active mode
                // Always open a new data connection
                sendReply(new FtpReply(150)); // File status okay; about to open data connection.
                try (var s = new Socket(getClientAddress(), getClientPort(), new InetSocketAddress(0).getAddress(), 20);
                     var os = s.getOutputStream();
                     var ps = new PrintWriter(new BufferedOutputStream(os))) {

                    for (var f : getCurrentDirectoryFiles()) {
                        ps.write(new EasyParsableListFormat(f).toString());
                    }

                    sendReply(new FtpReply(226)); // Closing data connection.
                } catch (IOException ex) {
                    System.err.println("IOException: " + ex.getMessage());
                }
            }
        } else {
            sendReply(new FtpReply(530)); // Not logged in.
        }
    }
    public void LIST(@NotNull final String pathname) {
        if (getAutorised()) {
            if (getStrictMode() && pathname.equals("-a")) {
                // Non-compliant behaviour by some clients (like WinSCP) (to get . and .. directories)
                sendReply(new FtpReply(501)); // Syntax error in parameters or arguments.
                return;
            }

            var dir = getCurrentDirectoryPath();
            if (!pathname.equals("-a")) {
                // Don't change directory if illegal parameter is present
                // Just list current directory instead
                setCurrentDirectoryPath(pathname);
            }
            LIST();
            setCurrentDirectoryPath(dir);
        } else {
            sendReply(new FtpReply(530)); // Not logged in.
        }
    }
    public void NLST() {
        /*
            125, 150
               226, 250
               425, 426, 451
            450
            500, 501, 502, 421, 530
         */
        if (getAutorised()) {
            // Always open a new data connection
            sendReply(new FtpReply(150)); // File status okay; about to open data connection.
            try (var s = new Socket(getClientAddress(), getClientPort(), new InetSocketAddress(0).getAddress(), 20);
                 var os = s.getOutputStream();
                 var ps = new PrintWriter(new BufferedOutputStream(os))) {

                for (var f : getCurrentDirectoryFiles()) {
                    ps.write(new EasyParsableListFormat(f).getName() + "\r\n");
                }

                sendReply(new FtpReply(226)); // Closing data connection.
            } catch (IOException ex) {
                System.err.println("IOException: " + ex.getMessage());
            }
        } else {
            sendReply(new FtpReply(530)); // Not logged in.
        }
    }
    public void NLST(@NotNull final String pathname) {}
    public void APPE() {}
    public void RNFR() {}
    public void RNTO() {}
    public void DELE(@NotNull final String pathname) {
        /*
            250
            450, 550
            500, 501, 502, 421, 530
         */

        if (getAutorised()) {
            var f = new File(pathname);
            if (f.exists()) {
                if (f.delete()) {
                    sendReply(new FtpReply(250)); // Requested file action okay, completed.
                } else {
                    sendReply(new FtpReply(450)); // Requested file action not taken.
                }
            } else {
                sendReply(new FtpReply(550)); // Requested action not taken.
            }
        } else {
            sendReply(new FtpReply(530)); // Not logged in.
        }
    }

    /**
     * Remove the directory with the name "pathname".
     * @param pathname Name of the directory
     */
    public void RMD(@NotNull final String pathname) {
        /*
            250
            500, 501, 502, 421, 530, 550
         */

        if (getAutorised()) {
            var f = new File(pathname);
            if (f.exists() && f.isDirectory() && f.delete()) {
                sendReply(new FtpReply(250)); // Requested file action okay, completed.
            } else {
                sendReply(new FtpReply(550)); // Requested action not taken.
            }
        } else {
            sendReply(new FtpReply(530)); // Not logged in.
        }
    }

    /**
     * Make a directory with the name "pathname".
     * @param pathname Name of the directory
     */
    public void MKD(@NotNull final String pathname) {
        /*
            257
            500, 501, 502, 421, 530, 550
         */
        if (getAutorised()) {
            var f = new File(getCurrentDirectoryPath(), pathname);
            if (!f.exists() && f.mkdir()) {
                sendReply(new FtpReply(257, "\"" + pathname + "\" created.")); // 257 "PATHNAME" created.
            } else {
                sendReply(new FtpReply(550)); // Requested action not taken.
            }
        } else {
            sendReply(new FtpReply(530)); // Not logged in.
        }
    }

    /**
     * Print the current working directory name.
     */
    public void PWD() {
        /*
            257
            500, 501, 502, 421, 550
         */
        sendReply(new FtpReply(257, "\"" + getCurrentDirectoryPath() + "\" created")); // "PATHNAME" created.
    }
    public void ABOR() {}
    //endregion

    //region Informational commands
    public void SYST() {
        /*
            215
            500, 501, 502, 421
         */
        final String systemType = "UNKNOWN";
        sendReply(new FtpReply(215, systemType + " system type.")); // NAME system type.
    }
    public void STAT() {}
    public void HELP() {}
    public void HELP(@NotNull final String param) {}
    //endregion

    //region Miscellaneous commands
    public void SITE(@NotNull final String param) {
        /*
            200 // Command okay.
            202 // Command not implemented, superfluous at this site.
            500, 501, 530
         */
        if (getAutorised()) {
            sendReply(new FtpReply(202)); // Command not implemented, superfluous at this site.
        } else {
            sendReply(new FtpReply(530)); // Not logged in.
        }
    }
    public void NOOP() {
        /*
            200
            500 421
         */
        sendReply(new FtpReply(200)); // Command okay.
    }
    //endregion
    //endregion

    //region RFC1123 Requirements for Internet Hosts -- Application and Support
    // See section 4.1 for updates to the FTP-Protocol
    //endregion

    //region RFC2389 Feature negotiation mechanism for the File Transfer Protocol (Proposed)
    public void FEAT() {
        /*
           There definitions will be found for basic ABNF elements like ALPHA,
           DIGIT, VCHAR, SP, etc.  To that, the following terms are added for
           use in this document.

                TCHAR          = VCHAR / SP / HTAB    ; visible plus white space

           The TCHAR type, and VCHAR from [3], give basic character types from
           varying sub-sets of the ASCII character set for use in various
           commands and responses.

                error-response = error-code SP *TCHAR CRLF
                error-code     = ("4" / "5") 2DIGIT

           Note that in ABNF, strings literals are case insensitive.  That
           convention is preserved in this document.  However note that ALPHA,
           in particular, is case sensitive, as are VCHAR and TCHAR.


        feat-response   = error-response / no-features / feature-listing
        no-features     = "211" SP *TCHAR CRLF
        feature-listing = "211-" *TCHAR CRLF
                          1*( SP feature CRLF )
                          "211 End" CRLF
        feature         = feature-label [ SP feature-parms ]
        feature-label   = 1*VCHAR
        feature-parms   = 1*TCHAR
         */

        // For FEAT codes see https://www.iana.org/assignments/ftp-commands-extensions/ftp-commands-extensions.xhtml

        // TODO List all features
        //sendLine("211-Features:");
        //sendLine(" base");
        //sendLine(" hist");
        //sendLine(" SIZE");

        // End of feature set or "No features"
        //sendReply(new FtpReply(211)); // System status, or system help reply.
        sendReply(new FtpReply(502)); // Command not implemented.);
    }
    public void OPTS(@NotNull final String commandName) {
        sendReply(new FtpReply(502)); // Command not implemented.
    }

    public void OPTS(@NotNull final String commandName, @NotNull final String commandOptions) {
        sendReply(new FtpReply(502)); // Command not implemented.
    }
    //endregion

    //region RFC2228 FTP Security Extensions
    // Authentication/Security Mechanism
    public void AUTH() {}
    // Authentication/Security Data
    public void ADAT() {}
    // Data Channel Protection Level
    public void PROT() {}
    // Protection Buffer Size
    public void PBSZ() {}
    // Clear Command Channel
    public void CCC() {}
    // Integrity Protected Command
    public void MIC() {}
    // Confidentiality Protected Command
    public void CONF() {}
    // Privacy Protected Command
    public void ENC() {}
    //endregion

    //region RFC2428 FTP Extensions for IPv6 and NATs (Proposed)
    public void EPRT() {}
    public void EPSV() {}
    public void EPSV(String net_prt) {}
    //endregion

    //region RFC2640 Internationalization of the File Transfer Protocol (Proposed)
    //endregion

    //region RFC2773 Encryption using KEA and SKIPJACK (Experimental)
    //endregion

    //region RFC3659 Extensions to FTP (Proposed)
    //endregion

    //region RFC5797 FTP Command and Extension Registry (Proposed)
    //endregion

    //region RFC7151 File Transfer Protocol HOST Command for Virtual Hosts (Proposed)
    public void HOST(@NotNull final String hostname) {}
    //endregion

    @Override
    public void run() {
        /*
        Connection establishment:
               120 // Service ready in nnn minutes.
                  220 // Service ready for new user.
               220 // Service ready for new user.
               421 // Service not available, closing control connection.
         */
        sendReply(new FtpReply(220)); // Service ready for new user.
        try {
            while (!cmdConnection.isClosed()) {
                parseCommand(br.readLine());
            }

            System.out.println("Command connection closed");
        } catch (IOException ex) {
            System.err.println("IOException: " + ex.getMessage());
        }
    }
}