/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.lang.foreign;

import java.lang.foreign.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;

import static java.lang.invoke.MethodHandles.insertArguments;

public class CallOverheadHelper extends CLayouts {

    static final Linker ABI = Linker.nativeLinker();

    // Note: addresses below are deliberately NOT final
    // so that they are not seen as constants by C2
    static final MethodHandle FUNC;
    static final MethodHandle FUNC_CRITICAL;
    static final MethodHandle FUNC_V;
    static final MethodHandle FUNC_CRITICAL_V;
    static MemorySegment FUNC_ADDR;

    static final MethodHandle IDENTITY;
    static final MethodHandle IDENTITY_CRITICAL;
    static final MethodHandle IDENTITY_V;
    static final MethodHandle IDENTITY_CRITICAL_V;
    static MemorySegment IDENTITY_ADDR;

    static final MethodHandle IDENTITY_STRUCT;
    static final MethodHandle IDENTITY_STRUCT_CRITICAL;
    static final MethodHandle IDENTITY_STRUCT_V;
    static final MethodHandle IDENTITY_STRUCT_CRITICAL_V;
    static MemorySegment IDENTITY_STRUCT_ADDR;

    static final MethodHandle IDENTITY_STRUCT_3;
    static final MethodHandle IDENTITY_STRUCT_3_V;
    static MemorySegment IDENTITY_STRUCT_3_ADDR;

    static final MethodHandle IDENTITY_MEMORY_ADDRESS;
    static final MethodHandle IDENTITY_MEMORY_ADDRESS_V;
    static MemorySegment IDENTITY_MEMORY_ADDRESS_ADDR;

    static final MethodHandle IDENTITY_MEMORY_ADDRESS_3;
    static final MethodHandle IDENTITY_MEMORY_ADDRESS_3_V;
    static MemorySegment IDENTITY_MEMORY_ADDRESS_3_ADDR;

    static final MethodHandle ARGS1;
    static final MethodHandle ARGS1_V;
    static MemorySegment ARGS1_ADDR;

    static final MethodHandle ARGS2;
    static final MethodHandle ARGS2_V;
    static MemorySegment ARGS2_ADDR;

    static final MethodHandle ARGS3;
    static final MethodHandle ARGS3_V;
    static MemorySegment ARGS3_ADDR;

    static final MethodHandle ARGS4;
    static final MethodHandle ARGS4_V;
    static MemorySegment ARGS4_ADDR;

    static final MethodHandle ARGS5;
    static final MethodHandle ARGS5_V;
    static MemorySegment ARGS5_ADDR;

    static final MethodHandle ARGS10;
    static final MethodHandle ARGS10_V;
    static MemorySegment ARGS10_ADDR;

    static final MemoryLayout POINT_LAYOUT = MemoryLayout.structLayout(
            C_INT, C_INT
    );

    static final MemorySegment SHARED_POINT;

    static {
        Arena scope = Arena.ofShared();
        SHARED_POINT = scope.allocate(POINT_LAYOUT);
    }

    static final MemorySegment CONFINED_POINT;

    static {
        Arena scope = Arena.ofConfined();
        CONFINED_POINT = scope.allocate(POINT_LAYOUT);
    }

    static final MemorySegment POINT;

    static {
        Arena scope = Arena.ofAuto();
        POINT = scope.allocate(POINT_LAYOUT);
    }

    static final SegmentAllocator RECYCLING_ALLOCATOR;

