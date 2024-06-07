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
 * @build JScanTestBase
 *     org.singlejar/* org.lib/* org.myapp/*
 *     cases.classpath.singlejar.main.Main
 *     cases.classpath.lib.Lib
 *     cases.classpath.app.App
 * @run testng TestJScanRestricted
 */

import jdk.test.lib.util.JarUtils;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

class TestJScanRestricted extends JScanTestBase {

    Path classPathApp;
    Path singleJarClassPath;
    Path singleJarModular;
    Path orgMyapp;

    @BeforeClass
    public void before() throws IOException {
        singleJarClassPath = Path.of("singleJar.jar");
        Path classes = Path.of(System.getProperty("test.classes", ""));
        JarUtils.createJarFile(singleJarClassPath, classes, Path.of("main", "Main.class"));

        JarUtils.createJarFile(Path.of("lib.jar"), classes, Path.of("lib", "Lib.class"));
        Manifest manifest = new Manifest();
        Attributes mainAttrs = manifest.getMainAttributes();
        mainAttrs.put(Attributes.Name.MANIFEST_VERSION, "1.0"); // need version or other attributes will be ignored
        mainAttrs.putValue("Class-Path", "lib.jar non-existent.jar");
        classPathApp = Path.of("app.jar");
        JarUtils.createJarFile(classPathApp, manifest, classes, Path.of("app", "App.class"));

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

    @Test
    public void testAllModulePath() {
        assertSuccess(jscanRestricted("--module-path", ".", "--dump-all", "--add-modules", "ALL-MODULE-PATH"))
                .stderrShouldBeEmpty()
                .stdoutShouldContain("org.lib")
                .stdoutShouldContain("org.lib.Lib")
                .stdoutShouldContain("org.lib.Lib::m()void is a native method declaration")
                .stdoutShouldContain("org.lib.Lib::doIt()void references restricted methods")
                .stdoutShouldContain("java.lang.foreign.MemorySegment::reinterpret(long)MemorySegment");
    }

    @Test
    public void testClassPathAttribute() {
        assertSuccess(jscanRestricted("--class-path", classPathApp.toString(), "--dump-all"))
                .stderrShouldBeEmpty()
                .stdoutShouldContain("ALL-UNNAMED")
                .stdoutShouldContain("lib.Lib")
                .stdoutShouldContain("lib.Lib::m()void is a native method declaration")
                .stdoutShouldContain("lib.Lib::doIt()void references restricted methods")
                .stdoutShouldContain("java.lang.foreign.MemorySegment::reinterpret(long)MemorySegment");
    }

    // TODO negative test cases
}
