/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @requires vm.compiler2.enabled
 * @summary Check getStable folding for var handles
 * @library /test/lib /
 * @run driver compiler.c2.irTests.stable.StableVarHandleTest
 */

package compiler.c2.irTests.stable;

import compiler.lib.ir_framework.IR;
import compiler.lib.ir_framework.IRNode;
import compiler.lib.ir_framework.Test;
import compiler.lib.ir_framework.TestFramework;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class StableVarHandleTest {

    public static void main(String[] args) {
        TestFramework.run();
    }

    static class Carrier {
        static Object refStaticField = new Object();
        Object refField = new Object();
        static boolean booleanStaticField = true;
        boolean booleanField = true;
        static byte byteStaticField = 42;
        byte byteField = 42;
        static char charStaticField = 42;
        char charField = 42;
        static short shortStaticField = 42;
        short shortField = 42;
        static int intStaticField = 42;
        int intField = 42;
        static long longStaticField = 42;
        long longField = 42;
        static float floatStaticField = 42;
        float floatField = 42;
        static double doubleStaticField = 42;
        double doubleField = 42;
    }

    static final VarHandle VH_REF_INSTANCE_FIELD;
    static final VarHandle VH_REF_STATIC_FIELD;
    static final VarHandle VH_REF_ARRAY_ELEMENT;
    static final VarHandle VH_BOOLEAN_INSTANCE_FIELD;
    static final VarHandle VH_BOOLEAN_STATIC_FIELD;
    static final VarHandle VH_BOOLEAN_ARRAY_ELEMENT;
    static final VarHandle VH_BYTE_INSTANCE_FIELD;
    static final VarHandle VH_BYTE_STATIC_FIELD;
    static final VarHandle VH_BYTE_ARRAY_ELEMENT;
    static final VarHandle VH_CHAR_INSTANCE_FIELD;
    static final VarHandle VH_CHAR_STATIC_FIELD;
    static final VarHandle VH_CHAR_ARRAY_ELEMENT;
    static final VarHandle VH_SHORT_INSTANCE_FIELD;
    static final VarHandle VH_SHORT_STATIC_FIELD;
    static final VarHandle VH_SHORT_ARRAY_ELEMENT;
    static final VarHandle VH_INT_INSTANCE_FIELD;
    static final VarHandle VH_INT_STATIC_FIELD;
    static final VarHandle VH_INT_ARRAY_ELEMENT;
    static final VarHandle VH_LONG_INSTANCE_FIELD;
    static final VarHandle VH_LONG_STATIC_FIELD;
    static final VarHandle VH_LONG_ARRAY_ELEMENT;
    static final VarHandle VH_FLOAT_INSTANCE_FIELD;
    static final VarHandle VH_FLOAT_STATIC_FIELD;
    static final VarHandle VH_FLOAT_ARRAY_ELEMENT;
    static final VarHandle VH_DOUBLE_INSTANCE_FIELD;
    static final VarHandle VH_DOUBLE_STATIC_FIELD;
    static final VarHandle VH_DOUBLE_ARRAY_ELEMENT;

    static final Carrier CARRIER = new Carrier();
    static final Object[] REF_ARR = { new Object() };
    static final boolean[] BOOLEAN_ARR = { true };
    static final byte[] BYTE_ARR = { 42 };
    static final char[] CHAR_ARR = { 42 };
    static final short[] SHORT_ARR = { 42 };
    static final int[] INT_ARR = { 42 };
    static final long[] LONG_ARR = { 42 };
    static final float[] FLOAT_ARR = { 42 };
    static final double[] DOUBLE_ARR = { 42 };

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            VH_REF_INSTANCE_FIELD = lookup.findVarHandle(Carrier.class, "refField", Object.class);
            VH_REF_STATIC_FIELD = lookup.findStaticVarHandle(Carrier.class, "refStaticField", Object.class);
            VH_REF_ARRAY_ELEMENT = MethodHandles.arrayElementVarHandle(Object[].class);
            VH_BOOLEAN_INSTANCE_FIELD = lookup.findVarHandle(Carrier.class, "booleanField", boolean.class);
            VH_BOOLEAN_STATIC_FIELD = lookup.findStaticVarHandle(Carrier.class, "booleanStaticField", boolean.class);
            VH_BOOLEAN_ARRAY_ELEMENT = MethodHandles.arrayElementVarHandle(boolean[].class);
            VH_BYTE_INSTANCE_FIELD = lookup.findVarHandle(Carrier.class, "byteField", byte.class);
            VH_BYTE_STATIC_FIELD = lookup.findStaticVarHandle(Carrier.class, "byteStaticField", byte.class);
            VH_BYTE_ARRAY_ELEMENT = MethodHandles.arrayElementVarHandle(byte[].class);
            VH_CHAR_INSTANCE_FIELD = lookup.findVarHandle(Carrier.class, "charField", char.class);
            VH_CHAR_STATIC_FIELD = lookup.findStaticVarHandle(Carrier.class, "charStaticField", char.class);
            VH_CHAR_ARRAY_ELEMENT = MethodHandles.arrayElementVarHandle(char[].class);
            VH_SHORT_INSTANCE_FIELD = lookup.findVarHandle(Carrier.class, "shortField", short.class);
            VH_SHORT_STATIC_FIELD = lookup.findStaticVarHandle(Carrier.class, "shortStaticField", short.class);
            VH_SHORT_ARRAY_ELEMENT = MethodHandles.arrayElementVarHandle(short[].class);
            VH_INT_INSTANCE_FIELD = lookup.findVarHandle(Carrier.class, "intField", int.class);
            VH_INT_STATIC_FIELD = lookup.findStaticVarHandle(Carrier.class, "intStaticField", int.class);
            VH_INT_ARRAY_ELEMENT = MethodHandles.arrayElementVarHandle(int[].class);
            VH_LONG_INSTANCE_FIELD = lookup.findVarHandle(Carrier.class, "longField", long.class);
            VH_LONG_STATIC_FIELD = lookup.findStaticVarHandle(Carrier.class, "longStaticField", long.class);
            VH_LONG_ARRAY_ELEMENT = MethodHandles.arrayElementVarHandle(long[].class);
            VH_FLOAT_INSTANCE_FIELD = lookup.findVarHandle(Carrier.class, "floatField", float.class);
            VH_FLOAT_STATIC_FIELD = lookup.findStaticVarHandle(Carrier.class, "floatStaticField", float.class);
            VH_FLOAT_ARRAY_ELEMENT = MethodHandles.arrayElementVarHandle(float[].class);
            VH_DOUBLE_INSTANCE_FIELD = lookup.findVarHandle(Carrier.class, "doubleField", double.class);
            VH_DOUBLE_STATIC_FIELD = lookup.findStaticVarHandle(Carrier.class, "doubleStaticField", double.class);
            VH_DOUBLE_ARRAY_ELEMENT = MethodHandles.arrayElementVarHandle(double[].class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static Object testFoldInstanceField_ref() {
        return VH_REF_INSTANCE_FIELD.getStable(CARRIER);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static Object testFoldStaticField_ref() {
        return VH_REF_STATIC_FIELD.getStable();
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static Object testFoldArrayElement_ref() {
        return VH_REF_ARRAY_ELEMENT.getStable(REF_ARR, 0);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static Object testFoldInstanceFieldAcquire_ref() {
        return VH_REF_INSTANCE_FIELD.getStableAcquire(CARRIER);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static Object testFoldStaticFieldAcquire_ref() {
        return VH_REF_STATIC_FIELD.getStableAcquire();
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static Object testFoldArrayElementAcquire_ref() {
        return VH_REF_ARRAY_ELEMENT.getStableAcquire(REF_ARR, 0);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static boolean testFoldInstanceField_boolean() {
        return (boolean) VH_BOOLEAN_INSTANCE_FIELD.getStable(CARRIER);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static boolean testFoldStaticField_boolean() {
        return (boolean) VH_BOOLEAN_STATIC_FIELD.getStable();
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static boolean testFoldArrayElement_boolean() {
        return (boolean) VH_BOOLEAN_ARRAY_ELEMENT.getStable(BOOLEAN_ARR, 0);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static boolean testFoldInstanceFieldAcquire_boolean() {
        return (boolean) VH_BOOLEAN_INSTANCE_FIELD.getStableAcquire(CARRIER);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static boolean testFoldStaticFieldAcquire_boolean() {
        return (boolean) VH_BOOLEAN_STATIC_FIELD.getStableAcquire();
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static boolean testFoldArrayElementAcquire_boolean() {
        return (boolean) VH_BOOLEAN_ARRAY_ELEMENT.getStableAcquire(BOOLEAN_ARR, 0);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static byte testFoldInstanceField_byte() {
        return (byte) VH_BYTE_INSTANCE_FIELD.getStable(CARRIER);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static byte testFoldStaticField_byte() {
        return (byte) VH_BYTE_STATIC_FIELD.getStable();
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static byte testFoldArrayElement_byte() {
        return (byte) VH_BYTE_ARRAY_ELEMENT.getStable(BYTE_ARR, 0);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static byte testFoldInstanceFieldAcquire_byte() {
        return (byte) VH_BYTE_INSTANCE_FIELD.getStableAcquire(CARRIER);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static byte testFoldStaticFieldAcquire_byte() {
        return (byte) VH_BYTE_STATIC_FIELD.getStableAcquire();
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static byte testFoldArrayElementAcquire_byte() {
        return (byte) VH_BYTE_ARRAY_ELEMENT.getStableAcquire(BYTE_ARR, 0);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static char testFoldInstanceField_char() {
        return (char) VH_CHAR_INSTANCE_FIELD.getStable(CARRIER);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static char testFoldStaticField_char() {
        return (char) VH_CHAR_STATIC_FIELD.getStable();
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static char testFoldArrayElement_char() {
        return (char) VH_CHAR_ARRAY_ELEMENT.getStable(CHAR_ARR, 0);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static char testFoldInstanceFieldAcquire_char() {
        return (char) VH_CHAR_INSTANCE_FIELD.getStableAcquire(CARRIER);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static char testFoldStaticFieldAcquire_char() {
        return (char) VH_CHAR_STATIC_FIELD.getStableAcquire();
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static char testFoldArrayElementAcquire_char() {
        return (char) VH_CHAR_ARRAY_ELEMENT.getStableAcquire(CHAR_ARR, 0);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static short testFoldInstanceField_short() {
        return (short) VH_SHORT_INSTANCE_FIELD.getStable(CARRIER);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static short testFoldStaticField_short() {
        return (short) VH_SHORT_STATIC_FIELD.getStable();
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static short testFoldArrayElement_short() {
        return (short) VH_SHORT_ARRAY_ELEMENT.getStable(SHORT_ARR, 0);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static short testFoldInstanceFieldAcquire_short() {
        return (short) VH_SHORT_INSTANCE_FIELD.getStableAcquire(CARRIER);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static short testFoldStaticFieldAcquire_short() {
        return (short) VH_SHORT_STATIC_FIELD.getStableAcquire();
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static short testFoldArrayElementAcquire_short() {
        return (short) VH_SHORT_ARRAY_ELEMENT.getStableAcquire(SHORT_ARR, 0);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static int testFoldInstanceField_int() {
        return (int) VH_INT_INSTANCE_FIELD.getStable(CARRIER);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static int testFoldStaticField_int() {
        return (int) VH_INT_STATIC_FIELD.getStable();
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static int testFoldArrayElement_int() {
        return (int) VH_INT_ARRAY_ELEMENT.getStable(INT_ARR, 0);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static int testFoldInstanceFieldAcquire_int() {
        return (int) VH_INT_INSTANCE_FIELD.getStableAcquire(CARRIER);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static int testFoldStaticFieldAcquire_int() {
        return (int) VH_INT_STATIC_FIELD.getStableAcquire();
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static int testFoldArrayElementAcquire_int() {
        return (int) VH_INT_ARRAY_ELEMENT.getStableAcquire(INT_ARR, 0);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static long testFoldInstanceField_long() {
        return (long) VH_LONG_INSTANCE_FIELD.getStable(CARRIER);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static long testFoldStaticField_long() {
        return (long) VH_LONG_STATIC_FIELD.getStable();
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static long testFoldArrayElement_long() {
        return (long) VH_LONG_ARRAY_ELEMENT.getStable(LONG_ARR, 0);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static long testFoldInstanceFieldAcquire_long() {
        return (long) VH_LONG_INSTANCE_FIELD.getStableAcquire(CARRIER);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static long testFoldStaticFieldAcquire_long() {
        return (long) VH_LONG_STATIC_FIELD.getStableAcquire();
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static long testFoldArrayElementAcquire_long() {
        return (long) VH_LONG_ARRAY_ELEMENT.getStableAcquire(LONG_ARR, 0);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static float testFoldInstanceField_float() {
        return (float) VH_FLOAT_INSTANCE_FIELD.getStable(CARRIER);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static float testFoldStaticField_float() {
        return (float) VH_FLOAT_STATIC_FIELD.getStable();
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static float testFoldArrayElement_float() {
        return (float) VH_FLOAT_ARRAY_ELEMENT.getStable(FLOAT_ARR, 0);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static float testFoldInstanceFieldAcquire_float() {
        return (float) VH_FLOAT_INSTANCE_FIELD.getStableAcquire(CARRIER);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static float testFoldStaticFieldAcquire_float() {
        return (float) VH_FLOAT_STATIC_FIELD.getStableAcquire();
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static float testFoldArrayElementAcquire_float() {
        return (float) VH_FLOAT_ARRAY_ELEMENT.getStableAcquire(FLOAT_ARR, 0);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static double testFoldInstanceField_double() {
        return (double) VH_DOUBLE_INSTANCE_FIELD.getStable(CARRIER);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static double testFoldStaticField_double() {
        return (double) VH_DOUBLE_STATIC_FIELD.getStable();
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static double testFoldArrayElement_double() {
        return (double) VH_DOUBLE_ARRAY_ELEMENT.getStable(DOUBLE_ARR, 0);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static double testFoldInstanceFieldAcquire_double() {
        return (double) VH_DOUBLE_INSTANCE_FIELD.getStableAcquire(CARRIER);
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static double testFoldStaticFieldAcquire_double() {
        return (double) VH_DOUBLE_STATIC_FIELD.getStableAcquire();
    }

    @Test
    @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
    static double testFoldArrayElementAcquire_double() {
        return (double) VH_DOUBLE_ARRAY_ELEMENT.getStableAcquire(DOUBLE_ARR, 0);
    }
}
