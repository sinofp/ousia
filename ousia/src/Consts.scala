import chisel3._

object Consts {
  val NOP_INST = 0x13.U(32.W)

  val SZ_SEL_ALU1 = 2
  val A1_ZERO     = 0.U(SZ_SEL_ALU1.W)
  val A1_RS1      = 1.U(SZ_SEL_ALU1.W)
  val A1_PC       = 2.U(SZ_SEL_ALU1.W)

  val SZ_SEL_ALU2 = 2
  val A2_ZERO     = 0.U(SZ_SEL_ALU2.W)
  val A2_RS2      = 1.U(SZ_SEL_ALU2.W)
  val A2_IMM      = 2.U(SZ_SEL_ALU2.W)

  val SZ_SEL_IMM = 3
  val IMM_S      = 0.U(SZ_SEL_IMM.W)
  val IMM_SB     = 1.U(SZ_SEL_IMM.W)
  val IMM_U      = 2.U(SZ_SEL_IMM.W)
  val IMM_UJ     = 3.U(SZ_SEL_IMM.W)
  val IMM_I      = 4.U(SZ_SEL_IMM.W)
  val IMM_Z      = 5.U(SZ_SEL_IMM.W)

  val SZ_SEL_RF_WDATA  = 2
  val RF_WDATA_ALU = 0.U(SZ_SEL_RF_WDATA.W)
  val RF_WDATA_MEM = 1.U(SZ_SEL_RF_WDATA.W)
  val RF_WDATA_PC4 = 2.U(SZ_SEL_RF_WDATA.W)
  val RF_WDATA_CSR = 3.U(SZ_SEL_RF_WDATA.W)

  val SZ_ALU_FN = 4
  val ALU_ADD   = 0.U(SZ_ALU_FN.W)
  val ALU_SL    = 1.U(SZ_ALU_FN.W)
  val ALU_SEQ   = 2.U(SZ_ALU_FN.W)
  val ALU_SNE   = 3.U(SZ_ALU_FN.W)
  val ALU_XOR   = 4.U(SZ_ALU_FN.W)
  val ALU_SR    = 5.U(SZ_ALU_FN.W)
  val ALU_OR    = 6.U(SZ_ALU_FN.W)
  val ALU_AND   = 7.U(SZ_ALU_FN.W)
  val ALU_SUB   = 10.U(SZ_ALU_FN.W)
  val ALU_SRA   = 11.U(SZ_ALU_FN.W)
  val ALU_SLT   = 12.U(SZ_ALU_FN.W)
  val ALU_SGE   = 13.U(SZ_ALU_FN.W)
  val ALU_SLTU  = 14.U(SZ_ALU_FN.W)
  val ALU_SGEU  = 15.U(SZ_ALU_FN.W)

  val ALU_DIVU   = ALU_SR
  val ALU_REM    = ALU_OR
  val ALU_REMU   = ALU_AND
  val ALU_MUL    = ALU_ADD
  val ALU_MULH   = ALU_SL
  val ALU_MULHSU = ALU_SEQ
  val ALU_MULHU  = ALU_SNE
}
