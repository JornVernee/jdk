/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.c2.irTests;

import compiler.lib.ir_framework.*;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

/*
 * @test
 * @library /test/lib /
 * @run driver compiler.c2.irTests.TestCallNativeNode
 */

public class TestCallNativeNode {

    public static void main(String[] args) {
        TestFramework.runWithFlags(
            // allow loading the native library
            "--enable-native-access=ALL-UNNAMED"
        );
    }

    static final ValueLayout.OfInt C_INT = layout("int");
    static final ValueLayout.OfLong C_LONG_LONG = layout("long long");
    static final ValueLayout.OfFloat C_FLOAT = layout("float");
    static final ValueLayout.OfDouble C_DOUBLE = layout("double");

    static final StructLayout LAYOUT_SMALL_STRUCT = MemoryLayout.structLayout(C_LONG_LONG);

    static final MemorySegment SMALL_STRUCT = Arena.global().allocate(LAYOUT_SMALL_STRUCT);
    static final SegmentAllocator SMALL_STRUCT_RET_ALLOC
            = SegmentAllocator.prefixAllocator(Arena.global().allocate(LAYOUT_SMALL_STRUCT));

    @Test
    @IR(counts = {IRNode.CALL_NATIVE, "1"})
    public static void testEmpty() throws Throwable {
        Handles.EMPTY.invokeExact();
    }

    @Test
    @IR(counts = {IRNode.CALL_NATIVE, "1"})
    public static int testIdInt() throws Throwable {
        return (int) Handles.ID_INT.invokeExact(1);
    }

    @Test
    @IR(counts = {IRNode.CALL_NATIVE, "1"})
    public static long testIdLong() throws Throwable {
        return (long) Handles.ID_LONG.invokeExact(1L);
    }

    @Test
    @IR(counts = {IRNode.CALL_NATIVE, "1"})
    public static float testIdFloat() throws Throwable {
        return (float) Handles.ID_FLOAT.invokeExact(1.0F);
    }

    @Test
    @IR(counts = {IRNode.CALL_NATIVE, "1"})
    public static double testIdDouble() throws Throwable {
        return (double) Handles.ID_DOUBLE.invokeExact(1.0D);
    }

    @Test
    @IR(counts = {IRNode.CALL_NATIVE, "1"})
    public static MemorySegment testSmallStruct() throws Throwable {
        return (MemorySegment) Handles.ID_SMALL_STRUCT.invokeExact(SMALL_STRUCT_RET_ALLOC, SMALL_STRUCT);
    }

    // where

    @SuppressWarnings("unchecked")
    private static <T extends ValueLayout> T layout(String name) {
        return (T) Linker.nativeLinker().canonicalLayouts().get(name);
    }

    private static class Handles {
        static {
            // keep this separate so that we have a chance to pass --enable-native-access when running
            System.loadLibrary("NativeCallNode");
        }

        static final MethodHandle EMPTY = downcallHandle("empty", FunctionDescriptor.ofVoid());
        static final MethodHandle ID_INT = downcallHandle("id_int", FunctionDescriptor.of(C_INT, C_INT));
        static final MethodHandle ID_LONG = downcallHandle("id_long", FunctionDescriptor.of(C_LONG_LONG, C_LONG_LONG));
        static final MethodHandle ID_FLOAT = downcallHandle("id_float", FunctionDescriptor.of(C_FLOAT, C_FLOAT));
        static final MethodHandle ID_DOUBLE = downcallHandle("id_double", FunctionDescriptor.of(C_DOUBLE, C_DOUBLE));
        static final MethodHandle ID_SMALL_STRUCT = downcallHandle("id_small_struct",
                FunctionDescriptor.of(LAYOUT_SMALL_STRUCT, LAYOUT_SMALL_STRUCT));

        private static MethodHandle downcallHandle(String name, FunctionDescriptor desc) {
            return Linker.nativeLinker().downcallHandle(
                    SymbolLookup.loaderLookup().findOrThrow(name),
                    desc,
                    Linker.Option.critical(false));
        }
    }
}
