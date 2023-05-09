/*
 * Copyright (c) 2020, 2023, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2023, Institute of Software, Chinese Academy of Sciences.
 * All rights reserved.
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
 *
 */

package jdk.internal.foreign.abi.riscv64.linux;

import jdk.internal.foreign.abi.AbstractLinker;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.internal.foreign.layout.ValueLayouts;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.nio.ByteOrder;

public final class LinuxRISCV64Linker extends AbstractLinker {

    public static LinuxRISCV64Linker getInstance() {
        final class Holder {
            private static final LinuxRISCV64Linker INSTANCE = new LinuxRISCV64Linker();
        }

        return Holder.INSTANCE;
    }

    private LinuxRISCV64Linker() {
        // Ensure there is only one instance
    }

    @Override
    protected MethodHandle arrangeDowncall(MethodType inferredMethodType, FunctionDescriptor function, LinkerOptions options) {
        return LinuxRISCV64CallArranger.arrangeDowncall(inferredMethodType, function, options);
    }

    @Override
    protected UpcallStubFactory arrangeUpcall(MethodType targetType, FunctionDescriptor function, LinkerOptions options) {
        return LinuxRISCV64CallArranger.arrangeUpcall(targetType, function, options);
    }

    public interface Layouts {
        ValueLayout.OfBoolean C_BOOL = (ValueLayout.OfBoolean) ValueLayouts.valueLayout(boolean.class, ByteOrder.LITTLE_ENDIAN)
                .withClassifier(TypeClass.INTEGER);
        ValueLayout.OfByte C_CHAR = (ValueLayout.OfByte) ValueLayouts.valueLayout(byte.class, ByteOrder.LITTLE_ENDIAN)
                .withClassifier(TypeClass.INTEGER);
        ValueLayout.OfShort C_SHORT = (ValueLayout.OfShort) ValueLayouts.valueLayout(short.class, ByteOrder.LITTLE_ENDIAN)
                .withClassifier(TypeClass.INTEGER);
        ValueLayout.OfInt C_INT = (ValueLayout.OfInt) ValueLayouts.valueLayout(int.class, ByteOrder.LITTLE_ENDIAN)
                .withClassifier(TypeClass.INTEGER);
        ValueLayout.OfLong C_LONG_LONG = (ValueLayout.OfLong) ValueLayouts.valueLayout(long.class, ByteOrder.LITTLE_ENDIAN)
                .withClassifier(TypeClass.INTEGER);
        ValueLayout.OfFloat C_FLOAT = (ValueLayout.OfFloat) ValueLayouts.valueLayout(float.class, ByteOrder.LITTLE_ENDIAN)
                .withClassifier(TypeClass.FLOAT);
        ValueLayout.OfDouble C_DOUBLE = (ValueLayout.OfDouble) ValueLayouts.valueLayout(double.class, ByteOrder.LITTLE_ENDIAN)
                .withClassifier(TypeClass.FLOAT);
        AddressLayout C_POINTER = (AddressLayout) ValueLayouts.valueLayout(MemorySegment.class, ByteOrder.LITTLE_ENDIAN)
                .withClassifier(TypeClass.POINTER);
    }

    @Override
    public MemoryLayout linkerType(String name) {
        return switch (name) {
            case "_Bool" -> Layouts.C_BOOL;
            case "char" -> Layouts.C_CHAR;
            case "short" -> Layouts.C_SHORT;
            case "int" -> Layouts.C_INT;
            case "long long" -> Layouts.C_LONG_LONG;
            case "float" -> Layouts.C_FLOAT;
            case "double" -> Layouts.C_DOUBLE;
            case "void*" -> Layouts.C_POINTER;
            default -> throw new IllegalStateException("Unknown type: " + name);
        };
    }

    @Override
    protected ByteOrder linkerByteOrder() {
        return ByteOrder.LITTLE_ENDIAN;
    }
}
