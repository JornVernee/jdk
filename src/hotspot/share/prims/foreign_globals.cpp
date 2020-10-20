/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

#include "precompiled.hpp"
#include "foreign_globals.hpp"
#include "classfile/symbolTable.hpp"
#include "memory/resourceArea.hpp"
#include "runtime/fieldDescriptor.hpp"

#define FOREIGN_ABI "jdk/internal/foreign/abi/"

int ForeignGlobals::field_offset(InstanceKlass* cls, const char* fieldname, Symbol* sigsym) {
  Symbol* fieldnamesym = SymbolTable::new_symbol(fieldname, (int)strlen(fieldname));
  fieldDescriptor fd;
  bool success = cls->find_field(fieldnamesym, sigsym, false, &fd);
  assert(success, "Field not found");
  return fd.offset();
}

InstanceKlass* ForeignGlobals::find_InstanceKlass(const char* name, TRAPS) {
  Symbol* sym = SymbolTable::new_symbol(name, (int)strlen(name));
  Klass* k = SystemDictionary::resolve_or_null(sym, Handle(), Handle(), THREAD);
  assert(k != nullptr, "Can not find class: %s", name);
  return InstanceKlass::cast(k);
}

const ForeignGlobals& ForeignGlobals::instance() {
  static ForeignGlobals globals; // thread-safe lazy init-once (since C++11)
  return globals;
}

const ABIDescriptor ForeignGlobals::parseABIDescriptor(jobject jabi) {
  return instance().parseABIDescriptor_impl(jabi);
}
const BufferLayout ForeignGlobals::parseBufferLayout(jobject jlayout) {
  return instance().parseBufferLayout_impl(jlayout);
}

ForeignGlobals::ForeignGlobals() {
  Thread* current_thread = Thread::current();
  ResourceMark rm;

  // ABIDescriptor
  InstanceKlass* k_ABI = find_InstanceKlass(FOREIGN_ABI "ABIDescriptor", current_thread);
  const char* strVMSArray = "[[L" FOREIGN_ABI "VMStorage;";
  Symbol* symVMSArray = SymbolTable::new_symbol(strVMSArray, (int)strlen(strVMSArray));
  ABI.inputStorage_offset = field_offset(k_ABI, "inputStorage", symVMSArray);
  ABI.outputStorage_offset = field_offset(k_ABI, "outputStorage", symVMSArray);
  ABI.volatileStorage_offset = field_offset(k_ABI, "volatileStorage", symVMSArray);
  ABI.stackAlignment_offset = field_offset(k_ABI, "stackAlignment", vmSymbols::int_signature());
  ABI.shadowSpace_offset = field_offset(k_ABI, "shadowSpace", vmSymbols::int_signature());

  // VMStorage
  InstanceKlass* k_VMS = find_InstanceKlass(FOREIGN_ABI "VMStorage", current_thread);
  VMS.index_offset = field_offset(k_VMS, "index", vmSymbols::int_signature());

  // BufferLayout
  InstanceKlass* k_BL = find_InstanceKlass(FOREIGN_ABI "BufferLayout", current_thread);
  BL.size_offset = field_offset(k_BL, "size", vmSymbols::long_signature());
  BL.arguments_next_pc_offset = field_offset(k_BL, "arguments_next_pc", vmSymbols::long_signature());
  BL.stack_args_bytes_offset = field_offset(k_BL, "stack_args_bytes", vmSymbols::long_signature());
  BL.stack_args_offset = field_offset(k_BL, "stack_args", vmSymbols::long_signature());
  BL.input_type_offsets_offset = field_offset(k_BL, "input_type_offsets", vmSymbols::long_array_signature());
  BL.output_type_offsets_offset = field_offset(k_BL, "output_type_offsets", vmSymbols::long_array_signature());
}
