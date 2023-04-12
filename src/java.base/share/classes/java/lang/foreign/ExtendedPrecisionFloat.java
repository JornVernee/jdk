package java.lang.foreign;

import jdk.internal.foreign.ExtendedPrecisionFloatImpl;

/**
 * carrier type for 80 bit IEEE 754 floats (opaque)
 */
public sealed interface ExtendedPrecisionFloat permits ExtendedPrecisionFloatImpl {
    //static ExtendedPrecisionFloat valueOf(double value) { ... }
    //double doubleValue();
}
