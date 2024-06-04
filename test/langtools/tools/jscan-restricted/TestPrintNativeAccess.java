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

/*
 * @test
 * @library /test/lib ./cases/modules
 * @build JScanTestBase org.singlejar/* org.lib/* org.myapp/* cases.classpath.singlejar.main.Main
 * @run testng TestPrintNativeAccess
 */

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Path;

class TestPrintNativeAccess extends JScanTestBase {

    Path singleJarClassPath;
    Path singleJarModular;
    Path orgMyapp;

    @BeforeClass
    public void before() throws IOException {
        singleJarClassPath = Path.of("singleJar.jar");
        Path classes = Path.of(System.getProperty("test.classes", ""));
        assertSuccess(jar("--create", "--file", singleJarClassPath.toString(),
                "-C", classes.toString(), "main/Main.class"));

        singleJarModular = makeModularJar("org.singlejar");
        orgMyapp = makeModularJar("org.myapp");
        makeModularJar("org.lib");
    }

    @Test
    public void testSingleJarClassPath() {
        assertSuccess(jscanRestricted("--class-path", singleJarClassPath.toString(), "--dump-all"))
                .stderrShouldBeEmpty()
                .stdoutShouldContain("ALL-UNNAMED")
                .stdoutShouldContain("main.Main")
                .stdoutShouldContain("main.Main::m()void is a native method declaration")
                .stdoutShouldContain("main.Main::main(String[])void references restricted methods")
                .stdoutShouldContain("java.lang.foreign.MemorySegment::reinterpret(long)MemorySegment");
    }

    @Test
    public void testSingleJarModulePath() {
        assertSuccess(jscanRestricted("--module-path", ".", "--dump-all", "--add-modules", "org.singlejar"))
                .stderrShouldBeEmpty()
                .stdoutShouldContain("org.singlejar")
                .stdoutShouldContain("org.singlejar.main.Main")
                .stdoutShouldContain("org.singlejar.main.Main::m()void is a native method declaration")
                .stdoutShouldContain("org.singlejar.main.Main::main(String[])void references restricted methods")
                .stdoutShouldContain("java.lang.foreign.MemorySegment::reinterpret(long)MemorySegment");
    }

    @Test
    public void testWithDepModule() {
        assertSuccess(jscanRestricted("--module-path", ".", "--dump-all", "--add-modules", "org.myapp"))
                .stderrShouldBeEmpty()
                .stdoutShouldContain("org.lib")
                .stdoutShouldContain("org.lib.Lib")
                .stdoutShouldContain("org.lib.Lib::m()void is a native method declaration")
                .stdoutShouldContain("org.lib.Lib::doIt()void references restricted methods")
                .stdoutShouldContain("java.lang.foreign.MemorySegment::reinterpret(long)MemorySegment");
    }
}
