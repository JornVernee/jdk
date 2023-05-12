/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.foreign.abi.x64.sysv;

import jdk.internal.foreign.Utils;
import jdk.internal.foreign.abi.AbstractLinker.LinkerType;
import jdk.internal.foreign.abi.SharedUtils;

import java.lang.foreign.GroupLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.SequenceLayout;
import java.lang.foreign.StructLayout;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static jdk.internal.foreign.abi.x64.sysv.ArgumentClassImpl.INTEGER;
import static jdk.internal.foreign.abi.x64.sysv.ArgumentClassImpl.POINTER;
import static jdk.internal.foreign.abi.x64.sysv.ArgumentClassImpl.SSE;

public final class TypeClass {
    enum Kind {
        STRUCT,
        POINTER,
        INTEGER,
        FLOAT,
        X87
    }

    private final Kind kind;
    final List<ArgumentClassImpl> classes;

    private TypeClass(Kind kind, List<ArgumentClassImpl> classes) {
        this.kind = kind;
        this.classes = classes;
    }

    public static TypeClass ofValue(ValueLayout layout) {
        final Kind kind;
        List<ArgumentClassImpl> argClasses = argumentClassFor(layout);
        kind = switch (argClasses.get(0)) {
            case POINTER -> Kind.POINTER;
            case INTEGER -> Kind.INTEGER;
            case SSE -> Kind.FLOAT;
            case X87 -> Kind.X87;
            default -> throw new IllegalStateException("Unexpected argument classes: " + argClasses.get(0));
        };
        return new TypeClass(kind, argClasses);
    }

    public static TypeClass ofStruct(GroupLayout layout) {
        return new TypeClass(Kind.STRUCT, classifyStructType(layout));
    }

    boolean inMemory() {
        return classes.stream().anyMatch(c -> c == ArgumentClassImpl.MEMORY);
    }

    private long numClasses(ArgumentClassImpl clazz) {
        return classes.stream().filter(c -> c == clazz).count();
    }

    public long nIntegerRegs() {
        return numClasses(INTEGER) + numClasses(ArgumentClassImpl.POINTER);
    }

    public long nVectorRegs() {
        return numClasses(SSE);
    }

    public Kind kind() {
        return kind;
    }

    // layout classification

    // The AVX 512 enlightened ABI says "eight eightbytes"
    // Although AMD64 0.99.6 states 4 eightbytes
    private static final int MAX_AGGREGATE_REGS_SIZE = 8;
    static final List<ArgumentClassImpl> COMPLEX_X87_CLASSES = List.of(
         ArgumentClassImpl.X87,
         ArgumentClassImpl.X87UP,
         ArgumentClassImpl.X87,
         ArgumentClassImpl.X87UP
    );

