/*
 * Copyright (c)0 Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA10-1301 USA.
 *
 * Please contact Oracle, Oracle Parkway, Redwood Shores, CA65 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.lang.invoke;

import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.ConstantDynamic;
import jdk.internal.org.objectweb.asm.Handle;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.Type;

import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.invoke.LambdaForm.BasicType.basicType;
import static java.lang.invoke.MethodType.methodType;
import static jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_FRAMES;
import static jdk.internal.org.objectweb.asm.ClassWriter.COMPUTE_MAXS;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_FINAL;
import static jdk.internal.org.objectweb.asm.Opcodes.ACC_PUBLIC;
import static jdk.internal.org.objectweb.asm.Opcodes.ALOAD;
import static jdk.internal.org.objectweb.asm.Opcodes.H_INVOKESTATIC;
import static jdk.internal.org.objectweb.asm.Opcodes.INVOKESPECIAL;
import static jdk.internal.org.objectweb.asm.Opcodes.INVOKEVIRTUAL;
import static jdk.internal.org.objectweb.asm.Opcodes.RETURN;

/**
 * This is an implementation helper class for
 * {@link MethodHandles.Lookup#wrapAsFunctionalInterface(MethodHandle, Class)}
 */
public class InterfaceWrapperClassFactory {

    private static final ConstantDynamic LOAD_CLASS_DATA_CONDY;

    private static final String INTRN_OBJECT = Type.getInternalName(Object.class);
    private static final String INTRN_THROWABLE = Type.getInternalName(Throwable.class);
    private static final String INTRN_METHODHANDLE = Type.getInternalName(MethodHandle.class);

    static {
        MethodType bsmType = methodType(Object.class, MethodHandles.Lookup.class, String.class, Class.class);
        Handle bsm = new Handle(H_INVOKESTATIC, Type.getInternalName(MethodHandles.class), "classData",
                                bsmType.descriptorString(), false);
        LOAD_CLASS_DATA_CONDY = new ConstantDynamic("load_classData", MethodHandle.class.descriptorString(), bsm);
    }


    private InterfaceWrapperClassFactory() {}

    // Used to ensure that each spun class name is unique
    private static final AtomicInteger counter = new AtomicInteger(0);

    /**
     * Generates an class that extends the given functional interface, implementing the given
     * single abstract method (name &amp; type) with a call to a method handle that is loaded from
     * class data.
     *
     * @see MethodHandles.Lookup#classData(MethodHandles.Lookup, String, Class)
     *
     * @param functionalInterface the interface class
     * @param samName the name of the single abstract method
     * @param samType the type of the single abstract method
     * @return the bytes of the generated class
     */
    public static byte[] generateClassFor(Class<?> functionalInterface, String samName, MethodType samType) {
        String implName = uniqueNameForWrapper(functionalInterface);

        ClassWriter cw = new ClassWriter(COMPUTE_MAXS | COMPUTE_FRAMES);
        cw.visit(Opcodes.V15,
                ACC_FINAL + Opcodes.ACC_SUPER,
                implName,
                null,
                INTRN_OBJECT,
                new String[]{Type.getInternalName(functionalInterface)});

        addConstructor(cw);
        addNewRecordMethod(cw, samName, samType);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static String uniqueNameForWrapper(Class<?> functionalInterface) {
        return functionalInterface.getName().replace('.', '/') + "$$Wrapper$$" + counter.incrementAndGet();
    }

    private static void addConstructor(ClassWriter cw) {
        MethodVisitor mv = cw.visitMethod(0, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, INTRN_OBJECT, "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void addNewRecordMethod(ClassWriter cw, String samName, MethodType methodType) {
        String methodDesc = methodType.toMethodDescriptorString();
        // override
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC + ACC_FINAL,
                samName,
                methodDesc,
                null,
                new String[]{INTRN_THROWABLE});
        mv.visitCode();
        mv.visitLdcInsn(LOAD_CLASS_DATA_CONDY);
        for (int i = 0; i < methodType.parameterCount(); i++) {
            mv.visitVarInsn(loadInsn(methodType.parameterType(i)), i + 1); // +1 to skip 'this'
        }
        mv.visitMethodInsn(INVOKEVIRTUAL, INTRN_METHODHANDLE, "invokeExact", methodDesc, false);
        mv.visitInsn(returnIns(methodType.returnType()));
        mv.visitMaxs(-1, -1);
        mv.visitEnd();
    }

    private static int returnIns(Class<?> type) {
        return switch (basicType(type)) {
            case I_TYPE -> Opcodes.IRETURN;
            case J_TYPE -> Opcodes.LRETURN;
            case F_TYPE -> Opcodes.FRETURN;
            case D_TYPE -> Opcodes.DRETURN;
            case L_TYPE -> Opcodes.ARETURN;
            case V_TYPE -> Opcodes.RETURN;
        };
    }

    private static int loadInsn(Class<?> type) throws InternalError {
        return switch (basicType(type)) {
            case I_TYPE -> Opcodes.ILOAD;
            case J_TYPE -> Opcodes.LLOAD;
            case F_TYPE -> Opcodes.FLOAD;
            case D_TYPE -> Opcodes.DLOAD;
            case L_TYPE -> Opcodes.ALOAD;
            case V_TYPE -> throw new IllegalArgumentException("Can not load void");
        };
    }
}
