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

import com.sun.tools.jnativescan.RestrictedUse.NativeMethodDecl;
import com.sun.tools.jnativescan.RestrictedUse.RestrictedMethodRefs;

import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.util.*;

class RestrictedMethodFinder {

    // ct.sym uses this fake name for the restricted annotation instead
    // see make/langtools/src/classes/build/tools/symbolgenerator/CreateSymbols.java
    private static final String RESTRICTED_NAME = "Ljdk/internal/javac/Restricted+Annotation;";
    private static final List<String> RESTRICTED_MODULES = List.of("java.base");

    private final Map<MethodRef, Boolean> CACHE = new HashMap<>();
    private final ClassResolver classesToScan;
    private final MethodResolver methodResolver;

    private RestrictedMethodFinder(ClassResolver classesToScan, MethodResolver methodResolver) {
        this.classesToScan = classesToScan;
        this.methodResolver = methodResolver;
    }

    public static RestrictedMethodFinder create(ClassResolver classesToScan, MethodResolver methodResolver) throws JNativeScanFatalError, IOException {
        return new RestrictedMethodFinder(classesToScan, methodResolver);
    }

    public Map<ScannedModule, Map<ClassDesc, List<RestrictedUse>>> findAll() throws JNativeScanFatalError {
        Map<ScannedModule, Map<ClassDesc, List<RestrictedUse>>> restrictedMethods = new HashMap<>();
        classesToScan.forEach((_, info) -> {
            ClassModel classModel = info.model();
            List<RestrictedUse> perClass = new ArrayList<>();
            boolean isInterface = classModel.flags().has(AccessFlag.INTERFACE);
            classModel.methods().forEach(methodModel -> {
                if (methodModel.flags().has(AccessFlag.NATIVE)) {
                    perClass.add(new NativeMethodDecl(MethodRef.ofModel(methodModel, isInterface)));
                } else {
                    Set<MethodRef> perMethod = new HashSet<>();
                    methodModel.code()
                        .ifPresent(code -> {
                            code.forEach(e -> {
                                switch (e) {
                                    case InvokeInstruction invoke -> {
                                        if (isRestrictedMethod(invoke.method())) {
                                            perMethod.add(MethodRef.ofMethodRefEntry(invoke.method()));
                                        }
                                    }
                                    default -> {
                                    }
                                }
                            });
                        });
                    if (!perMethod.isEmpty()) {
                        perClass.add(new RestrictedMethodRefs(MethodRef.ofModel(methodModel, isInterface),
                                Set.copyOf(perMethod)));
                    }
                }
            });
            if (!perClass.isEmpty()) {
                ScannedModule scannedModule = new ScannedModule(info.jarPath(), info.moduleName());
                restrictedMethods.computeIfAbsent(scannedModule, _ -> new HashMap<>())
                        .put(classModel.thisClass().asSymbol(), perClass);
            }
        });
        return restrictedMethods;
    }

    private boolean isRestrictedMethod(MemberRefEntry method) throws JNativeScanFatalError {
        return switch (method) {
            case MethodRefEntry mre ->
                    isRestrictedMethod(mre.owner().asSymbol(), mre.name().stringValue(), mre.typeSymbol(), false);
            case InterfaceMethodRefEntry mre ->
                    isRestrictedMethod(mre.owner().asSymbol(), mre.name().stringValue(), mre.typeSymbol(), true);
            default -> throw new IllegalStateException("Unexpected type: " + method);
        };
    }

    private boolean isRestrictedMethod(ClassDesc owner, String name, MethodTypeDesc type, boolean isInterface) throws JNativeScanFatalError {
        try {
            return CACHE.computeIfAbsent(new MethodRef(owner, name, type, isInterface), methodRef -> {
                if (methodRef.owner().isArray()) {
                    // no restricted methods in arrays atm, and we can't look them up since they have no class file
                    return false;
                }
                MethodModel method = methodResolver.resolve(methodRef);
                return hasRestrictedAnnotation(method);
            });
        } catch (IllegalStateException e) {
            throw ((JNativeScanFatalError) e.getCause());
        }
    }

    private static boolean hasRestrictedAnnotation(MethodModel method) {
        return method.findAttribute(Attributes.runtimeVisibleAnnotations())
                .map(rva -> rva.annotations().stream().anyMatch(ann ->
                        ann.className().stringValue().equals(RESTRICTED_NAME)))
                .orElse(false);
    }
}
