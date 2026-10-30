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
        // (mismatched and off-heap accesses currently do not fold,
        // so tests for segment view, byte array view, and byte buffer view handles are excluded here)
        var testTemplate = Template.make("Type", "Init", "AccessMode", (String _, String _, String _) -> scope(
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

        List<TemplateToken> testTemplateTokens = Stream.of("getStable", "getStableAcquire")
            .mapMulti((String accessMode, Consumer<TemplateToken> sink) ->{
                sink.accept(testTemplate.asToken("Object",  "new Object()", accessMode));
                sink.accept(testTemplate.asToken("boolean", "true",         accessMode));
                sink.accept(testTemplate.asToken("byte",    "42",           accessMode));
                sink.accept(testTemplate.asToken("char",    "42",           accessMode));
                sink.accept(testTemplate.asToken("short",   "42",           accessMode));
                sink.accept(testTemplate.asToken("int",     "42",           accessMode));
                sink.accept(testTemplate.asToken("long",    "42",           accessMode));
                sink.accept(testTemplate.asToken("float",   "42",           accessMode));
                sink.accept(testTemplate.asToken("double",  "42",           accessMode));
            }).toList();

        return TestFrameworkClass.render(
                "compiler.c2.irTests.stable", "StableVarHandleTest_Case",
                Set.of("java.nio.*",
                       "java.lang.invoke.*"),
                comp.getEscapedClassPathOfCompiledClasses(),
                testTemplateTokens);
    }
}
