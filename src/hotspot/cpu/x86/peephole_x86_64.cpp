/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 *
 */
#include "precompiled.hpp"

#ifdef COMPILER2

#include "peephole_x86_64.hpp"
#include "adfiles/ad_x86.hpp"

// This function transforms the shapes
// mov d, s1; add d, s2 into
// lea d, [s1 + s2]     and
// mov d, s1; shl d, s2 into
// lea d, [s1 << s2]    with s2 = 1, 2, 3
bool lea_coalesce_helper(Block* block, int block_index, PhaseCFG* cfg_, PhaseRegAlloc* ra_,
                         MachNode* (*new_root)(), uint inst0_rule, bool imm) {
  MachNode* inst0 = block->get_node(block_index)->as_Mach();
  assert(inst0->rule() == inst0_rule, "sanity");

  OptoReg::Name dst = ra_->get_reg_first(inst0);
  MachNode* inst1 = nullptr;
  OptoReg::Name src1 = OptoReg::Bad;

  if (inst0->in(1)->is_MachSpillCopy()) {
    OptoReg::Name in = ra_->get_reg_first(inst0->in(1)->in(1));
    if (OptoReg::is_reg(in) && OptoReg::as_VMReg(in)->is_Register()) {
      inst1 = inst0->in(1)->as_Mach();
      src1 = in;
    }
  }
  if (inst1 == nullptr) {
    return false;
  }
  assert(dst != src1, "");

  // Only coalesce if inst1 is immediately followed by inst0
  // Can be improved for more general cases
  if (block_index < 1 || block->get_node(block_index - 1) != inst1) {
    return false;
  }
  int inst1_index = block_index - 1;
  Node* inst2;
  if (imm) {
    inst2 = nullptr;
  } else {
    inst2 = inst0->in(2);
    if (inst2 == inst1) {
      inst2 = inst2->in(1);
    }
  }

  // See VM_Version::supports_fast_3op_lea()
  if (!imm) {
    Register rsrc1 = OptoReg::as_VMReg(src1)->as_Register();
    Register rsrc2 = OptoReg::as_VMReg(ra_->get_reg_first(inst2))->as_Register();
    if ((rsrc1 == rbp || rsrc1 == r13) && (rsrc2 == rbp || rsrc2 == r13)) {
      return false;
    }
  }

  // Go down the block to find the output proj node (the flag output) of inst0
  int proj_index = -1;
  Node* proj = nullptr;
  for (uint pos = block_index + 1; pos < block->number_of_nodes(); pos++) {
    Node* curr = block->get_node(pos);
    if (curr->is_MachProj() && curr->in(0) == inst0) {
      proj_index = pos;
      proj = curr;
      break;
    }
  }
  assert(proj != nullptr, "");
  // If some node uses the flag, cannot remove
  if (proj->outcnt() > 0) {
    return false;
  }

  MachNode* root = new_root();
  // Assign register for the newly allocated node
  ra_->set_oop(root, ra_->is_oop(inst0));
  ra_->set_pair(root->_idx, ra_->get_reg_second(inst0), ra_->get_reg_first(inst0));

  // Set input and output for the node
  root->add_req(inst0->in(0));
  root->add_req(inst1->in(1));
  // No input for constant after matching
  if (!imm) {
    root->add_req(inst2);
  }
  inst0->replace_by(root);
  proj->set_req(0, inst0);

  // Initialize the operand array
  root->_opnds[0] = inst0->_opnds[0]->clone();
  root->_opnds[1] = inst0->_opnds[1]->clone();
  root->_opnds[2] = inst0->_opnds[2]->clone();

  // Modify the block
  inst0->set_removed();
  inst1->set_removed();
  block->remove_node(proj_index);
  block->remove_node(block_index);
  block->remove_node(inst1_index);
  block->insert_node(root, block_index - 1);

  // Modify the CFG
  cfg_->map_node_to_block(inst0, nullptr);
  cfg_->map_node_to_block(inst1, nullptr);
  cfg_->map_node_to_block(proj, nullptr);
  cfg_->map_node_to_block(root, block);

  return true;
}

bool Peephole::lea_coalesce_reg(Block* block, int block_index, PhaseCFG* cfg_, PhaseRegAlloc* ra_,
                                MachNode* (*new_root)(), uint inst0_rule) {
  return lea_coalesce_helper(block, block_index, cfg_, ra_, new_root, inst0_rule, false);
}

bool Peephole::lea_coalesce_imm(Block* block, int block_index, PhaseCFG* cfg_, PhaseRegAlloc* ra_,
                                MachNode* (*new_root)(), uint inst0_rule) {
  return lea_coalesce_helper(block, block_index, cfg_, ra_, new_root, inst0_rule, true);
}

