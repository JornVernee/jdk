package jdk.internal.foreign.abi.fallback;

import java.lang.foreign.Linker;

public enum TypeClass implements Linker.Classifier {
    UINT8,
    SINT8,
    SINT16,
    UINT16,
    SINT32,
    SINT64,
    FLOAT,
    DOUBLE,
    POINTER
}
