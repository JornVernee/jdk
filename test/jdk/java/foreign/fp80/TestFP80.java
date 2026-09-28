/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @run testng/othervm --enable-native-access=ALL-UNNAMED TestFP80
 */

import org.testng.annotations.Test;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;

public class TestFP80 extends NativeTestHelper  {

    static final MethodHandle FROM_DOUBLE;
    static final MethodHandle TO_DOUBLE;
    static final MethodHandle FROM_DOUBLE_STRUCT;
    static final MethodHandle TO_DOUBLE_STRUCT;
    static final MethodHandle UPCALL;
    static final MethodHandle UPCALL_STRUCT;

    static final MemoryLayout LONG_DOUBLE_LAYOUT = Linker.nativeLinker().canonicalLayouts().get("long double");
    static final StructLayout S_LAYOUT = MemoryLayout.structLayout(LONG_DOUBLE_LAYOUT);

    static final SegmentAllocator LONG_ARR_ALLOC
            = (size, _) -> MemorySegment.ofArray(new long[(int) ((size + 7) / 8)]);

    static {
        System.loadLibrary("FP80");

        FROM_DOUBLE = Linker.nativeLinker().downcallHandle(
                SymbolLookup.loaderLookup().find("from_double").orElseThrow(),
                FunctionDescriptor.of(LONG_DOUBLE_LAYOUT, ValueLayout.JAVA_DOUBLE));
        TO_DOUBLE = Linker.nativeLinker().downcallHandle(
                SymbolLookup.loaderLookup().find("to_double").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, LONG_DOUBLE_LAYOUT));
        FROM_DOUBLE_STRUCT = Linker.nativeLinker().downcallHandle(
                SymbolLookup.loaderLookup().find("from_double_struct").orElseThrow(),
                FunctionDescriptor.of(S_LAYOUT, ValueLayout.JAVA_DOUBLE));
        TO_DOUBLE_STRUCT = Linker.nativeLinker().downcallHandle(
                SymbolLookup.loaderLookup().find("to_double_struct").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, S_LAYOUT));
        UPCALL = Linker.nativeLinker().downcallHandle(
                SymbolLookup.loaderLookup().find("upcall").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, C_POINTER));
        UPCALL_STRUCT = Linker.nativeLinker().downcallHandle(
                SymbolLookup.loaderLookup().find("upcall_struct").orElseThrow(),
                FunctionDescriptor.of(ValueLayout.JAVA_DOUBLE, ValueLayout.JAVA_DOUBLE, C_POINTER));
    }

    @Test
    public void testFP80() throws Throwable {
        TestValue testValue = genTestValue(C_DOUBLE, null);
        double input = (double) testValue.value();

        MemorySegment fp80 = (MemorySegment) FROM_DOUBLE.invokeExact(LONG_ARR_ALLOC, input);
        double output = (double) TO_DOUBLE.invokeExact(fp80);
        testValue.check().accept(output);
    }

    @Test
    public void testFP80Struct() throws Throwable {
        TestValue testValue = genTestValue(C_DOUBLE, null);
        double input = (double) testValue.value();

        MemorySegment fp80 = (MemorySegment) FROM_DOUBLE_STRUCT.invokeExact(LONG_ARR_ALLOC, input);
        double output = (double) TO_DOUBLE_STRUCT.invokeExact(fp80);
        testValue.check().accept(output);
    }

    @Test
    public void testFP80Upcall() throws Throwable {
        TestValue testValue = genTestValue(C_DOUBLE, null);
        double input = (double) testValue.value();

        MemorySegment stub = upcallStub(TestFP80.class, "payload",
                FunctionDescriptor.of(LONG_DOUBLE_LAYOUT, LONG_DOUBLE_LAYOUT));
        double output = (double) UPCALL.invokeExact(input, stub);
        testValue.check().accept(output);
    }

    @Test
    public void testFP80UpcallStruct() throws Throwable {
        TestValue testValue = genTestValue(C_DOUBLE, null);
        double input = (double) testValue.value();

        MemorySegment stub = upcallStub(TestFP80.class, "payload",
                FunctionDescriptor.of(S_LAYOUT, S_LAYOUT));
        double output = (double) UPCALL_STRUCT.invokeExact(input, stub);
        testValue.check().accept(output);
    }

    public static MemorySegment payload(MemorySegment s) {
        return s;
    }
}