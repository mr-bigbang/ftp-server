package de.mr_bigbang.net.ftp;

class FtpReply {
    private final String SYSTEM_TYPE = "UNKNOWN";

    //region Properties
    private int code;
    public int getCode() {
        return code;
    }
    protected void setCode(int code) {
        if (code <= 0) {
            throw new UnsupportedOperationException("Code must be positive and not 0");
        }

        this.code = code;
    }

    private String message;
    public String getMessage() {
        return this.message;
    }

    public void setMessage(String message) {
        if (message == null || message.trim().equals("")) {
            throw new IllegalArgumentException("Message must not be NULL");
        }

        this.message = message;
    }
    //endregion

    public FtpReply(int code, String message) {
       this.setCode(code);
       this.setMessage(message);
    }

    public FtpReply(int code) {
        this.setCode(code);
        // Set default messages
        switch (code) {
            case 125:
                this.setMessage("Data connection already open; transfer starting.");
                break;
            case 150:
                this.setMessage("File status okay; about to open data connection.");
                break;
            case 200:
                this.setMessage("Command okay.");
                break;
            case 211:
                this.setMessage("System status, or system help reply.");
                break;
            case 215:
                this.setMessage(String.format("%s system type.", this.SYSTEM_TYPE));
                break;
            case 220:
                this.setMessage("Service ready for new user.");
                break;
            case 221:
                this.setMessage("Service closing control connection.");
                break;
            case 226:
                this.setMessage("Closing data connection.");
                break;
            case 230:
                this.setMessage("User logged in, proceed.");
                break;
            case 250:
                this.setMessage("Requested file action okay, completed.");
                break;
            case 257:
                this.setMessage("\"PATHNAME\" created.");
                break;
            case 331:
                this.setMessage("User name okay, need password.");
                break;
            case 332:
                this.setMessage("Need account for login.");
                break;
            case 350:
                this.setMessage("Requested file action pending further information.");
                break;
            case 450:
                this.setMessage("Requested file action not taken.");
                break;
            case 501:
                this.setMessage("Syntax error in parameters or arguments.");
                break;
            case 502:
                this.setMessage("Command not implemented.");
                break;
            case 503:
                this.setMessage("Bad sequence of commands.");
                break;
            case 530:
                this.setMessage("Not logged in.");
                break;
            case 550:
                this.setMessage("Requested action not taken.");
                break;
            default:
                this.setMessage("--- --- NOT IMPLEMENTED --- ---");
                break;
        }
    }
}
