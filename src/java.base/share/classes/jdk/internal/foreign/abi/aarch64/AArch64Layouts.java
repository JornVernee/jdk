package jdk.internal.foreign.abi.aarch64;

import jdk.internal.foreign.layout.ValueLayouts;

import java.lang.foreign.AddressLayout;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;

public interface AArch64Layouts {
    ValueLayout.OfBoolean C_BOOL = (ValueLayout.OfBoolean) ValueLayouts.valueLayout(boolean.class, ByteOrder.LITTLE_ENDIAN)
            .withClassifier(TypeClass.INTEGER);
    ValueLayout.OfByte C_CHAR = (ValueLayout.OfByte) ValueLayouts.valueLayout(byte.class, ByteOrder.LITTLE_ENDIAN)
            .withClassifier(TypeClass.INTEGER);
    ValueLayout.OfShort C_SHORT = (ValueLayout.OfShort) ValueLayouts.valueLayout(short.class, ByteOrder.LITTLE_ENDIAN)
            .withClassifier(TypeClass.INTEGER);
    ValueLayout.OfInt C_INT = (ValueLayout.OfInt) ValueLayouts.valueLayout(int.class, ByteOrder.LITTLE_ENDIAN)
            .withClassifier(TypeClass.INTEGER);
    ValueLayout.OfLong C_LONG_LONG = (ValueLayout.OfLong) ValueLayouts.valueLayout(long.class, ByteOrder.LITTLE_ENDIAN)
            .withClassifier(TypeClass.INTEGER);
    ValueLayout.OfFloat C_FLOAT = (ValueLayout.OfFloat) ValueLayouts.valueLayout(float.class, ByteOrder.LITTLE_ENDIAN)
            .withClassifier(TypeClass.FLOAT);
    ValueLayout.OfDouble C_DOUBLE = (ValueLayout.OfDouble) ValueLayouts.valueLayout(double.class, ByteOrder.LITTLE_ENDIAN)
            .withClassifier(TypeClass.FLOAT);
    AddressLayout C_POINTER = (AddressLayout) ValueLayouts.valueLayout(MemorySegment.class, ByteOrder.LITTLE_ENDIAN)
            .withClassifier(TypeClass.POINTER);

    static MemoryLayout linkerType(String name) {
        return switch (name) {
            case "_Bool" -> AArch64Layouts.C_BOOL;
            case "char" -> AArch64Layouts.C_CHAR;
            case "short" -> AArch64Layouts.C_SHORT;
            case "int" -> AArch64Layouts.C_INT;
            case "long long" -> AArch64Layouts.C_LONG_LONG;
            case "float" -> AArch64Layouts.C_FLOAT;
            case "double" -> AArch64Layouts.C_DOUBLE;
            case "void*" -> AArch64Layouts.C_POINTER;
            default -> throw new IllegalStateException("Unknown type: " + name);
        };
    }
}
