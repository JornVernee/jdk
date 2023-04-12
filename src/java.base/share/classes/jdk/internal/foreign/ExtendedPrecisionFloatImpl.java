package jdk.internal.foreign;

import java.lang.foreign.ExtendedPrecisionFloat;

/**
 * IEEE 754 extended precision float
 */
public record ExtendedPrecisionFloatImpl(long mantissa, long exponent) implements ExtendedPrecisionFloat {

    // slightly simplify code gen
    public static ExtendedPrecisionFloatImpl of(long mantissa, long exponent) {
        return new ExtendedPrecisionFloatImpl(mantissa, exponent);
    }

}
