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
 * @library /test/lib ./cases/modular
 * @build JScanTestBase org.singlejar/* cases.classpath.singlejar.main.Main
 * @run testng TestPrintNativeAccess
 */

import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.nio.file.Path;

class TestPrintNativeAccess extends JScanTestBase {

    Path singleJarClassPath;
    Path singleJarModular;

    @BeforeClass
    public void before() {
        singleJarClassPath = Path.of("singleJar.jar");
        Path classes = Path.of(System.getProperty("test.classes", ""));
        assertSuccess(jar("--create", "--file", singleJarClassPath.toString(),
                "-C", classes.toString(), "main/Main.class"));

        singleJarModular = Path.of("singleJar_modular.jar");
        Path singleJarRoot = findModuleRoot("org.singlejar");
        assertSuccess(jar("--create", "--file", singleJarModular.toString(),
                "-C", singleJarRoot.toString(), "main/Main.class",
                "-C", singleJarRoot.toString(), "module-info.class"));
    }

    @Test
    public void testSingleJarClassPath() {
        assertSuccess(jscanRestricted("--class-path", singleJarClassPath.toString(), "--print-native-access"))
                .stderrShouldBeEmpty()
                .stdoutShouldContain("ALL-UNNAMED");
    }

    @Test
    public void testSingleJarModulePath() {
        assertSuccess(jscanRestricted("--module-path", singleJarModular.toString(), "--print-native-access", "--add-modules", "org.singlejar"))
                .stderrShouldBeEmpty()
                .stdoutShouldContain("org.singlejar");
    }
}
