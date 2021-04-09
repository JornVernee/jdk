/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(3)
public class MethodHandlesEmulateLookupSwitch {

    private static final String[] CASES = {
        "yqnl939vpc",
        "fr3k96epfu",
        "5auxi2p8a9",
        "33floc73wp",
        "uh9ckdkq2g",
        "5v64pqqrdh",
        "9qzgm7h08s",
        "cerl0yfs52",
        "h7jnjazhvz",
        "dqm4u574j1",
    };

    private static final MethodHandle EMULATED_HASHMAP;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            {
                MethodHandle MH_Map_getOrDefault = lookup.findVirtual(Map.class, "getOrDefault",
                        MethodType.methodType(Object.class, Object.class, Object.class));

                Map<String, Integer> string2Index = new HashMap<>();

                for (int i = 0; i < CASES.length; i++) {
                    string2Index.put(CASES[i], i);
                }

                MethodHandle MH_String2Index = MH_Map_getOrDefault;
                MH_String2Index = MH_String2Index.bindTo(string2Index);
                MH_String2Index = MethodHandles.insertArguments(MH_String2Index, 1, -1);
                MH_String2Index = MH_String2Index.asType(MethodType.methodType(int.class, String.class));

                EMULATED_HASHMAP = emulateLookupSwitch(MH_String2Index);
            }
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static MethodHandle emulateLookupSwitch(MethodHandle MH_String2Index) {
        MethodHandle[] cases = new MethodHandle[10];

        for (int i = 0; i < cases.length; i++) {
            cases[i] = simpleCase(String.valueOf(i));
        }

        MethodHandle switcher = MethodHandles.tableSwitch(
            simpleCase("-1"),
            cases
        );

        return MethodHandles.filterArguments(switcher, 0, MH_String2Index);
    }

    private static MethodHandle simpleCase(String value) {
        return MethodHandles.dropArguments(MethodHandles.constant(String.class, value), 0, int.class);
    }

    private static final int BATCH_SIZE = 1_000_000;

    @Setup(Level.Trial)
    public void setup() throws Throwable {
        inputs = new String[BATCH_SIZE];
        Random inputRandom = new Random(0);
        for (int i = 0; i < BATCH_SIZE; i++) {
            int next = inputRandom.nextInt(CASES.length + 1);
            inputs[i] = next == CASES.length ? "_" : CASES[next];
        }
    }

    public String[] inputs;

    @Benchmark
    public void nativeLookupSwitch(Blackhole bh) throws Throwable {
        for (int i = 0; i < inputs.length; i++) {
            bh.consume(nativeLookupSwitch(inputs[i]));
        }
    }

    @Benchmark
    public void emulatedLookupSwitch(Blackhole bh) throws Throwable {
        for (int i = 0; i < inputs.length; i++) {
            bh.consume((String) EMULATED_HASHMAP.invokeExact(inputs[i]));
        }
    }

    static String nativeLookupSwitch(String switchOn) {
        String res;
        switch (switchOn) {
            case "yqnl939vpc" -> res = "0";
            case "fr3k96epfu" -> res = "1";
            case "5auxi2p8a9" -> res = "2";
            case "33floc73wp" -> res = "3";
            case "uh9ckdkq2g" -> res = "4";
            case "5v64pqqrdh" -> res = "5";
            case "9qzgm7h08s" -> res = "6";
            case "cerl0yfs52" -> res = "7";
            case "h7jnjazhvz" -> res = "8";
            case "dqm4u574j1" -> res = "9";
            default -> res = "-1";
        }
        return res;
    }

}
