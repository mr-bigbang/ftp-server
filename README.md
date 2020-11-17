# FTP Server
Small standalone, multithreaded FTP-Server written in Java.

The implementation primarily follows RFC959 with hints from https://cr.yp.to/ftp.html

This project was created to learn about Java, Sockets and Internet protocols. It is therefore NOT production-grade code and should NOT be used anywhere near productive systems.

This server should be compatible with FileZilla and WinSCP clients.

# Features
- Support for active and passive FTP
- File listing in EPLF format
- Anonymous login
