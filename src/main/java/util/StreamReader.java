package util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static util.LoggingUtils.getCurrentLogger;
import static util.LoggingUtils.handleError;

public class StreamReader implements Runnable {


    private BufferedReader br;
    private boolean stacktrace;
    private StringBuilder sb;
    private boolean running = false;

    public StreamReader(InputStream stream, boolean stacktrace) {
        br = new BufferedReader(new InputStreamReader(stream));
        this.stacktrace = stacktrace;
    }

    @Override
    public void run() {
        running = true;
        sb = new StringBuilder();
        String line;
        try {
            line = br.readLine();

            while(line != null) {
                sb.append(line);
                sb.append(System.lineSeparator());

                line = br.readLine();
            }
        } catch(IOException e) {
            handleError(e, "Could not read line from InputStream '" + br + "'",
                    stacktrace);
        }
        finally {
            running = false;
            Thread currentThread = Thread.currentThread();
            getCurrentLogger().debug("thread '" + currentThread.getName() + "' (ID: " + currentThread.getId() + ") is exiting");
        }
    }

    public String getOutput() throws IllegalThreadStateException {
        if(running) throw new IllegalThreadStateException();
        return sb.toString();
    }

    public String getOutputForced() {
        return sb.toString();
    }

    public void close() throws IOException {
        br.close();
    }
}