    static {
        Arena scope = Arena.ofAuto();
        RECYCLING_ALLOCATOR = SegmentAllocator.prefixAllocator(scope.allocate(POINT_LAYOUT));
        System.loadLibrary("CallOverheadJNI");

        System.loadLibrary("CallOverhead");
        SymbolLookup loaderLibs = SymbolLookup.loaderLookup();
        {
            FUNC_ADDR = loaderLibs.findOrThrow("func");
            FunctionDescriptor fd = FunctionDescriptor.ofVoid();
            FUNC_V = ABI.downcallHandle(fd);
            FUNC_CRITICAL_V = ABI.downcallHandle(fd, Linker.Option.critical(false));
            FUNC = insertArguments(FUNC_V, 0, FUNC_ADDR);
            FUNC_CRITICAL = insertArguments(FUNC_CRITICAL_V, 0, FUNC_ADDR);
        }
        {
            IDENTITY_ADDR = loaderLibs.findOrThrow("identity");
            FunctionDescriptor fd = FunctionDescriptor.of(C_INT, C_INT);
            IDENTITY_V = ABI.downcallHandle(fd);
            IDENTITY_CRITICAL_V = ABI.downcallHandle(fd, Linker.Option.critical(false));
            IDENTITY = insertArguments(IDENTITY_V, 0, IDENTITY_ADDR);
            IDENTITY_CRITICAL = insertArguments(IDENTITY_CRITICAL_V, 0, IDENTITY_ADDR);
        }
        {
            IDENTITY_STRUCT_ADDR = loaderLibs.findOrThrow("identity_struct");
            FunctionDescriptor fd = FunctionDescriptor.of(POINT_LAYOUT, POINT_LAYOUT);
            IDENTITY_STRUCT_V = ABI.downcallHandle(fd);
            IDENTITY_STRUCT_CRITICAL_V = ABI.downcallHandle(fd, Linker.Option.critical(false));
            IDENTITY_STRUCT = insertArguments(IDENTITY_STRUCT_V, 0, IDENTITY_STRUCT_ADDR);
            IDENTITY_STRUCT_CRITICAL = insertArguments(IDENTITY_STRUCT_CRITICAL_V, 0, IDENTITY_STRUCT_ADDR);
        }

        IDENTITY_STRUCT_3_ADDR = loaderLibs.findOrThrow("identity_struct_3");
        IDENTITY_STRUCT_3_V = ABI.downcallHandle(
                FunctionDescriptor.of(POINT_LAYOUT, POINT_LAYOUT, POINT_LAYOUT, POINT_LAYOUT));
        IDENTITY_STRUCT_3 = insertArguments(IDENTITY_STRUCT_3_V, 0, IDENTITY_STRUCT_3_ADDR);

        IDENTITY_MEMORY_ADDRESS_ADDR = loaderLibs.findOrThrow("identity_memory_address");
        IDENTITY_MEMORY_ADDRESS_V = ABI.downcallHandle(
                FunctionDescriptor.of(C_POINTER, C_POINTER));
        IDENTITY_MEMORY_ADDRESS = insertArguments(IDENTITY_MEMORY_ADDRESS_V, 0, IDENTITY_MEMORY_ADDRESS_ADDR);

        IDENTITY_MEMORY_ADDRESS_3_ADDR = loaderLibs.findOrThrow("identity_memory_address_3");
        IDENTITY_MEMORY_ADDRESS_3_V = ABI.downcallHandle(
                FunctionDescriptor.of(C_POINTER, C_POINTER, C_POINTER, C_POINTER));
        IDENTITY_MEMORY_ADDRESS_3 = insertArguments(IDENTITY_MEMORY_ADDRESS_3_V, 0, IDENTITY_MEMORY_ADDRESS_3_ADDR);

        ARGS1_ADDR = loaderLibs.findOrThrow("args1");
        ARGS1_V = ABI.downcallHandle(
                FunctionDescriptor.ofVoid(C_LONG_LONG));
        ARGS1 = insertArguments(ARGS1_V, 0, ARGS1_ADDR);

        ARGS2_ADDR = loaderLibs.findOrThrow("args2");
        ARGS2_V = ABI.downcallHandle(
                FunctionDescriptor.ofVoid(C_LONG_LONG, C_DOUBLE));
        ARGS2 = insertArguments(ARGS2_V, 0, ARGS2_ADDR);

        ARGS3_ADDR = loaderLibs.findOrThrow("args3");
        ARGS3_V = ABI.downcallHandle(
                FunctionDescriptor.ofVoid(C_LONG_LONG, C_DOUBLE, C_LONG_LONG));
        ARGS3 = insertArguments(ARGS3_V, 0, ARGS3_ADDR);

        ARGS4_ADDR = loaderLibs.findOrThrow("args4");
        ARGS4_V = ABI.downcallHandle(
                FunctionDescriptor.ofVoid(C_LONG_LONG, C_DOUBLE, C_LONG_LONG, C_DOUBLE));
        ARGS4 = insertArguments(ARGS4_V, 0, ARGS4_ADDR);

        ARGS5_ADDR = loaderLibs.findOrThrow("args5");
        ARGS5_V = ABI.downcallHandle(
                FunctionDescriptor.ofVoid(C_LONG_LONG, C_DOUBLE, C_LONG_LONG, C_DOUBLE, C_LONG_LONG));
        ARGS5 = insertArguments(ARGS5_V, 0, ARGS5_ADDR);

        ARGS10_ADDR = loaderLibs.findOrThrow("args10");
        ARGS10_V = ABI.downcallHandle(
                FunctionDescriptor.ofVoid(C_LONG_LONG, C_DOUBLE, C_LONG_LONG, C_DOUBLE, C_LONG_LONG,
                                          C_DOUBLE, C_LONG_LONG, C_DOUBLE, C_LONG_LONG, C_DOUBLE));
        ARGS10 = insertArguments(ARGS10_V, 0, ARGS10_ADDR);
    }

    static native void blank();
    static native int identity(int x);
}
