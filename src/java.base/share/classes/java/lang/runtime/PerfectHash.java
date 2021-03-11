package java.lang.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * TODO
 */
public class PerfectHash {

    private static final MethodHandle MH_DEFAULT_HASH;
    private static final MethodHandle MH_LOOKUP;
    private static final MethodHandle MH_CONFIRM;
    private static final MethodHandle MH_DEFAULT_INDEX;
    private static final MethodHandle MH_DEFAULT_THROW;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MH_LOOKUP = lookup.findStatic(PerfectHash.class, "lookup",
                    MethodType.methodType(int.class, Object.class, MethodHandle.class, MethodHandle.class, int.class));
            MH_CONFIRM = lookup.findStatic(PerfectHash.class, "confirmKey",
                    MethodType.methodType(int.class, int.class, Object.class, Object.class, int.class));
            MH_DEFAULT_INDEX = lookup.findStatic(PerfectHash.class, "defaultIndex",
                    MethodType.methodType(int.class, int.class, Object.class));
            MH_DEFAULT_THROW = lookup.findStatic(PerfectHash.class, "defaultThrow",
                    MethodType.methodType(int.class, int.class, Object.class));
            MH_DEFAULT_HASH = lookup.findStatic(PerfectHash.class, "defaultHash",
                    MethodType.methodType(int.class, String.class, int.class))
                    .asType(MethodType.methodType(int.class, Object.class, int.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }
    
    private static final int SENTINEL = -1;

    private PerfectHash() {}

    private static int defaultHash(String key, int entropy) {
        if (entropy == 0)
            return Math.abs(key.hashCode());

        int h = entropy;
        for (int i = 0; i < key.length(); i++) {
            h = 31 * h + (key.charAt(i) & 0xff);
        }
        return Math.abs(h);
    }

    private static int lookup(Object key, MethodHandle hasher, MethodHandle switcher, int numCases) throws Throwable {
        int hash = (int) hasher.invokeExact(key);
        return (int) switcher.invokeExact(hash % numCases, key);
    }

    private static int confirmKey(int switchIndex, Object key, Object savedKey, int index) {
        return key.equals(savedKey) ? index : SENTINEL;
    }

    private static int defaultIndex(int switchIndex, Object key) {
        return SENTINEL;
    }

    private static int defaultThrow(int switchIndex, Object key) {
        throw new IllegalStateException("Switch index out of bounds: " + switchIndex);
    }

    /**
     * TODO
     * @param keyset todo
     * @return todo
     */
    public static MethodHandle minimalPerfectHash(Set<String> keyset) {
        return minimalPerfectHash(keyset, MH_DEFAULT_HASH);
    }

    /**
     * TODO
     * @param keyset todo
     * @param hasher todo
     * @return todo
     */
    public static MethodHandle minimalPerfectHash(Set<?> keyset, MethodHandle hasher) {
        try {
            int[] sharedNextIndex = new int[1];
            return minimalPerfectHashR(keyset, hasher, 0.75D, 0, sharedNextIndex, (int) 1099511628211L);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    private static MethodHandle minimalPerfectHashR(Set<?> keyset, MethodHandle hasher, double loadFactor,
                                                    int entropy, int[] nextIndex, int entropyPrime) throws Throwable {

        // TODO limit on depth -> fallback to linear scan?

        int numKeys = (int) Math.ceil(keyset.size() * (1 / loadFactor));
        @SuppressWarnings({"unchecked", "rawtypes"})
        List<Object>[] keyDist = new List[numKeys];

        int chosenEntropy = entropy;
        while (true){
            for (Object key : keyset) {
                int hash = (int) hasher.invokeExact(key, chosenEntropy);
                if (hash < 0) {
                    throw new IllegalArgumentException("Hash must be positive: " + hash + ", for key: " + key);
                }
                int saveIndex = hash % numKeys;
                if (keyDist[saveIndex] == null) {
                    keyDist[saveIndex] = new ArrayList<>();
                }
                keyDist[saveIndex].add(key);
            }
            if (allKeysInSameBucket(keyDist)) {
                Arrays.fill(keyDist, null);
                chosenEntropy = chosenEntropy == 0 ? entropyPrime : chosenEntropy * entropyPrime;
            } else {
                break;
            }
        }

        MethodHandle[] targets = new MethodHandle[numKeys];

        for (int i = 0; i < numKeys; i++) {
            List<Object> keys = keyDist[i];
            if(keys == null) { // 0 keys in this slot (there were collisions elsewhere)
                targets[i] = MH_DEFAULT_INDEX;
            } else if (keys.size() == 1) { // no collision
                Object key = keys.get(0);
                targets[i] = MethodHandles.insertArguments(MH_CONFIRM, 2, key, nextIndex[0]++);
            } else { // collisions
                assert (keys.size() > 1);
                targets[i] = MethodHandles.dropArguments(
                    minimalPerfectHashR(Set.of(keys.toArray()), hasher, loadFactor, chosenEntropy, nextIndex, entropyPrime),
                    0,
                    int.class
                );
            }
        }

        MethodHandle switcher = MethodHandles.tableSwitch(MH_DEFAULT_THROW, targets);
        MethodHandle levelHasher = MethodHandles.insertArguments(hasher, 1, chosenEntropy);
        return MethodHandles.insertArguments(MH_LOOKUP, 1, levelHasher, switcher, numKeys);
    }

    private static boolean allKeysInSameBucket(List<Object>[] keyDist) {
        int numNulls = 0;
        for (int i = 0; i < keyDist.length; i++) {
            if (keyDist[i] == null)
                numNulls++;
        }
        return numNulls == (keyDist.length - 1);
    }

}
