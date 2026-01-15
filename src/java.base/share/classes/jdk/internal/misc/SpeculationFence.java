package jdk.internal.misc;

/**
 * A speculation fence is used to invalidate JIT speculation
 */
public final class SpeculationFence {

    /**
     * Trigger this fence, signalling the JIT that it should discard all
     * speculative optimization that were guarded by this fence.
     */
    public void doFence() {
        Unsafe.internalDoFence(this);
    }
}
