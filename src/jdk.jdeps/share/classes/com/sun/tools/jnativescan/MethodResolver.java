/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.sun.tools.jnativescan;

import java.lang.classfile.AccessFlags;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.*;

import static java.lang.constant.ConstantDescs.*;

public class MethodResolver {

    private record NameAndType(String name, MethodTypeDesc type) {}
    private final Map<NameAndType, MethodModel> publicNonStaticObjectMethods;
    private final ClassResolver classResolver;

    private MethodResolver(ClassResolver classResolver, Map<NameAndType, MethodModel> publicNonStaticObjectMethods) {
        this.classResolver = classResolver;
        this.publicNonStaticObjectMethods = publicNonStaticObjectMethods;
    }

    public static MethodResolver create(ClassResolver classResolver) {
        ClassModel objectClassModel = classResolver.lookup(CD_Object)
                .orElseThrow(() -> new JNativeScanFatalError("Can not find java/lang/Object class")).model();

        Map<NameAndType, MethodModel> publicNonStaticObjectMethods = new HashMap<>();
        objectClassModel.methods().forEach(methodModel -> {
            AccessFlags flags = methodModel.flags();
            if (flags.has(AccessFlag.PUBLIC) && !flags.has(AccessFlag.STATIC)) {
                String methodName = methodModel.methodName().stringValue();
                MethodTypeDesc type = methodModel.methodTypeSymbol();
                publicNonStaticObjectMethods.put(new NameAndType(methodName, type), methodModel);
            }
        });

        return new MethodResolver(classResolver, publicNonStaticObjectMethods);
    }

    public MethodModel resolve(MethodRef ref) {
        return ref.isInterface() ? resolveInterfaceMethod(ref) : resolveClassMethod(ref);
    }

    // https://docs.oracle.com/javase/specs/jvms/se22/html/jvms-5.html#jvms-5.4.3.3
    private MethodModel resolveClassMethod(MethodRef ref) throws JNativeScanFatalError {
        ClassDesc current = ref.owner();
        do {
            ClassModel classModel = findClass(current);
            if (isSigPolyClass(current)) {
                Optional<MethodModel> methodModelOpt = findMethodByName(classModel, ref.name());
                if (methodModelOpt.isPresent()) {
                    MethodModel methodModel = methodModelOpt.get();
                    if (hasSigPolyFlags(methodModel)) {
                        return methodModel;
                    }
                }
            }

            Optional<MethodModel> methodModelOpt = findMethodByNameAndType(classModel, ref.name(), ref.type());
            if (methodModelOpt.isPresent()) {
                return methodModelOpt.get();
            }

            current = classModel.superclass().map(ClassEntry::asSymbol).orElse(null);
        } while (current != null);

        ClassModel rootModel = findClass(ref.owner());
        MethodModel model = maximallySpecificMethods(rootModel, ref);
        if (model != null) {
            return model;
        }
        model = findFirstNonPrivateNonStatic(rootModel, ref);
        if (model != null) {
            return model;
        }

        throw new JNativeScanFatalError("Can not resolve method reference: " + ref);
    }

    // https://docs.oracle.com/javase/specs/jvms/se22/html/jvms-2.html#jvms-2.9.3
    private static boolean isSigPolyClass(ClassDesc holder) {
        // class checked separately
        return holder.equals(CD_MethodHandle) || holder.equals(CD_VarHandle);
    }

    private static boolean hasSigPolyFlags(MethodModel methodModel) {
        MethodTypeDesc type = methodModel.methodTypeSymbol();
        AccessFlags flags = methodModel.flags();
        return type.parameterCount() == 1
                && type.parameterList().get(0).equals(CD_Object.arrayType())
                && flags.has(AccessFlag.VARARGS)
                && flags.has(AccessFlag.NATIVE);
    }

