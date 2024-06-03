/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
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
