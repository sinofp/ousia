package core

import chisel3._
import chisel3.util.BitPat

object Consts {
  val NOP_INST = 0x13.U(32.W)

  val SZ_SEL_ALU1 = 2
  val A1_ZERO     = 0.U
  val A1_RS1      = 1.U
  val A1_PC       = 2.U

  val SZ_SEL_ALU2 = 0
  val A2_ZERO     = 0.U
  val A2_RS2      = 1.U
  val A2_IMM      = 2.U

  val SZ_FMT  = 4
  val FMT_R   = 0.U
  val FMT_I   = 1.U
  val FMT_S   = 2.U
  val FMT_SB  = 3.U
  val FMT_U   = 4.U
  val FMT_UJ  = 5.U
  val FMT_IC  = 6.U
  val FMT_ICI = 7.U // 这俩是因为我的decode里没有直接给出ALU两个操作数来源，要通过FMT判断，所以假的假FMT
  val FMT_WIP = 8.U // 表示目前不支持这条指令，但之后要支持

  val SZ_MEM_SZ = 3
  val MEM_B     = 0.U
  val MEM_H     = 1.U
  val MEM_W     = 2.U
  val MEM_BU    = 3.U
  val MEM_HU    = 4.U

  val SZ_RF_WDATA  = 2
  val RF_WDATA_ALU = 0.U
  val RF_WDATA_MEM = 1.U
  val RF_WDATA_PC4 = 2.U
  val RF_WDATA_CSR = 3.U

  val SZ_CSR_CMD = 3
  val CSR_CMD_N  = 0.U
  val CSR_CMD_P  = 4.U // interrupt?
  val CSR_CMD_W  = 5.U
  val CSR_CMD_S  = 6.U
  val CSR_CMD_C  = 7.U

  val PRV_U = "b00".U
  val PRV_S = "b01".U
  val PRV_M = "b11".U

  def X = BitPat("b?")
  def N = BitPat("b0")
  def Y = BitPat("b1")
}
