package java.lang.invoke;

import jdk.internal.misc.SpeculationFence;
import jdk.internal.vm.annotation.TrustFinalFields;

/**
 * Condition to be used with VarHandle::getStable
 */
public final class Condition {
    /**
     * The non-default condition singleton
     */
    public static final Condition NON_DEFAULT = new Condition();

    // package-private for access from VarHandle implementation classes
    final SpeculationFence fence;

    private Condition() {
        fence = null;
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
}
