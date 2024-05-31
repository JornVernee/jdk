package com.sun.tools.jscan;

import java.io.OutputStream;
import java.io.PrintWriter;

class Log {

    private final PrintWriter out;
    private final PrintWriter err;

    public Log(PrintWriter out, PrintWriter err) {
        this.out = out;
        this.err = err;
    }

    public void error(String message) {
        err.println("ERROR: " + message);
    }

    public void println(String message) {
        out.println(message);
    }

    public PrintWriter out() {
        return out;
    }
}
