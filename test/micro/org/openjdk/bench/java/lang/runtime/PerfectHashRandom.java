package org.openjdk.bench.java.lang.runtime;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.runtime.PerfectHash;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(3)
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public class PerfectHashRandom {

    private static final MethodHandle MH_HashMap_getOrDefault;

    static {
        try {
            MH_HashMap_getOrDefault = MethodHandles.lookup().findVirtual(Map.class, "getOrDefault",
                    MethodType.methodType(Object.class, Object.class, Object.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static final MethodType CALL_TYPE = MethodType.methodType(int.class, Object.class);
    private static final MutableCallSite CALL_SITE = new MutableCallSite(CALL_TYPE);
    private static final MethodHandle TARGET = CALL_SITE.dynamicInvoker();

    private static final int BATCH_SIZE = 1_000_000;

    @Param({
        //"hashMap",
        "perfectHash"
    })
    public String strategy;

    @Param({
        //"5",
        "10",
        //"25",
        //"50",
        //"100"
    })
    public int numCases;

    @Param({
        //"5",
        //"10",
        "15",
        //"20"
    })
    public int caseLabelLength;

    // TODO test non-Latin1 chars as well
    private static final String caseChars = "abcdefghijklmnopqrstuvwxyz0123456789";

    public String[] inputs;

    @Setup(Level.Trial)
    public void setup() throws Throwable {
        Random caseRandom = new Random(0);
        String[] keyset = Stream.generate(() -> genCase(caseRandom))
                .distinct()
                .limit(numCases)
                .toArray(String[]::new);

        System.out.println(Arrays.toString(keyset));

        MethodHandle targetHandle = switch (strategy) {
            case "hashMap" -> hashMapLookupHandle(keyset);
            case "perfectHash" -> PerfectHash.minimalPerfectHash(Set.of(keyset));
            default -> throw new IllegalStateException("Unsupported strategy: " + strategy);
        };

        CALL_SITE.setTarget(targetHandle);

        inputs = new String[BATCH_SIZE];
        Random inputRandom = new Random(0);
        for (int i = 0; i < BATCH_SIZE; i++) {
            // TODO the default case has just as much chance of being hit as other cases,
            // but this might not be representative of real-world scenarios
            // e.g. the default case could be more or less common
            // need to do corpus experiment
            int next = inputRandom.nextInt(keyset.length + 1);
            inputs[i] = next == keyset.length ? "_" : keyset[next];
        }
    }

    private MethodHandle hashMapLookupHandle(String[] keyset) {
        HashMap<String, Integer> hashMap = new HashMap<>();
        for (int i = 0; i < keyset.length; i++) {
            hashMap.put(keyset[i], i);
        }
        return MethodHandles.insertArguments(
            MH_HashMap_getOrDefault.bindTo(hashMap),
            1,
            Integer.valueOf(0)
        ).asType(MethodType.methodType(int.class, Object.class));
    }

    private String genCase(Random rand) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < caseLabelLength; i++) {
            sb.append(caseChars.charAt(rand.nextInt(caseChars.length())));
        }
        return sb.toString();
    }

    @Benchmark
    public void testStringSwitch(Blackhole bh) throws Throwable {
        for (int i = 0; i < inputs.length; i++) {
            bh.consume((int) TARGET.invokeExact((Object) inputs[i]));
        }
    }
}