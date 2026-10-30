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
 * @run driver ${test.main.class}
 */

package compiler.c2.irTests.stable;

import compiler.lib.compile_framework.CompileFramework;
import compiler.lib.template_framework.Template;
import compiler.lib.template_framework.TemplateToken;
import compiler.lib.template_framework.library.TestFrameworkClass;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static compiler.lib.template_framework.Template.scope;

public class StableVarHandleTest {

    public static void main(String[] args) {
        CompileFramework comp = new CompileFramework();
        comp.addJavaSourceCode("compiler.c2.irTests.stable.StableVarHandleTest_Case", generate(comp));
        comp.compile();
        comp.invoke("compiler.c2.irTests.stable.StableVarHandleTest_Case", "main", new Object[] {new String[] {}});
    }

    private static String generate(CompileFramework comp) {
        // TODO add cases for MemorySegment and direct ByteBuffer
        var allTypeTemplate = Template.make("Type", "Init", "AccessMode", (String _, String _, String _) -> scope(
            """
            static class $Carrier {
                static #Type staticField = #Init;
                #Type field = #Init;
            }

            static final VarHandle $VH_INSTANCE_FIELD;
            static final VarHandle $VH_STATIC_FIELD;
            static final VarHandle $VH_ARRAY_ELEMENT;

            static {
                try {
                    MethodHandles.Lookup lookup = MethodHandles.lookup();
                    $VH_INSTANCE_FIELD = lookup.findVarHandle($Carrier.class, "field", #Type.class);
                    $VH_STATIC_FIELD = lookup.findStaticVarHandle($Carrier.class, "staticField", #Type.class);
                    $VH_ARRAY_ELEMENT = MethodHandles.arrayElementVarHandle(#Type[].class);
                } catch (ReflectiveOperationException e) {
                    throw new ExceptionInInitializerError(e);
                }
            }

            static final $Carrier $CARRIER = new $Carrier();
            static final #Type[] $ARR = { #Init };

            @Test
            @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
            static #Type $testFoldInstanceField() {
                return (#Type) $VH_INSTANCE_FIELD.#AccessMode($CARRIER);
            }

            @Test
            @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
            static #Type $testFoldStaticField() {
                return (#Type) $VH_STATIC_FIELD.#AccessMode();
            }

            @Test
            @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
            static #Type $testFoldArrayElement() {
                return (#Type) $VH_ARRAY_ELEMENT.#AccessMode($ARR, 0);
            }
            """));
        var viewPrimsOnlyTemplate = Template.make("Type", "Init", (String _, String _) -> scope(
            """
            static final VarHandle $VH_BYTE_ARR_VIEW
                = MethodHandles.byteArrayViewVarHandle(#Type[].class, ByteOrder.nativeOrder());
            static final VarHandle $VH_BYTE_BUFFER_VIEW
                = MethodHandles.byteBufferViewVarHandle(#Type[].class, ByteOrder.nativeOrder());

            static final byte[] $BYTE_ARR = new byte[8];
            static final ByteBuffer $BB;

            static {
                $VH_BYTE_ARR_VIEW.set($BYTE_ARR, 0, (#Type) #Init);
                $BB = ByteBuffer.allocate(8);
                $VH_BYTE_BUFFER_VIEW.set($BB, 0, (#Type) #Init);
            }

            @Test
            @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
            static #Type $testFoldByteArrayView() {
                return (#Type) $VH_BYTE_ARR_VIEW.getStable($BYTE_ARR, 0);
            }

            @Test
            @IR(failOn = { IRNode.LOAD, IRNode.MEMBAR })
            static #Type $testFoldByteBufferView() {
                return (#Type) $VH_BYTE_BUFFER_VIEW.getStable($BB, 0);
            }
            """));

        record TestCase(String type, String init, boolean isViewPrim) {}
        List<TemplateToken> testTemplateTokens = Stream.of(
                new TestCase("Object",  "new Object()", false),
                new TestCase("boolean", "true",         false),
                new TestCase("byte",    "42",           false),
                new TestCase("char",    "42",           true),
                new TestCase("short",   "42",           true),
                new TestCase("int",     "42",           true),
                new TestCase("long",    "42",           true),
                new TestCase("float",   "42",           true),
                new TestCase("double",  "42",           true)
        )
        .mapMulti((TestCase testCase, Consumer<TemplateToken> sink) -> {
            sink.accept(allTypeTemplate.asToken(testCase.type(), testCase.init(), "getStable"));
            sink.accept(allTypeTemplate.asToken(testCase.type(), testCase.init(), "getStableAcquire"));
            if (testCase.isViewPrim()) {
                sink.accept(viewPrimsOnlyTemplate.asToken(testCase.type(), testCase.init()));
            }
        })
        .toList();

        return TestFrameworkClass.render(
                "compiler.c2.irTests.stable", "StableVarHandleTest_Case",
                Set.of("java.nio.*",
                       "java.lang.invoke.*"),
                comp.getEscapedClassPathOfCompiledClasses(),
                testTemplateTokens);
    }
}
