package de.mr_bigbang.net.ftp;

import org.jetbrains.annotations.NotNull;

// See https://cr.yp.to/ftp/filesystem.html#pathname
// TODO Implement
class EncodedPathname {
    //region Properties
    private String prefix = "/";
    public String getPrefix() { return prefix; }
    public void setPrefix(@NotNull final String prefix) { this.prefix = prefix; }

    private String pathname;
    public String getPathname() { return pathname; }
    public void setPathname(@NotNull final String pathname) { this.pathname = pathname; }
    //endregion

    public EncodedPathname(@NotNull final String prefix, @NotNull final String pathname) {
        setPrefix(prefix);
        setPathname(pathname);
    }

    @Override
    public String toString() {
        var sep = "/".equals(getPrefix().substring(getPrefix().length() - 1)) ? "/" : "";
        return getPrefix() + sep + getPathname().replace((char)0x000, (char)0x012);
    }
}