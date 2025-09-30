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

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.foreign.MemorySegment;
import java.util.concurrent.TimeUnit;

import static org.openjdk.bench.java.lang.foreign.CallOverheadHelper.CONFINED_POINT;
import static org.openjdk.bench.java.lang.foreign.CallOverheadHelper.IDENTITY_MEMORY_ADDRESS;
import static org.openjdk.bench.java.lang.foreign.CallOverheadHelper.IDENTITY_MEMORY_ADDRESS_3;
import static org.openjdk.bench.java.lang.foreign.CallOverheadHelper.IDENTITY_STRUCT;
import static org.openjdk.bench.java.lang.foreign.CallOverheadHelper.IDENTITY_STRUCT_3;
import static org.openjdk.bench.java.lang.foreign.CallOverheadHelper.RECYCLING_ALLOCATOR;
import static org.openjdk.bench.java.lang.foreign.CallOverheadHelper.SHARED_POINT;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = { "--enable-native-access=ALL-UNNAMED", "-Djava.library.path=micro/native" })
public class CallOverheadAcquire {

    @Benchmark
    public MemorySegment panama_identity_struct_confined() throws Throwable {
        return (MemorySegment) IDENTITY_STRUCT.invokeExact(RECYCLING_ALLOCATOR, CONFINED_POINT);
    }

    @Benchmark
    public MemorySegment panama_identity_struct_shared() throws Throwable {
        return (MemorySegment) IDENTITY_STRUCT.invokeExact(RECYCLING_ALLOCATOR, SHARED_POINT);
    }

    @Benchmark
    public MemorySegment panama_identity_struct_confined_3() throws Throwable {
        return (MemorySegment) IDENTITY_STRUCT_3.invokeExact(RECYCLING_ALLOCATOR, CONFINED_POINT, CONFINED_POINT, CONFINED_POINT);
    }

    @Benchmark
    public MemorySegment panama_identity_struct_shared_3() throws Throwable {
        return (MemorySegment) IDENTITY_STRUCT_3.invokeExact(RECYCLING_ALLOCATOR, SHARED_POINT, SHARED_POINT, SHARED_POINT);
    }

    @Benchmark
    public MemorySegment panama_identity_memory_address_shared() throws Throwable {
        return (MemorySegment) IDENTITY_MEMORY_ADDRESS.invokeExact(SHARED_POINT);
    }

    @Benchmark
    public MemorySegment panama_identity_memory_address_confined() throws Throwable {
        return (MemorySegment) IDENTITY_MEMORY_ADDRESS.invokeExact(CONFINED_POINT);
    }

    @Benchmark
    public MemorySegment panama_identity_memory_address_shared_3() throws Throwable {
        return (MemorySegment) IDENTITY_MEMORY_ADDRESS_3.invokeExact(SHARED_POINT, SHARED_POINT, SHARED_POINT);
    }

    @Benchmark
    public MemorySegment panama_identity_memory_address_confined_3() throws Throwable {
        return (MemorySegment) IDENTITY_MEMORY_ADDRESS_3.invokeExact(CONFINED_POINT, CONFINED_POINT, CONFINED_POINT);
    }

    @Benchmark
    public MemorySegment panama_identity_memory_address_null() throws Throwable {
        return (MemorySegment) IDENTITY_MEMORY_ADDRESS.invokeExact(MemorySegment.NULL);
    }

    @Benchmark
    public MemorySegment panama_identity_memory_address_null_3() throws Throwable {
        return (MemorySegment) IDENTITY_MEMORY_ADDRESS_3.invokeExact(MemorySegment.NULL, MemorySegment.NULL, MemorySegment.NULL);
    }
}
