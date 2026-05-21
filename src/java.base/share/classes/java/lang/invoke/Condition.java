package java.lang.invoke;

import jdk.internal.misc.SpeculationFence;

/**
 * Condition to be used with VarHandle::getStable
 */
public abstract sealed class Condition {
    /**
     * The non-default condition singleton
     */
    public static final Condition NON_DEFAULT = new NonDefaultCondition();

    // package-private for access from VarHandle implementation classes
    final SpeculationFence fence;

    private Condition(SpeculationFence fence) {
        this.fence = fence;
    }

    /**
     * {@return a new create condition}
     * <p>
     * TODO
     */
    public static Condition initialized() {
        return new InitializedCondition();
    }

    /**
     * {@return a new create condition}
     * <p>
     * TODO
     */
    public static Condition resttable() {
        return new ResettableCondition();
    }

    /**
     * Trigger this condition
     *
     * @throws UnsupportedOperationException if triggering this condition is not supported
     * @throws IllegalStateException if this condition can not be triggered in its current state
     */
    public void trigger() {
        throw new UnsupportedOperationException();
    }

    private static final class NonDefaultCondition extends Condition {
        private NonDefaultCondition() {
            super(null);
        }
    }

    private static final class InitializedCondition extends Condition {
        private InitializedCondition() {
            super(new SpeculationFence());
        }

        @Override
        public synchronized void trigger() {
            // synchronize on this instance to avoid TOC-TOU between epoch check and bump
            if (fence.epoch() != 0) {
                throw new IllegalStateException("Already initialized");
            }
            fence.doFence();
        }
    }

    private static final class ResettableCondition extends Condition {
        private ResettableCondition() {
            super(new SpeculationFence());
        }

        @Override
        public void trigger() {
            fence.doFence();
        }
    }
}
