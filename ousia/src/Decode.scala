import chisel3._
import chisel3.util.BitPat
import Instructions._
import Consts._
import chisel3.util.experimental.decode.{decoder, QMCMinimizer, TruthTable}

class CtrlSigs extends CoreBundle {
  val legal = Bool()
  val branch = Bool()
  val jal = Bool()
  val jalr = Bool()
  val sel_alu1 = UInt(SZ_SEL_ALU1.W)
  val sel_alu2 = UInt(SZ_SEL_ALU2.W)
  val sel_imm = UInt(SZ_SEL_IMM.W)
  val alu_fn = UInt(SZ_ALU_FN.W)
  val rf_we = Bool()
  val sel_rf_wdata = UInt(SZ_SEL_RF_WDATA.W)
}

class Decode extends CoreModule {
  val inst = IO(Input(UInt(32.W)))
  val sigs = IO(Output(new CtrlSigs))

  import BitPat.{N, Y, dontCare => DC}

  def mkMap(bp: BitPat, insts: Inst*) = insts.map(_ -> bp).toMap

  implicit def uint2BitPat(x: UInt): BitPat = BitPat(x)

  import sigs._

  // format: off
  val columns: Seq[(Data, Map[Inst, BitPat], BitPat)] = Seq(
    (legal, Map(), Y()),
    (branch, mkMap(Y(), BEQ, BNE, BLT, BGE, BLTU, BGEU), N()),
    (jal, mkMap(Y(), JAL), N()),
    (jalr, mkMap(Y(), JALR), N()),
    (sel_alu1, mkMap(A1_PC, JAL, AUIPC) ++ mkMap(A1_ZERO, LUI), A1_RS1),
    (sel_alu2, mkMap(A2_RS2, BEQ, BNE, BLT, BGE, BLTU, BGEU, ADD, SUB, SLT, SLTU, AND, OR, XOR, SLL, SRL, SRA), A2_IMM),
    (sel_imm, mkMap(IMM_SB, BEQ, BNE, BLT, BGE, BLTU, BGEU) ++ mkMap(IMM_UJ, JAL) ++ mkMap(IMM_U, JALR, LUI) ++ mkMap(IMM_S, SB, SH, SW), IMM_I),
    (alu_fn, mkMap(ALU_SEQ, BEQ) ++ mkMap(ALU_SNE, BNE) ++ mkMap(ALU_SLT, BLT, SLTI, SLT)
      ++ mkMap(ALU_SLTU, BLTU, SLTIU, SLTU) ++ mkMap(ALU_SGE, BGE) ++ mkMap(ALU_SGEU, BGEU) ++ mkMap(ALU_AND, AND, ANDI)
      ++ mkMap(ALU_OR, OR, ORI) ++ mkMap(ALU_XOR, XOR, XORI) ++ mkMap(ALU_SUB, SUB) ++ mkMap(ALU_SL, SLL, SLLI)
      ++ mkMap(ALU_SR, SRL, SRLI) ++ mkMap(ALU_SRA, SRA, SRAI), ALU_ADD),
    (rf_we, mkMap(N(), BEQ, BNE, BLT, BGE, BLTU, BGEU, SB, SH, SW), Y()),
    (sel_rf_wdata, mkMap(RF_WDATA_PC4, JAL, JALR) ++ mkMap(RF_WDATA_MEM, SB, SH, SW), RF_WDATA_ALU),
  )
  val table: Seq[(Inst, BitPat)] =
    Seq(BEQ, BNE, BLT, BGE, BLTU, BGEU, JALR, JAL, LUI, AUIPC, ADDI, SLLI, SLTI, SLTIU, XORI, SRLI, SRAI, ORI, ANDI,
      ADD, SUB, SLL, SLT, SLTU, XOR, SRL, SRA, OR, AND, LB, LH, LW, LBU, LHU, SB, SH, SW)
      .map(inst => inst -> columns.map { case (_, map, default) => map.getOrElse(inst, default) }.reduce(_ ## _))
  val default = Seq(N(), N(), N(), N(), DC(SZ_SEL_ALU1), DC(SZ_SEL_ALU2), DC(SZ_SEL_IMM), DC(SZ_ALU_FN), N(), DC(SZ_SEL_RF_WDATA)).reduce(_ ## _)
  // format: on

  val out = decoder(QMCMinimizer, inst, TruthTable(table, default))
  sigs := out.asTypeOf(new CtrlSigs)
}
