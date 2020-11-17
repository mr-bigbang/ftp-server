package de.mr_bigbang.net.ftp;

class FtpCommand {
    private String cmd;
    protected String getCmd() { return this.cmd; }
    protected void setCmd(String cmd) { this.cmd = cmd; }

    private String param;
    protected String getParam() { return this.param; }
    protected void setParam(String param) { this.param = param; }

    public FtpCommand(String cmd) {
        setCmd(cmd);
    }

    public FtpCommand(String cmd, String param) {
        this(cmd);
        setParam(param);
    }
}
