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

import static org.openjdk.bench.java.lang.foreign.CallOverheadHelper.FUNC;
import static org.openjdk.bench.java.lang.foreign.CallOverheadHelper.FUNC_ADDR;
import static org.openjdk.bench.java.lang.foreign.CallOverheadHelper.FUNC_CRITICAL;
import static org.openjdk.bench.java.lang.foreign.CallOverheadHelper.FUNC_CRITICAL_V;
import static org.openjdk.bench.java.lang.foreign.CallOverheadHelper.IDENTITY;
import static org.openjdk.bench.java.lang.foreign.CallOverheadHelper.IDENTITY_ADDR;
import static org.openjdk.bench.java.lang.foreign.CallOverheadHelper.IDENTITY_CRITICAL;
import static org.openjdk.bench.java.lang.foreign.CallOverheadHelper.IDENTITY_CRITICAL_V;
import static org.openjdk.bench.java.lang.foreign.CallOverheadHelper.IDENTITY_STRUCT;
import static org.openjdk.bench.java.lang.foreign.CallOverheadHelper.IDENTITY_STRUCT_ADDR;
import static org.openjdk.bench.java.lang.foreign.CallOverheadHelper.IDENTITY_STRUCT_CRITICAL;
import static org.openjdk.bench.java.lang.foreign.CallOverheadHelper.IDENTITY_STRUCT_CRITICAL_V;
import static org.openjdk.bench.java.lang.foreign.CallOverheadHelper.POINT;
import static org.openjdk.bench.java.lang.foreign.CallOverheadHelper.RECYCLING_ALLOCATOR;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 3, jvmArgs = { "--enable-native-access=ALL-UNNAMED", "-Djava.library.path=micro/native" })
public class CallOverheadCritical {

    @Benchmark
    public void panama_blank() throws Throwable {
        FUNC.invokeExact();
    }

    @Benchmark
    public void panama_blank_critical() throws Throwable {
        FUNC_CRITICAL.invokeExact();
    }

    @Benchmark
    public void panama_blank_critical_v() throws Throwable {
        FUNC_CRITICAL_V.invokeExact(FUNC_ADDR);
    }

    @Benchmark
    public int panama_identity_int() throws Throwable {
        return (int) IDENTITY.invokeExact(10);
    }

    @Benchmark
    public int panama_identity_int_critical() throws Throwable {
        return (int) IDENTITY_CRITICAL.invokeExact(10);
    }

    @Benchmark
    public int panama_identity_int_critical_v() throws Throwable {
        return (int) IDENTITY_CRITICAL_V.invokeExact(IDENTITY_ADDR, 10);
    }

    @Benchmark
    @Fork(value = 3, jvmArgs = { "--enable-native-access=ALL-UNNAMED", "-Djava.library.path=micro/native",
        "-XX:+UnlockDiagnosticVMOptions", "-XX:-UseL2NIntrinsics"})
    public int panama_identity_int_critical_no_intrinsics() throws Throwable {
        return (int) IDENTITY_CRITICAL.invokeExact(10);
    }

    @Benchmark
    public MemorySegment panama_identity_struct() throws Throwable {
        return (MemorySegment) IDENTITY_STRUCT.invokeExact(RECYCLING_ALLOCATOR, POINT);
    }

    @Benchmark
    public MemorySegment panama_identity_struct_critical() throws Throwable {
        return (MemorySegment) IDENTITY_STRUCT_CRITICAL.invokeExact(RECYCLING_ALLOCATOR, POINT);
    }

    @Benchmark
    public MemorySegment panama_identity_struct_critical_v() throws Throwable {
        return (MemorySegment) IDENTITY_STRUCT_CRITICAL_V.invokeExact(IDENTITY_STRUCT_ADDR, RECYCLING_ALLOCATOR, POINT);
    }
}
