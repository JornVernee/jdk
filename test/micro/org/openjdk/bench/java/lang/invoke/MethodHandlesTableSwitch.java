package org.openjdk.bench.java.lang.invoke;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 5, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 10, time = 500, timeUnit = TimeUnit.MILLISECONDS)
@State(org.openjdk.jmh.annotations.Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Fork(3)
public class MethodHandlesTableSwitch {

    private static final MethodType callType = MethodType.methodType(int.class, int.class);

    private static final MutableCallSite cs = new MutableCallSite(callType);
    private static final MethodHandle target = cs.dynamicInvoker();

    private static final MutableCallSite csWithOffset = new MutableCallSite(callType);
    private static final MethodHandle targetWithOffset = csWithOffset.dynamicInvoker();

    private static final MethodHandle MH_SUBTRACT;
    private static final MethodHandle MH_DEFAULT;

    static {
        try {
            MH_SUBTRACT = MethodHandles.lookup().findStatic(MethodHandlesTableSwitch.class, "subtract",
                    MethodType.methodType(int.class, int.class, int.class));
            MH_DEFAULT = MethodHandles.lookup().findStatic(MethodHandlesTableSwitch.class, "defaultCase",
                    MethodType.methodType(int.class, int.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    // Using batch size since we really need a per-invocation setup
    // but the measured code is too fast. Using JMH batch size doesn't work
    // since there is no way to do a batch-level setup as well.
    private static final int BATCH_SIZE = 1_000_000;

    @Param({ "5", "10", "25", "50", "100" })
    public int numCases;

    public static final int OFFSET = 150;

    public int[] inputs;
    public int[] inputsOffset;

    @Setup(Level.Trial)
    public void setupTrial() throws Throwable {
        MethodHandle switcher = MethodHandles.tableSwitch(
                IntStream.range(0, numCases)
                        .mapToObj(i -> MethodHandles.dropArguments(MethodHandles.constant(int.class, i), 0, int.class))
                        .toArray(MethodHandle[]::new));
        cs.setTarget(switcher);

        MethodHandle switcherWithOffset = MethodHandles.filterArguments(switcher, 0, MethodHandles.insertArguments(MH_SUBTRACT, 1, OFFSET));
        csWithOffset.setTarget(switcherWithOffset);

        inputs = new int[BATCH_SIZE];
        inputsOffset = new int[BATCH_SIZE];
        Random rand = new Random(0);
        for (int i = 0; i < BATCH_SIZE; i++) {
            inputs[i] = rand.nextInt(numCases);
            inputsOffset[i] = inputs[i] + OFFSET;
        }
    }

    private static int subtract(int a, int b) {
        return a - b;
    }

    private static int defaultCase(int x) {
        throw new IllegalStateException();
    }

    @Benchmark
    public void testSwitch_offset_no(Blackhole bh) throws Throwable {
        for (int i = 0; i < inputs.length; i++) {
            bh.consume((int) target.invokeExact(inputs[i]));
        }
    }

    @Benchmark
    public void testSwitch_offset_yes(Blackhole bh) throws Throwable {
        for (int i = 0; i < inputsOffset.length; i++) {
            bh.consume((int) targetWithOffset.invokeExact(inputsOffset[i]));
        }
    }

}