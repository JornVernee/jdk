package com.sun.tools.jscan;

import jdk.internal.opt.CommandLine;

import java.io.PrintWriter;
import java.util.spi.ToolProvider;

public class Main {
    private final PrintWriter out;
    private final PrintWriter err;

    private Main(PrintWriter out, PrintWriter err) {
        this.out = out;
        this.err = err;
    }

    private void printHelp()  {
        out.print("""
            Placeholder
            """);
    }

    private void printError(String message) {
        printError(message, null);
    }

    private void printError(String message, Throwable t) {
        err.println("ERROR: " + message);
        if (t != null) {
            t.printStackTrace(err);
        }
    }

    public int run(String[] args) {
        if (args.length < 1) {
            printError("No action specified");
            printHelp();
            return 1;
        }

        Log log = new Log(out, err);

        String action = args[0];
        String[] remainingArgs = new String[args.length - 1];
        System.arraycopy(args, 1, remainingArgs, 0, remainingArgs.length);
        try {
            String[] expandedArgs = CommandLine.parse(remainingArgs);
            switch (action) {
                case "restricted" -> JScanRestricted.run(log, expandedArgs);
                default -> {
                    printError("Unknown action: " + action);
                    // TODO implement e.g. --version
                    return 1;
                }
            };
            return 0;
        } catch (Throwable t) {
            log.error(t.getMessage());
            t.printStackTrace(err);
            return 1;
        }
    }

    public static void main(String[] args) {
        System.exit(new Main.Provider().run(System.out, System.err, args));
    }

    public static class Provider implements ToolProvider {

        @Override
        public String name() {
            return "jget";
        }

        @Override
        public int run(PrintWriter out, PrintWriter err, String... args) {
            return new Main(out, err).run(args);
        }
    }
}
