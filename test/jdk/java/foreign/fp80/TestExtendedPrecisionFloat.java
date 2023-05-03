/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @enablePreview
 * @library ../ /test/lib
 * @requires jdk.foreign.linker == "SYS_V"
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestExtendedPrecisionFloat
 */

import org.testng.annotations.Test;

import java.lang.foreign.ExtendedPrecisionFloat;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

public class TestExtendedPrecisionFloat extends NativeTestHelper  {

    static final MethodHandle FROM_DOUBLE;
    static final MethodHandle TO_DOUBLE;

    static {
        System.loadLibrary("FP80");

        FROM_DOUBLE = Linker.nativeLinker().downcallHandle(
            SymbolLookup.loaderLookup().find("from_double").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.FP80, ValueLayout.JAVA_DOUBLE));
        TO_DOUBLE = Linker.nativeLinker().downcallHandle(
            SymbolLookup.loaderLookup().find("to_double").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.FP80));
    }

    @Test
    public void testFP80() throws Throwable {
        TestValue testValue = genTestValue(C_DOUBLE, null);
        double input = (double) testValue.value();

        ExtendedPrecisionFloat fp80 = (ExtendedPrecisionFloat) FROM_DOUBLE.invokeExact(input);
        double output = (double) TO_DOUBLE.invokeExact(fp80);

        testValue.check().accept(output);
    }
}
