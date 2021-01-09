package core

import chipsalliance.rocketchip.config.Config
import chisel3._
import chisel3.util._
import Consts._
import Instructions._
import ALU._
import chisel3.util.experimental.BoringUtils

class Naive(implicit c: Config) extends CoreModule {
  val io = IO(new Bundle {
    val iwb = new WishBoneIO
    val rf  = Output(new RFIO)
  })

  io.rf.getElements.foreach(_ := 42.U)
  if (rf2Top)
    io.rf.elements.foreach(p => BoringUtils.addSink(p._2, s"${p._1}"))

  val br_taken  = WireInit(Bool(), false.B)
  val br_target = Wire(UInt(xLen.W))
  val Rrs1      = Wire(UInt(xLen.W))
  val imm       = Wire(UInt(xLen.W))
  val jal       = WireInit(Bool(), false.B)
  val jalr      = WireInit(Bool(), false.B)
  val mem_out   = Wire(UInt(xLen.W))
  val alu       = Module(new ALU)
  val if_stall  = !io.iwb.ack

  // IF
  val pc   = RegInit(UInt(32.W), 0.U)
  val pcp4 = pc + 4.U
  pc           := MuxCase(pcp4, Seq(if_stall -> pc, br_taken -> br_target, jal -> (pc + imm), jalr -> (Rrs1 + imm)))
  io.iwb.addr  := pc
  io.iwb.cyc   := true.B
  io.iwb.stb   := true.B
  io.iwb.sel   := "b1111".U
  io.iwb.we    := false.B
  io.iwb.wdata := DontCare
  val inst = Mux(if_stall, 0.U, io.iwb.rdata)

  // ID
  class FirstCtrlSigs extends Bundle {
    val legal  = Bool()
    val fmt    = UInt(SZ_FMT.W)
    val branch = Bool()
    val jalr   = Bool()
    val lui    = Bool()
    val alu_fn = UInt(SZ_ALU_FN.W)
    val mem_en = Bool()
    val mem_rw = Bool()
    val mem_sz = UInt(SZ_MEM_SZ.W)
  }

  implicit def uint2BitPat(x: UInt) = BitPat(x)

