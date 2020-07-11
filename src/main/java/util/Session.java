package util;

import java.io.File;

public class Session {

    private File apk;
    private File in;
    private File out;

    public Session(File apk, File in, File out) {
        this.apk = apk;
        this.in = in;
        this.out = out;
    }

    public File getApk() {
        return apk;
    }

    public File getIn() {
        return in;
    }

    public File getOut() {
        return out;
    }
}
