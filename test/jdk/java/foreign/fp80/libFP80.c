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

#include "export.h"

struct S {
    long double x;
};

EXPORT long double from_double(double d) {
    return d;
}

EXPORT double to_double(long double ld) {
    return ld;
}

EXPORT struct S from_double_struct(double d) {
    struct S s;
    s.x = d;
    return s;
}

EXPORT double to_double_struct(struct S s) {
    return s.x;
}

EXPORT double upcall(double d, long double(*cb)(long double)) {
    return cb(d);
}

EXPORT double upcall_struct(double d, struct S(*cb)(struct S)) {
    struct S s;
    s.x = d;
    return cb(s).x;
}