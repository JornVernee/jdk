/*
 * Copyright (c) 2020 Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.lang.invoke;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(3)
public class MethodHandlesWrappers {

    private static final MethodHandle MH_impl = MethodHandles.identity(Object.class);

    @FunctionalInterface
    public interface Func {
        Object apply(Object o);
    }

    @State(Scope.Benchmark)
    public static class BMState {
        final MethodHandle mh = MH_impl;
        final Func funcProxy = MethodHandleProxies.asInterfaceInstance(Func.class, MH_impl);
        final Func funcWrapped;

        {
            try {
                funcWrapped = MethodHandles.lookup().wrapAsFunctionalInterface(MH_impl, Func.class);
            } catch (IllegalAccessException e) {
                throw new InternalError(e);
            }
        }
    }

    @Benchmark
    public Object proxy(BMState state) throws Throwable {
        return state.funcProxy.apply(new Object());
    }

    @Benchmark
    public Object reflective(BMState state) throws Throwable {
        return state.mh.invokeExact(new Object());
    }

    @Benchmark
    public Object wrapped(BMState state) throws Throwable {
        return state.funcWrapped.apply(new Object());
    }
}
