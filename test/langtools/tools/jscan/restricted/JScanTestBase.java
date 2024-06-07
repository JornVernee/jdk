/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;

import java.io.StringWriter;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

import jdk.test.lib.process.OutputAnalyzer;

public class JScanTestBase {

    private static final ToolProvider JAR_TOOL = ToolProvider.findFirst("jar")
            .orElseThrow(() -> new RuntimeException("jar tool not found"));
    private static final ToolProvider JSCAN_TOOL = ToolProvider.findFirst("jscan")
            .orElseThrow(() -> new RuntimeException("jscan tool not found"));

    public static OutputAnalyzer jar(String... args) {
        return run(JAR_TOOL, args);
    }

    public static OutputAnalyzer jscanRestricted(String... args) {
        String[] newArgs = new String[args.length + 1];
        newArgs[0] = "restricted";
        System.arraycopy(args, 0, newArgs, 1, args.length);
        return run(JSCAN_TOOL, newArgs);
    }

    private static OutputAnalyzer run(ToolProvider tp, String[] commands) {
        int rc;
        StringWriter sw = new StringWriter();
        StringWriter esw = new StringWriter();

        try (PrintWriter pw = new PrintWriter(sw);
             PrintWriter epw = new PrintWriter(esw)) {
            System.out.println("Running " + tp.name() + ", Command: " + Arrays.toString(commands));
            rc = tp.run(pw, epw, commands);
        }
        OutputAnalyzer output = new OutputAnalyzer(sw.toString(), esw.toString(), rc);
        output.outputTo(System.out);
        output.errorTo(System.err);
        return output;
    }

    public static Path makeModularJar(String moduleName) throws IOException {
        Path jarPath = Path.of(moduleName + ".jar");
        Path moduleRoot = moduleRoot(moduleName);
        List<String> command = new ArrayList<>();
        command.add("--create");
        command.add("--file");
        command.add(jarPath.toString());
        try (Stream<Path> files = Files.walk(moduleRoot)) {
            files.filter(Files::isRegularFile)
                .forEach(p -> {
                    command.add("-C");
                    command.add(moduleRoot.toString());
                    command.add(moduleRoot.relativize(p).toString());
                });
        }
        assertSuccess(jar(command.toArray(String[]::new)));
        return jarPath;
    }

    public static Path moduleRoot(String name) {
        return Path.of(System.getProperty("test.module.path")).resolve(name);
    }

    public static OutputAnalyzer assertSuccess(OutputAnalyzer output) {
        if (output.getExitValue() != 0) {
            throw new IllegalStateException("tool run failed");
        }
        return output;
    }

    public static OutputAnalyzer assertFailure(OutputAnalyzer output) {
        if (output.getExitValue() == 0) {
            throw new IllegalStateException("tool run succeeded");
        }
        return output;
    }
}
