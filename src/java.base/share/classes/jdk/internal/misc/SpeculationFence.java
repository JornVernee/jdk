package jdk.internal.misc;

/**
 * A speculation fence is used to invalidate JIT speculation
 */
public final class SpeculationFence {
    private static final Unsafe U = Unsafe.getUnsafe();

    private static final long EPOCH_OFFSET = U.objectFieldOffset(SpeculationFence.class, "epoch");

    private long epoch = 0; // accessed by VM

    /**
     * Trigger this fence, signalling the JIT that it should discard all
     * speculative optimization that were guarded by this fence.
     */
    public void doFence() {
        long oldEpoch = U.getAndAddInt(this, EPOCH_OFFSET, 1);
        if (oldEpoch > 0) { // only an initialization or reset triggers the fence
            Unsafe.internalDoFence(this); // will bump epoch
        }
    }

    /**
     * {@return the current epoch of this fence}
     */
    public long epoch() {
        return U.getIntVolatile(this, EPOCH_OFFSET);
    }
}