  val table: Seq[(BitPat, Seq[BitPat])] = Seq(
    BEQ        -> Seq(Y, FMT_SB, Y, N, N, FN_SEQ, N, X, MEM_B),
    BNE        -> Seq(Y, FMT_SB, Y, N, N, FN_SNE, N, X, MEM_B),
    BLT        -> Seq(Y, FMT_SB, Y, N, N, FN_SLT, N, X, MEM_B),
    BGE        -> Seq(Y, FMT_SB, Y, N, N, FN_SGE, N, X, MEM_B),
    BLTU       -> Seq(Y, FMT_SB, Y, N, N, FN_SLTU, N, X, MEM_B),
    BGEU       -> Seq(Y, FMT_SB, Y, N, N, FN_SGEU, N, X, MEM_B),
    JALR       -> Seq(Y, FMT_I, N, Y, N, FN_ADD, N, X, MEM_B),
    JAL        -> Seq(Y, FMT_UJ, N, Y, N, FN_ADD, N, X, MEM_B),
    LUI        -> Seq(Y, FMT_U, N, Y, N, FN_ADD, N, X, MEM_B),
    AUIPC      -> Seq(Y, FMT_U, N, N, N, FN_ADD, N, X, MEM_B),
    ADDI       -> Seq(Y, FMT_I, N, N, N, FN_ADD, N, X, MEM_B),
    SLLI       -> Seq(Y, FMT_I, N, N, N, FN_SL, N, X, MEM_B),
    SLTI       -> Seq(Y, FMT_I, N, N, N, FN_SLT, N, X, MEM_B),
    SLTIU      -> Seq(Y, FMT_I, N, N, N, FN_SLTU, N, X, MEM_B),
    XORI       -> Seq(Y, FMT_I, N, N, N, FN_XOR, N, X, MEM_B),
    SRLI       -> Seq(Y, FMT_I, N, N, N, FN_SR, N, X, MEM_B),
    SRAI       -> Seq(Y, FMT_I, N, N, N, FN_SRA, N, X, MEM_B),
    ORI        -> Seq(Y, FMT_I, N, N, N, FN_OR, N, X, MEM_B),
    ANDI       -> Seq(Y, FMT_I, N, N, N, FN_AND, N, X, MEM_B),
    ADD        -> Seq(Y, FMT_R, N, N, N, FN_ADD, N, X, MEM_B),
    SUB        -> Seq(Y, FMT_R, N, N, N, FN_SUB, N, X, MEM_B),
    SLL        -> Seq(Y, FMT_R, N, N, N, FN_SL, N, X, MEM_B),
    SLT        -> Seq(Y, FMT_R, N, N, N, FN_SLT, N, X, MEM_B),
    SLTU       -> Seq(Y, FMT_R, N, N, N, FN_SLTU, N, X, MEM_B),
    XOR        -> Seq(Y, FMT_R, N, N, N, FN_XOR, N, X, MEM_B),
    SRL        -> Seq(Y, FMT_R, N, N, N, FN_SR, N, X, MEM_B),
    SRA        -> Seq(Y, FMT_R, N, N, N, FN_SRA, N, X, MEM_B),
    OR         -> Seq(Y, FMT_R, N, N, N, FN_OR, N, X, MEM_B),
    AND        -> Seq(Y, FMT_R, N, N, N, FN_AND, N, X, MEM_B),
    LB         -> Seq(Y, FMT_I, N, N, N, FN_ADD, Y, N, MEM_B),
    LH         -> Seq(Y, FMT_I, N, N, N, FN_ADD, Y, N, MEM_H),
    LW         -> Seq(Y, FMT_I, N, N, N, FN_ADD, Y, N, MEM_W),
    LBU        -> Seq(Y, FMT_I, N, N, N, FN_ADD, Y, N, MEM_BU),
    LHU        -> Seq(Y, FMT_I, N, N, N, FN_ADD, Y, N, MEM_HU),
    SB         -> Seq(Y, FMT_S, N, N, N, FN_ADD, Y, Y, MEM_B),
    SH         -> Seq(Y, FMT_S, N, N, N, FN_ADD, Y, Y, MEM_B),
    SW         -> Seq(Y, FMT_S, N, N, N, FN_ADD, Y, Y, MEM_B),
    FENCE      -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    FENCE_I    -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    AMOADD_W   -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    AMOXOR_W   -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    AMOOR_W    -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    AMOAND_W   -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    AMOMIN_W   -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    AMOMAX_W   -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    AMOMINU_W  -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    AMOMAXU_W  -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    AMOSWAP_W  -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    LR_W       -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    SC_W       -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    ECALL      -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    EBREAK     -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    URET       -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    SRET       -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    MRET       -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    DRET       -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    SFENCE_VMA -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    WFI        -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    CSRRW      -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    CSRRS      -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    CSRRC      -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    CSRRWI     -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    CSRRSI     -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
    CSRRCI     -> Seq(Y, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B),
  )

  val default: Seq[BitPat] = Seq(N, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B)
  val firstDecoder         = DecodeLogic(inst, default, table)
  val cs                   = Wire(new FirstCtrlSigs) // 按性能讲，是不是一口气decode完更快？但那样代码不是纵向长就是横向长
  cs.getElements.reverse zip firstDecoder map { case (data, int) => data := int }

  val rf                   = Module(new RegFile(2))
  rf.io.raddr(0) := inst(19, 15)
  rf.io.raddr(1) := inst(24, 20)

  Rrs1 := rf.io.rdata(0)
  val Rrs2   = rf.io.rdata(1)
  val alu_fn = cs.alu_fn

