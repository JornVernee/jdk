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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.spi.ToolProvider;

public class Main {

    private static boolean DEBUG = Boolean.getBoolean("com.sun.tools.jscan.DEBUG");

    private static final int SUCCESS_CODE = 0;
    private static final int FATAL_ERROR_CODE = 1;

    private final PrintWriter out;
    private final PrintWriter err;

    private Main(PrintWriter out, PrintWriter err) {
        this.out = out;
        this.err = err;
    }

    private void printHelp()  {
        out.print("""
            USAGE: jscan <restricted|dependencies|deprecations> [options...]
            Use jscan <action> --help for more info about an action
            """);
    }

    private void printVersion() {
        out.print("jscan " + Runtime.version());
    }

    public int run(String[] args) {
        Log log = new Log(out, err);
        if (args.length < 1) {
            log.error("No action specified");
            printHelp();
            return FATAL_ERROR_CODE;
        }

        String action = args[0];
        String[] remainingArgs = new String[args.length - 1];
        System.arraycopy(args, 1, remainingArgs, 0, remainingArgs.length);
        try {
            String[] expandedArgs = expandArgFiles(remainingArgs);
            switch (action) {
                case "restricted" -> JScanRestricted.run(log, expandedArgs);
                case "dependencies" -> com.sun.tools.jdeps.Main.run(expandedArgs, out);
                case "deprecations" -> com.sun.tools.jdeprscan.Main.call(out, err, expandedArgs);
                case "-h", "-?", "-help", "--help" -> printHelp();
                case "--version" -> printVersion();
                default -> {
                    log.error("Unknown action: " + action);
                    return FATAL_ERROR_CODE;
                }
            }
        } catch (JScanFatalError fatalError) {
            log.error(fatalError.getMessage());
            if (DEBUG) {
                fatalError.printStackTrace(log.err());
            }
            return FATAL_ERROR_CODE;
        } catch (Throwable e) {
            log.error("Unexpected exception encountered");
            e.printStackTrace(log.err());
            return FATAL_ERROR_CODE;
        }

        return SUCCESS_CODE;
    }

    private static String[] expandArgFiles(String[] args) throws JScanFatalError {
        try {
            return CommandLine.parse(args);
        } catch (IOException e) { // file not found
            throw new JScanFatalError(e.getMessage(), e);
        }
    }

    public static void main(String[] args) {
        System.exit(new Main.Provider().run(System.out, System.err, args));
    }

    public static class Provider implements ToolProvider {

        @Override
        public String name() {
            return "jscan";
        }

        @Override
        public int run(PrintWriter out, PrintWriter err, String... args) {
            return new Main(out, err).run(args);
        }
    }
}