    // https://docs.oracle.com/javase/specs/jvms/se22/html/jvms-5.html#jvms-5.4.3.4
    private MethodModel resolveInterfaceMethod(MethodRef ref) {
        ClassModel classModel = findClass(ref.owner());
        Optional<MethodModel> methodModelOpt = findMethodByNameAndType(classModel, ref.name(), ref.type());
        if (methodModelOpt.isPresent()) {
            return methodModelOpt.get();
        }
        MethodModel model = publicNonStaticObjectMethods.get(new NameAndType(ref.name(), ref.type()));
        if (model != null) {
            return model;
        }
        model = maximallySpecificMethods(classModel, ref);
        if (model != null) {
            return model;
        }

        model = findFirstNonPrivateNonStatic(classModel, ref);
        if (model != null) {
            return model;
        }
        throw new JNativeScanFatalError("Can not resolve interface method reference: " + ref);
    }

    // super interfaces of this class or a super class
    private List<ClassDesc> allImmediateSuperInterfaces(ClassModel root) {
        List<ClassDesc> result = new ArrayList<>();
        ClassModel current = root;
        do {
            current.interfaces().stream().map(ClassEntry::asSymbol).forEach(result::add);
            current = current.superclass().map(ClassEntry::asSymbol).map(this::findClass).orElse(null);
        } while (current != null);
        return result;
    }

    private MethodModel maximallySpecificMethods(ClassModel root, MethodRef ref) {
        Deque<ClassDesc> toScan = new ArrayDeque<>(allImmediateSuperInterfaces(root));
        MethodModel result = null;
        while (!toScan.isEmpty()) {
            ClassDesc desc = toScan.poll();
            ClassModel classModel = findClass(desc);
            Optional<MethodModel> method = findMethodByNameAndType(classModel, ref.name(), ref.type());
            if (method.isPresent()) {
                MethodModel methodModel = method.get();
                if (!methodModel.flags().has(AccessFlag.ABSTRACT)) {
                    if (result != null) {
                        // mimic what VM does in this case
                        throw new IncompatibleClassChangeError("Conflicting default methods: " + result + " " + methodModel);
                    }
                    result = methodModel;
                }
            } else {
                classModel.interfaces().stream().map(ClassEntry::asSymbol).forEach(toScan::offer);
            }
        }
        return result;
    }

    private MethodModel findFirstNonPrivateNonStatic(ClassModel root, MethodRef ref) {
        Deque<ClassDesc> toScan = new ArrayDeque<>(allImmediateSuperInterfaces(root));
        while (!toScan.isEmpty()) {
            ClassDesc desc = toScan.poll();
            ClassModel classModel = findClass(desc);
            Optional<MethodModel> method = findMethodByNameAndType(classModel, ref.name(), ref.type());
            if (method.isPresent()) {
                MethodModel methodModel = method.get();
                if (!methodModel.flags().has(AccessFlag.PRIVATE) && !methodModel.flags().has(AccessFlag.STATIC)) {
                    return methodModel;
                }
            } else {
                classModel.interfaces().stream().map(ClassEntry::asSymbol).forEach(toScan::offer);
            }
        }
        return null;
    }

    private ClassModel findClass(ClassDesc desc) {
        Optional<ClassResolver.Info> classInfo = classResolver.lookup(desc);
        return classInfo
                .orElseThrow(() ->
                        new JNativeScanFatalError("Can not resolve class: " + desc))
                .model();
    }

    private static Optional<MethodModel> findMethodByName(ClassModel classModel, String name) {
        return classModel.methods().stream()
                .filter(m -> m.methodName().stringValue().equals(name))
                .findFirst();
    }

    private static Optional<MethodModel> findMethodByNameAndType(ClassModel classModel, String name, MethodTypeDesc type) {
        return classModel.methods().stream()
                .filter(m -> m.methodName().stringValue().equals(name)
                        && m.methodType().stringValue().equals(type.descriptorString()))
                .findFirst();
    }
}
