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

  val SZ_ALU_FN = 4
  val ALU_X     = BitPat.dontCare(SZ_ALU_FN)
  val ALU_ADD   = 0.U
  val ALU_SL    = 1.U
  val ALU_SEQ   = 2.U
  val ALU_SNE   = 3.U
  val ALU_XOR   = 4.U
  val ALU_SR    = 5.U
  val ALU_OR    = 6.U
  val ALU_AND   = 7.U
  val ALU_SUB   = 10.U
  val ALU_SRA   = 11.U
  val ALU_SLT   = 12.U
  val ALU_SGE   = 13.U
  val ALU_SLTU  = 14.U
  val ALU_SGEU  = 15.U

  val ALU_DIVU   = ALU_SR
  val ALU_REM    = ALU_OR
  val ALU_REMU   = ALU_AND
  val ALU_MUL    = ALU_ADD
  val ALU_MULH   = ALU_SL
  val ALU_MULHSU = ALU_SEQ
  val ALU_MULHU  = ALU_SNE
}