    private static List<ArgumentClassImpl> createMemoryClassArray(long size) {
        return IntStream.range(0, (int)size)
                .mapToObj(i -> ArgumentClassImpl.MEMORY)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static List<ArgumentClassImpl> argumentClassFor(ValueLayout layout) {
        return switch (SharedUtils.linkerType(layout)) {
            case BOOL, CHAR, SHORT, INT, LONG, LONG_LONG, SIZE_T -> List.of(INTEGER);
            case FLOAT, DOUBLE -> List.of(SSE);
            case PTR -> List.of(POINTER);
            case LONG_DOUBLE -> List.of(ArgumentClassImpl.X87, ArgumentClassImpl.X87UP);
        };
    }

    // TODO: handle zero length arrays
    private static List<ArgumentClassImpl> classifyStructType(GroupLayout type) {
        List<ArgumentClassImpl>[] eightbytes = groupByEightBytes(type);
        long nWords = eightbytes.length;
        if (nWords > MAX_AGGREGATE_REGS_SIZE) {
            return createMemoryClassArray(nWords);
        }

        ArrayList<ArgumentClassImpl> classes = new ArrayList<>();

        for (int idx = 0; idx < nWords; idx++) {
            List<ArgumentClassImpl> subclasses = eightbytes[idx];
            ArgumentClassImpl result = subclasses.stream()
                    .reduce(ArgumentClassImpl.NO_CLASS, ArgumentClassImpl::merge);
            classes.add(result);
        }

        for (int i = 0; i < classes.size(); i++) {
            ArgumentClassImpl c = classes.get(i);

            if (c == ArgumentClassImpl.MEMORY) {
                // if any of the eightbytes was passed in memory, pass the whole thing in memory
                return createMemoryClassArray(classes.size());
            }

            if (c == ArgumentClassImpl.X87UP) {
                if (i == 0) {
                    throw new IllegalArgumentException("Unexpected leading X87UP class");
                }

                if (classes.get(i - 1) != ArgumentClassImpl.X87) {
                    return createMemoryClassArray(classes.size());
                }
            }
        }

        if (classes.size() > 2) {
            if (classes.get(0) != SSE) {
                return createMemoryClassArray(classes.size());
            }

            for (int i = 1; i < classes.size(); i++) {
                if (classes.get(i) != ArgumentClassImpl.SSEUP) {
                    return createMemoryClassArray(classes.size());
                }
            }
        }

        return classes;
    }

    static TypeClass classifyLayout(MemoryLayout type) {
        try {
            if (type instanceof ValueLayout valueLayout) {
                return ofValue(valueLayout);
            } else if (type instanceof GroupLayout groupLayout) {
                return ofStruct(groupLayout);
            } else {
                throw new IllegalArgumentException("Unsupported layout: " + type);
            }
        } catch (UnsupportedOperationException e) {
            System.err.println("Failed to classify layout: " + type);
            throw e;
        }
    }

    private static List<ArgumentClassImpl>[] groupByEightBytes(GroupLayout group) {
        long offset = 0L;
        int nEightbytes;
        try {
            // alignUp can overflow the value, but it's okay since toIntExact still catches it
            nEightbytes = Math.toIntExact(Utils.alignUp(group.byteSize(), 8) / 8);
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("GroupLayout is too large: " + group, e);
        }
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<ArgumentClassImpl>[] groups = new List[nEightbytes];
        LongFunction<List<ArgumentClassImpl>> getGroup = groupOffset -> {
            List<ArgumentClassImpl> layouts = groups[(int)groupOffset / 8];
            if (layouts == null) {
                layouts = new ArrayList<>();
                groups[(int)groupOffset / 8] = layouts;
            }
            return layouts;
        };
        for (MemoryLayout l : group.memberLayouts()) {
            groupByEightBytes(l, offset, getGroup);
            if (group instanceof StructLayout) {
                offset += l.byteSize();
            }
        }
        return groups;
    }

    private static void groupByEightBytes(MemoryLayout l, long offset, LongFunction<List<ArgumentClassImpl>> groups) {
        if (l instanceof GroupLayout group) {
            for (MemoryLayout m : group.memberLayouts()) {
                groupByEightBytes(m, offset, groups);
                if (group instanceof StructLayout) {
                    offset += m.byteSize();
                }
            }
        } else if (l instanceof PaddingLayout) {
            return;
        } else if (l instanceof SequenceLayout seq) {
            MemoryLayout elem = seq.elementLayout();
            for (long i = 0 ; i < seq.elementCount() ; i++) {
                groupByEightBytes(elem, offset, groups);
                offset += elem.byteSize();
            }
        } else if (l instanceof ValueLayout vl) {
            List<ArgumentClassImpl> layouts = groups.apply(offset);
            // if the aggregate contains unaligned fields, it has class MEMORY
            List<ArgumentClassImpl> argumentClass = (offset % vl.byteAlignment()) == 0 ?
                    argumentClassFor(vl) :
                    List.of(ArgumentClassImpl.MEMORY);

            layouts.add(argumentClass.get(0));
            if (argumentClass.get(0) == ArgumentClassImpl.X87) {
                assert argumentClass.get(1) == ArgumentClassImpl.X87UP;
                List<ArgumentClassImpl> nextLayouts = groups.apply(offset + 8);
                nextLayouts.add(ArgumentClassImpl.X87UP);
            }
        } else {
            throw new IllegalStateException("Unexpected layout: " + l);
        }
    }
}