enum StatusFlags {
  Flag_OF, Flag_CF, Flag_SF, Flag_ZF, Flag_PF, Flag_AF
};

static bool is_cmpOp(MachOper* oper) {
  int opcode = oper->opcode();
  return opcode == CMPOP || opcode == CMPOPU || opcode == CMPOPUCF || opcode == CMPOPUCF2;
}

static BoolTest::mask bool_test_for(MachOper* oper) {
  switch (oper->opcode()) {
    case CMPOP:     return static_cast<cmpOpOper*>(oper)->bool_test();
    case CMPOPU:    return static_cast<cmpOpUOper*>(oper)->bool_test();
    case CMPOPUCF:  return static_cast<cmpOpUCFOper*>(oper)->bool_test();
    case CMPOPUCF2: return static_cast<cmpOpUCF2Oper*>(oper)->bool_test();
    default: ShouldNotReachHere(); // should be compare op
  }
}

static int flags_use_mask_for(MachNode* n) {
  // iterate all operands until we find the first cmpOp
  for (int i = n->oper_input_base(); i < n->num_opnds(); i++) {
    if (is_cmpOp(n->_opnds[i])) {
      switch (bool_test_for(n->_opnds[i])) {
        case  BoolTest::eq:
        case  BoolTest::ne:
          return Flag_ZF;

        case  BoolTest::lt:
        case  BoolTest::ge:
          return Flag_SF | Flag_OF;

        case  BoolTest::le:
        case  BoolTest::gt:
          return Flag_ZF | Flag_SF | Flag_OF;

        case  BoolTest::overflow:
        case  BoolTest::no_overflow:
          return Flag_OF;

        default: ShouldNotReachHere();
      }
    }
  }
  return 0;
}

static int flags_def_mask_for(MachNode* node, bool test_is_long) {
  if (test_is_long) {
    switch (node->rule()) {
      case andL_rReg_rule:
      case andL_rReg_imm_rule:
      case andL_rReg_mem_rule:
      case andL_mem_rReg_rule:
      case andL_mem_imm_rule:
      case orL_rReg_rule:
      case orL_rReg_imm_rule:
      case orL_rReg_mem_rule:
      case orL_mem_rReg_rule:
      case orL_mem_imm_rule:
      case xorL_rReg_imm_rule:
      case xorL_rReg_mem_rule:
      case xorL_mem_rReg_rule:
      case xorL_mem_imm_rule:
        return Flag_OF | Flag_CF | Flag_SF | Flag_ZF | Flag_PF;
    }
  } else {
    switch (node->rule()) {
      case andI_rReg_rule:
      case andI_rReg_imm_rule:
      case andI_rReg_mem_rule:
      case andI_mem_rReg_rule:
      case andI_mem_imm_rule:
      case orI_rReg_rule:
      case orI_rReg_imm_rule:
      case orI_rReg_mem_rule:
      case orI_mem_rReg_rule:
      case orI_mem_imm_rule:
      case xorI_rReg_imm_rule:
      case xorI_rReg_mem_rule:
      case xorI_mem_rReg_rule:
      case xorI_mem_imm_rule:
        return Flag_OF | Flag_CF | Flag_SF | Flag_ZF | Flag_PF;
    }
  }
  return 0;
}

bool Peephole::test_coalesce(Block* block, int block_index, PhaseCFG* cfg_, PhaseRegAlloc* ra_,
                             MachNode* (*new_root)(), uint inst0_rule) {
  MachNode* test = block->get_node(block_index)->as_Mach();
  assert(test->rule() == inst0_rule, "sanity");

  // look for pattern:
  //
  // inst1_1 (sets status flag)
  // MachProj
  // inst0 (testI_reg or testL_reg)
  // ... (other things can be scheduled here, as long as they don't kill the status register)
  // user (uses status flag)

  Node* prev = test->in(1);
  if (!prev->is_Mach()
      || !block->get_node(block_index - 1)->is_MachProj()
      || block->get_node(block_index - 2) != prev) {
    return false;
  }

  // find users
  int user_use_mask = 0;
  for (DUIterator_Fast imax, i = test->fast_outs(imax); i < imax; i++) {
    MachNode* user = test->fast_out(i)->as_Mach();
    user_use_mask |= flags_use_mask_for(user);
  }

  bool test_is_long = inst0_rule == testL_reg_rule;
  int prev_def_mask = flags_def_mask_for(prev->as_Mach(), test_is_long);

  bool prev_defs_flags = (prev_def_mask & user_use_mask) == user_use_mask;
  if (!prev_defs_flags) {
    return false;
  }

  // replace test with arithmetic node result
  test->replace_by(prev);

  // Modify the block
  test->as_Mach()->set_removed();
  block->remove_node(block_index);

  // Modify the CFG
  cfg_->map_node_to_block(test, nullptr);

  return true;
}

#endif // COMPILER2
