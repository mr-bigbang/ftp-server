package de.mr_bigbang.net.tftp;

enum ErrorCode {
    /*
    Value     Meaning
    0         Not defined, see error message (if any).
    1         File not found.
    2         Access violation.
    3         Disk full or allocation exceeded.
    4         Illegal TFTP operation.
    5         Unknown transfer ID.
    6         File already exists.
    7         No such user.
    */
    NOT_DEFINED((byte) 0x00),
    FILE_NOT_FOUND((byte) 0x01),
    ACCESS_VIOLATION((byte) 0x02),
    DISK_FULL((byte) 0x03),
    ILLEGAL_TFTP_OPERATION((byte) 0x04),
    UNKNOWN_TRANSFER_ID((byte) 0x05),
    FILE_ALREADY_EXISTS((byte) 0x06),
    NO_SUCH_USER((byte) 0x07);

    private byte code;

    public byte getCode() {
        return code;
    }

    private void setCode(byte code) {
        if (code < 0 || code > 7) {
            throw new IllegalArgumentException();
        }
        this.code = code;
    }

    ErrorCode(byte code) {
        setCode(code);
    }
}
