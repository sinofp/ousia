package core

import chisel3._
import chisel3.util.BitPat

object Consts {
  val SZ_SEL_ALU1 = 2
  val A1_ZERO     = 0.U
  val A1_RS1      = 1.U
  val A1_PC       = 2.U

  val SZ_SEL_ALU2 = 0
  val A2_ZERO     = 0.U
  val A2_RS2      = 1.U
  val A2_IMM      = 2.U

  val SZ_FMT  = 3
  val FMT_R   = 0.U
  val FMT_I   = 1.U
  val FMT_S   = 2.U
  val FMT_SB  = 3.U
  val FMT_U   = 4.U
  val FMT_UJ  = 5.U
  val FMT_WIP = 6.U // 表示目前不支持这条指令，但之后要支持

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

  def X = BitPat("b?")
  def N = BitPat("b0")
  def Y = BitPat("b1")
}
