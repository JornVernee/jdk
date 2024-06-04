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
package com.sun.tools.jscan;

import java.io.IOException;
import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.InterfaceMethodRefEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.constantpool.MethodRefEntry;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

class RestrictedMethodFinder {

    private static final ClassDesc RESTRICTED_DESC = ClassDesc.of("jdk.internal.javac.Restricted");

    private final Map<MethodRef, Boolean> CACHE = new HashMap<>();
    private final FileSystem jrtfs;
    private final Runtime.Version version;

    public RestrictedMethodFinder(Runtime.Version version) {
        this.jrtfs = FileSystems.getFileSystem(URI.create("jrt:/"));
        this.version = version;
    }

    public Map<ClassDesc, List<RestrictedUse>> findRestrictedMethodReferences(Path jar) {
        Map<ClassDesc, List<RestrictedUse>> restrictedMethods = new HashMap<>();
        forEachClassFile(jar, model -> {
            List<RestrictedUse> perClass = new ArrayList<>();
            model.methods().forEach(method -> {
                if (method.flags().has(AccessFlag.NATIVE)) {
                    perClass.add(new RestrictedUse.NativeMethodDecl(MethodRef.ofModel(method)));
                } else {
                    Set<MethodRef> perMethod = new HashSet<>();
                    method.code()
                            .ifPresent(code -> {
                                code.forEach(e -> {
                                    switch (e) {
                                        case InvokeInstruction invoke -> {
                                            if (isRestrictedMethod(invoke.method())) {
                                                perMethod.add(MethodRef.ofMethodRef(invoke.method()));
                                            }
                                        }
                                        default -> {
                                        }
                                    }
                                });
                            });
                    if (!perMethod.isEmpty()) {
                        perClass.add(new RestrictedUse.RestrictedMethodRefs(MethodRef.ofModel(method), Set.copyOf(perMethod)));
                    }
                }
            });
            if (!perClass.isEmpty()) {
                restrictedMethods.put(model.thisClass().asSymbol(), perClass);
            }
        });
        return restrictedMethods;
    }

    private boolean isRestrictedMethod(MemberRefEntry method) {
        return switch (method) {
            case MethodRefEntry mre ->
                    isRestrictedMethod(mre.owner().asSymbol(), mre.name().stringValue(), mre.typeSymbol());
            case InterfaceMethodRefEntry mre ->
                    isRestrictedMethod(mre.owner().asSymbol(), mre.name().stringValue(), mre.typeSymbol());
            default -> throw new IllegalStateException("Unexpected type: " + method);
        };
    }

    public boolean isRestrictedMethod(ClassDesc owner, String name, MethodTypeDesc type) {
        return CACHE.computeIfAbsent(new MethodRef(owner, name, type), k -> {
            // file path we need looks like /packages/<package name>/<module name>/com/foo/Widget.class
            Path packagePath = jrtfs.getPath("/packages/" + k.owner().packageName());
            if (!Files.exists(packagePath)) {
                return false; // not a JDK package. Can not be restricted
            }

            Path moduleRoot;
            // infer module name
            try (Stream<Path> modules = Files.list(packagePath)) {
                moduleRoot = modules.findAny().orElseThrow();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            String ownerDescriptor = k.owner().descriptorString();
            String ownerBinaryName = ownerDescriptor.substring(1, ownerDescriptor.length() - 1);
            Path classFile = moduleRoot.resolve(ownerBinaryName + ".class");

            ClassModel classModel;
            try {
                classModel = ClassFile.of().parse(classFile);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            MethodModel method = classModel.methods().stream()
                    .filter(m -> m.methodName().stringValue().equals(k.name())
                        && m.methodType().stringValue().equals(k.type().descriptorString()))
                    .findFirst()
                    .orElseThrow();

            return method.findAttribute(Attributes.runtimeVisibleAnnotations())
                    .map(rva -> rva.annotations().stream().anyMatch(ann -> ann.classSymbol().equals(RESTRICTED_DESC)))
                    .orElse(false);
        });
    }

    private void forEachClassFile(Path jarFile, Consumer<ClassModel> action) {
        try (JarFile jf = new JarFile(jarFile.toFile(), false, ZipFile.OPEN_READ, version)) {
            jf.versionedStream().forEach(je -> {
                if (je.getName().endsWith(".class")) {
                    try {
                        ClassModel model = ClassFile.of().parse(jf.getInputStream(je).readAllBytes());
                        action.accept(model);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