  def SXT(x: UInt, len: Int = 32) = Fill(len - x.getWidth, x(x.getWidth - 1)) ## x
  imm := MuxLookup(
    cs.fmt,
    0.U,
    Seq(
      FMT_I  -> SXT(inst(31, 20)),
      FMT_S  -> SXT(inst(31, 25) ## inst(11, 7)),
      FMT_SB -> SXT(inst(31) ## inst(7) ## inst(30, 25) ## inst(11, 8) ## 0.U),
      FMT_U  -> inst(31, 12) ## 0.U(12.W),
      FMT_UJ -> SXT(inst(31) ## inst(19, 12) ## inst(20) ## inst(30, 21)),
    ),
  )

  val sel_alu1 = MuxCase(A1_RS1, Seq(cs.lui -> A1_ZERO, (cs.fmt === FMT_UJ || cs.fmt === FMT_U && !cs.lui) -> A1_PC))

  val sel_alu2 = MuxCase(A2_RS2, Seq((cs.fmt === FMT_I || cs.fmt === FMT_U || cs.fmt === FMT_UJ) -> A2_IMM))

  val bxx = cs.fmt === FMT_SB

  val mem_load  = cs.mem_en && !cs.mem_rw
  val mem_store = cs.mem_en && cs.mem_rw
  val mem_size  = cs.mem_sz

  val sel_rf_wdata =
    MuxCase(RF_WDATA_ALU, Seq((cs.jalr || cs.fmt === FMT_UJ) -> RF_WDATA_PC4, (cs.fmt === FMT_S) -> RF_WDATA_MEM))

  // EX
  alu.io.in1 := MuxLookup(sel_alu1, 0.U, Seq(A1_PC -> pc, A1_RS1 -> Rrs1))
  alu.io.in2       := MuxLookup(sel_alu2, 0.U, Seq(A2_IMM -> imm, A2_RS2 -> Rrs2))
  alu.io.fn        := alu_fn
  alu.io.dw        := false.B
  alu.io.adder_out := DontCare

  // rocket core不知道为啥在mem阶段判断br_target，因为EX已经很复杂了？但我看13年的图，判断是在EX
  br_taken  := alu.io.cmp_out && bxx
  br_target := pc + imm

  // MEM
  val dtcm = Module(new DTCM)
  dtcm.io.addr  := alu.io.out
  mem_out       := dtcm.io.rdata //todo sh, sb... lh, lb...
  dtcm.io.wen   := mem_store
  dtcm.io.wdata := Rrs2

  // WB
  rf.io.wen   := cs.fmt === FMT_R || cs.fmt === FMT_I || cs.fmt === FMT_U || cs.fmt === FMT_UJ // 反过来？
  rf.io.waddr := inst(11, 7)
  rf.io.wdata := MuxLookup(sel_rf_wdata, alu.io.out, Seq(RF_WDATA_MEM -> mem_out, RF_WDATA_PC4 -> pcp4))
}

class DTCM(implicit c: Config) extends CoreModule {
  val io = IO(new Bundle {
    val addr  = Input(UInt(xLen.W))
    val wdata = Input(UInt(xLen.W))
    val wen   = Input(Bool())
    val rdata = Output(UInt(xLen.W))
  })

  val mem = Reg(Vec(512, UInt(8.W)))

  io.rdata := mem(io.addr)
  when(io.wen) {
    mem(io.addr) := io.wdata
  }
}

class RFIO(implicit c: Config) extends CoreBundle {
  val w    = if (rf2Top) xLen.W else 0.W
  val zero = UInt(w)
  val ra   = UInt(w)
  val sp   = UInt(w)
  val gp   = UInt(w)
  val tp   = UInt(w)
  val t0   = UInt(w)
  val t1   = UInt(w)
  val t2   = UInt(w)
  val s0   = UInt(w)
  val s1   = UInt(w)
  val a0   = UInt(w)
  val a1   = UInt(w)
  val a2   = UInt(w)
  val a3   = UInt(w)
  val a4   = UInt(w)
  val a5   = UInt(w)
  val a6   = UInt(w)
  val a7   = UInt(w)
  val s2   = UInt(w)
  val s3   = UInt(w)
  val s4   = UInt(w)
  val s5   = UInt(w)
  val s6   = UInt(w)
  val s7   = UInt(w)
  val s8   = UInt(w)
  val s9   = UInt(w)
  val s10  = UInt(w)
  val s11  = UInt(w)
  val t3   = UInt(w)
  val t4   = UInt(w)
  val t5   = UInt(w)
  val t6   = UInt(w)
}

class WishBoneIO(implicit c: Config) extends CoreBundle {
  val addr  = Output(UInt(xLen.W))
  val wdata = Output(UInt(xLen.W))
  val sel   = Output(UInt((xLen / 8).W))
  val we    = Output(Bool())
  val cyc   = Output(Bool())
  val stb   = Output(Bool())
  val rdata = Input(UInt(xLen.W))
  val ack   = Input(Bool())
}
