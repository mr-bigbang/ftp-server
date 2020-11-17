package de.mr_bigbang.net.ftp;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;

/**
 * An EPLF response to LIST is a series of lines, each line specifying one file. Each line contains
 * - a plus sign (\053);
 * - a series of facts about the file;
 * - a tab (\011);
 * - an abbreviated pathname; and
 * - \015\012.
 *
 * @Source https://cr.yp.to/ftp/list/eplf.html
 */
/*
+i8388621.48594,m825718503,r,s280,\tdjb.html\r\n
+i8388621.50690,m824255907,/,\r514\r\n
+i8388621.48598,m824253270,r,s612,\t514.html\r\n
*/
class EasyParsableListFormat {
    private final ArrayList<String> facts = new ArrayList<>();

    private String name;
    public String getName() {
        return this.name;
    }
    public EasyParsableListFormat setName(String name) {
        this.name = name;
        return this;
    }

    public EasyParsableListFormat addRetrFact() {
        facts.add("r");
        return this;
    }

    // If this is set, the item is a folder
    public EasyParsableListFormat addCwdFact() {
        facts.add("/");
        return this;
    }

    public EasyParsableListFormat addSizeFact(long size) {
        facts.add("s" + size);
        return this;
    }

    public EasyParsableListFormat addModifiedFact(Date modified) {
        facts.add("m" + modified.getTime() / 1000);
        return this;
    }

    public EasyParsableListFormat addModifiedFact(long milliseconds) {
        facts.add("m" + milliseconds / 1000);
        return this;
    }

    public EasyParsableListFormat addIdentifierFact() {
        // TODO How to get unique ID?
        facts.add("i" + "123456789");
        return this;
    }

    /**
     * This feature is no supported.
     *
     * @deprecated
     */
    public EasyParsableListFormat addUpFact(File f)
    throws UnsupportedOperationException {
        // This feature (as well as SITE CHMOD) is no implemented
        // Also not supported on Windows
        // var perm = Files.getPosixFilePermissions(f.toPath());

        // for (var p : perm) {
        //    System.err.println(p.ordinal());
        // }

        throw new UnsupportedOperationException("Feature not supported");

        //facts.add("up" + "000");
        //return this;
    }

    public EasyParsableListFormat() { }

    public EasyParsableListFormat(@NotNull File f) {
        this.setName(f.getName());
        this.addModifiedFact(f.lastModified());

        if (f.isFile()) {
            this.addRetrFact();
            this.addSizeFact(f.length());
        } else if (f.isDirectory()) {
            this.addCwdFact();
        }
    }

    @Override
    public String toString() {
        return String.format("+%s,\t%s\r\n", String.join(",", facts), getName());
    }
}
