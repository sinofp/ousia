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
    val dwb = new WishBoneIO
    val rf  = Output(new RFIO)
  })

  io.rf.getElements.foreach(_ := 42.U)
  if (rf2Top)
    io.rf.elements.foreach(p => BoringUtils.addSink(p._2, s"${p._1}"))

  val icache    = Module(new Cache)
  val dcache    = Module(new Cache)
  val br_taken  = WireInit(Bool(), false.B)
  val br_target = Wire(UInt(xLen.W))
  val Rrs1      = Wire(UInt(xLen.W))
  val imm       = Wire(UInt(xLen.W))
  val jal       = WireInit(Bool(), false.B)
  val jalr      = WireInit(Bool(), false.B)
  val mem_en    = WireInit(Bool(), false.B)
  val mem_load  = WireInit(Bool(), false.B)
  val alu       = Module(new ALU)
  val iack      = icache.io.cpu.resp.valid
  val dack      = dcache.io.cpu.resp.valid
  val iack2     = RegNext(iack, false.B)
  val commit    = !mem_en && iack2 || mem_en && dack
  val iread     = RegInit(Bool(), true.B)
  iread := Mux(commit || iack, !iread, iread)

  // IF
  val icache_req  = icache.io.cpu.req
  val icache_resp = icache.io.cpu.resp
  val pc          = RegInit(UInt(32.W), 0.U)
  val pcp4        = pc + 4.U
  pc                   := Mux(commit, MuxCase(pcp4, Seq(br_taken -> br_target, jal -> (pc + imm), jalr -> (Rrs1 + imm))), pc)
  icache.io.cpu.abort  := false.B
  icache_req.bits.addr := pc
  icache_req.valid     := iread
  icache_req.bits.sel  := "b1111".U
  icache_req.bits.we   := false.B
  icache_req.bits.data := DontCare
  val inst = Reg(UInt(32.W))
  inst         := MuxCase("h00000013".U, Seq((mem_en && !dack) -> inst, iack -> icache_resp.bits.data))
  icache.io.wb <> io.iwb

  // ID
  class FirstCtrlSigs extends Bundle {
    val legal   = Bool()
    val fmt     = UInt(SZ_FMT.W)
    val branch  = Bool()
    val jalr    = Bool()
    val lui     = Bool()
    val alu_fn  = UInt(SZ_ALU_FN.W)
    val mem_en  = Bool()
    val mem_rw  = Bool()
    val mem_sz  = UInt(SZ_MEM_SZ.W)
    val csr_cmd = UInt(SZ_CSR_CMD.W)
  }

  implicit def uint2BitPat(x: UInt)          = BitPat(x)
  def unimpl(legal: BitPat = Y): Seq[BitPat] = Seq(legal, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B, CSR_CMD_N)

  val table: Seq[(BitPat, Seq[BitPat])] = Seq(
    BEQ        -> Seq(Y, FMT_SB, Y, N, N, FN_SEQ, N, X, MEM_B, CSR_CMD_N),
    BNE        -> Seq(Y, FMT_SB, Y, N, N, FN_SNE, N, X, MEM_B, CSR_CMD_N),
    BLT        -> Seq(Y, FMT_SB, Y, N, N, FN_SLT, N, X, MEM_B, CSR_CMD_N),
    BGE        -> Seq(Y, FMT_SB, Y, N, N, FN_SGE, N, X, MEM_B, CSR_CMD_N),
    BLTU       -> Seq(Y, FMT_SB, Y, N, N, FN_SLTU, N, X, MEM_B, CSR_CMD_N),
    BGEU       -> Seq(Y, FMT_SB, Y, N, N, FN_SGEU, N, X, MEM_B, CSR_CMD_N),
    JALR       -> Seq(Y, FMT_I, N, Y, N, FN_ADD, N, X, MEM_B, CSR_CMD_N),
    JAL        -> Seq(Y, FMT_UJ, N, N, N, FN_ADD, N, X, MEM_B, CSR_CMD_N),
    LUI        -> Seq(Y, FMT_U, N, N, Y, FN_ADD, N, X, MEM_B, CSR_CMD_N),
    AUIPC      -> Seq(Y, FMT_U, N, N, N, FN_ADD, N, X, MEM_B, CSR_CMD_N),
    ADDI       -> Seq(Y, FMT_I, N, N, N, FN_ADD, N, X, MEM_B, CSR_CMD_N),
    SLLI       -> Seq(Y, FMT_I, N, N, N, FN_SL, N, X, MEM_B, CSR_CMD_N),
    SLTI       -> Seq(Y, FMT_I, N, N, N, FN_SLT, N, X, MEM_B, CSR_CMD_N),
    SLTIU      -> Seq(Y, FMT_I, N, N, N, FN_SLTU, N, X, MEM_B, CSR_CMD_N),
    XORI       -> Seq(Y, FMT_I, N, N, N, FN_XOR, N, X, MEM_B, CSR_CMD_N),
    SRLI       -> Seq(Y, FMT_I, N, N, N, FN_SR, N, X, MEM_B, CSR_CMD_N),
    SRAI       -> Seq(Y, FMT_I, N, N, N, FN_SRA, N, X, MEM_B, CSR_CMD_N),
    ORI        -> Seq(Y, FMT_I, N, N, N, FN_OR, N, X, MEM_B, CSR_CMD_N),
    ANDI       -> Seq(Y, FMT_I, N, N, N, FN_AND, N, X, MEM_B, CSR_CMD_N),
    ADD        -> Seq(Y, FMT_R, N, N, N, FN_ADD, N, X, MEM_B, CSR_CMD_N),
    SUB        -> Seq(Y, FMT_R, N, N, N, FN_SUB, N, X, MEM_B, CSR_CMD_N),
    SLL        -> Seq(Y, FMT_R, N, N, N, FN_SL, N, X, MEM_B, CSR_CMD_N),
    SLT        -> Seq(Y, FMT_R, N, N, N, FN_SLT, N, X, MEM_B, CSR_CMD_N),
    SLTU       -> Seq(Y, FMT_R, N, N, N, FN_SLTU, N, X, MEM_B, CSR_CMD_N),
    XOR        -> Seq(Y, FMT_R, N, N, N, FN_XOR, N, X, MEM_B, CSR_CMD_N),
    SRL        -> Seq(Y, FMT_R, N, N, N, FN_SR, N, X, MEM_B, CSR_CMD_N),
    SRA        -> Seq(Y, FMT_R, N, N, N, FN_SRA, N, X, MEM_B, CSR_CMD_N),
    OR         -> Seq(Y, FMT_R, N, N, N, FN_OR, N, X, MEM_B, CSR_CMD_N),
    AND        -> Seq(Y, FMT_R, N, N, N, FN_AND, N, X, MEM_B, CSR_CMD_N),
    LB         -> Seq(Y, FMT_I, N, N, N, FN_ADD, Y, N, MEM_B, CSR_CMD_N),
    LH         -> Seq(Y, FMT_I, N, N, N, FN_ADD, Y, N, MEM_H, CSR_CMD_N),
    LW         -> Seq(Y, FMT_I, N, N, N, FN_ADD, Y, N, MEM_W, CSR_CMD_N),
    LBU        -> Seq(Y, FMT_I, N, N, N, FN_ADD, Y, N, MEM_BU, CSR_CMD_N),
    LHU        -> Seq(Y, FMT_I, N, N, N, FN_ADD, Y, N, MEM_HU, CSR_CMD_N),
    SB         -> Seq(Y, FMT_S, N, N, N, FN_ADD, Y, Y, MEM_B, CSR_CMD_N),
    SH         -> Seq(Y, FMT_S, N, N, N, FN_ADD, Y, Y, MEM_H, CSR_CMD_N),
    SW         -> Seq(Y, FMT_S, N, N, N, FN_ADD, Y, Y, MEM_W, CSR_CMD_N),
    FENCE      -> unimpl(),
    FENCE_I    -> unimpl(),
    AMOADD_W   -> unimpl(),
    AMOXOR_W   -> unimpl(),
    AMOOR_W    -> unimpl(),
    AMOAND_W   -> unimpl(),
    AMOMIN_W   -> unimpl(),
    AMOMAX_W   -> unimpl(),
    AMOMINU_W  -> unimpl(),
    AMOMAXU_W  -> unimpl(),
    AMOSWAP_W  -> unimpl(),
    LR_W       -> unimpl(),
    SC_W       -> unimpl(),
    ECALL      -> unimpl(),
    EBREAK     -> unimpl(),
    URET       -> unimpl(),
    SRET       -> unimpl(),
    MRET       -> unimpl(),
    DRET       -> unimpl(),
    SFENCE_VMA -> unimpl(),
    WFI        -> unimpl(),
    CSRRW      -> Seq(Y, FMT_IC, N, N, N, FN_ADD, N, X, MEM_B, CSR_CMD_W),
    CSRRS      -> Seq(Y, FMT_IC, N, N, N, FN_ADD, N, X, MEM_B, CSR_CMD_S),
    CSRRC      -> Seq(Y, FMT_IC, N, N, N, FN_ADD, N, X, MEM_B, CSR_CMD_C),
    CSRRWI     -> Seq(Y, FMT_ICI, N, N, N, FN_ADD, N, X, MEM_B, CSR_CMD_W),
    CSRRSI     -> Seq(Y, FMT_ICI, N, N, N, FN_ADD, N, X, MEM_B, CSR_CMD_S),
    CSRRCI     -> Seq(Y, FMT_ICI, N, N, N, FN_ADD, N, X, MEM_B, CSR_CMD_C),
  )

  val default      = unimpl(X)
  val firstDecoder = DecodeLogic(inst, default, table)
  val cs           = Wire(new FirstCtrlSigs) // 按性能讲，是不是一口气decode完更快？但那样代码不是纵向长就是横向长
  cs.getElements.reverse zip firstDecoder map { case (data, int) => data := int }

  mem_en := cs.mem_en
  jal  := cs.fmt === FMT_UJ
  jalr := cs.jalr

  val rs1 = inst(19, 15)
  val rs2 = inst(24, 20)
  val rd  = inst(11, 7)

  val rf = Module(new RegFile(2))
  rf.io.raddr(0) := rs1
  rf.io.raddr(1) := rs2

  Rrs1 := rf.io.rdata(0)
  val Rrs2   = rf.io.rdata(1)
  val alu_fn = cs.alu_fn

  def ZXT(x: UInt, len: Int = 32) = 0.U((len - x.getWidth).W) ## x
  def SXT(x: UInt, len: Int = 32) = Fill(len - x.getWidth, x(x.getWidth - 1)) ## x
  imm := Mux(
    cs.csr_cmd === CSR_CMD_N,
    MuxLookup(
      cs.fmt,
      0.U,
      Seq(
        FMT_I  -> SXT(inst(31, 20)),
        FMT_S  -> SXT(inst(31, 25) ## inst(11, 7)),
        FMT_SB -> SXT(inst(31) ## inst(7) ## inst(30, 25) ## inst(11, 8) ## 0.U),
        FMT_U  -> inst(31, 12) ## 0.U(12.W),
        FMT_UJ -> SXT(inst(31) ## inst(19, 12) ## inst(20) ## inst(30, 21) ## 0.U),
      ),
    ),
    ZXT(rs1),
  )

  val sel_alu1 = MuxCase(
    A1_RS1,
    Seq((cs.fmt === FMT_ICI || cs.lui) -> A1_ZERO, (cs.fmt === FMT_UJ || cs.fmt === FMT_U && !cs.lui) -> A1_PC),
  )
  val sel_alu2 =
    MuxCase(
      A2_RS2,
      Seq(
        (cs.fmt === FMT_I || cs.fmt === FMT_U || cs.fmt === FMT_UJ || cs.fmt === FMT_S || cs.fmt === FMT_ICI) -> A2_IMM,
        (cs.fmt === FMT_IC)                                                                                   -> A2_ZERO,
      ),
    )

  mem_load := cs.mem_en && !cs.mem_rw
  val mem_store = cs.mem_en && cs.mem_rw
  val mem_size  = cs.mem_sz

  val sel_rf_wdata =
    MuxCase(
      RF_WDATA_ALU,
      Seq((jalr || jal) -> RF_WDATA_PC4, mem_load -> RF_WDATA_MEM, (cs.csr_cmd =/= CSR_CMD_N) -> RF_WDATA_CSR),
    )

  // EX
  alu.io.in1       := MuxLookup(sel_alu1, 0.U, Seq(A1_PC -> pc, A1_RS1 -> Rrs1))
  alu.io.in2       := MuxLookup(sel_alu2, 0.U, Seq(A2_IMM -> imm, A2_RS2 -> Rrs2))
  alu.io.fn        := alu_fn
  alu.io.dw        := false.B
  alu.io.adder_out := DontCare

  // rocket core不知道为啥在mem阶段判断br_target，因为EX已经很复杂了？但我看13年的图，判断是在EX
  br_taken  := alu.io.cmp_out && cs.fmt === FMT_SB
  br_target := pc + imm

  // MEM
  io.dwb              <> dcache.io.wb
  dcache.io.cpu.abort := false.B
  val dcache_req  = dcache.io.cpu.req
  val dcache_resp = dcache.io.cpu.resp
  dcache_req.bits.addr := alu.io.out
  dcache_req.valid     := cs.mem_en
  // read
  val mem_out = {
    val rdata = dcache_resp.bits.data
    MuxLookup(
      cs.mem_sz,
      rdata,
      Seq(
        MEM_H  -> SXT(rdata(15, 0)),
        MEM_HU -> ZXT(rdata(15, 0)),
        MEM_B  -> SXT(rdata(7, 0)),
        MEM_BU -> ZXT(rdata(7, 0)),
      ),
    )
  }
  // write
  dcache_req.bits.we   := mem_store
  dcache_req.bits.sel  := MuxLookup(cs.mem_sz, "b1111".U, Seq(MEM_H -> "b0011".U, MEM_B -> "b0001".U))
  dcache_req.bits.data := Rrs2

  // WB
  val csr = Module(new CSR)
  csr.io.cmd     := cs.csr_cmd
  csr.io.rdIsX0  := !rd.orR
  csr.io.rs1IsX0 := !rs1.orR // 这个不只代表rs1不是x0，也意味着CSR??I的uimm不是0
  csr.io.csr     := inst(31, 20)
  csr.io.in      := alu.io.out

  rf.io.wen   := cs.fmt === FMT_R || cs.fmt === FMT_I || cs.fmt === FMT_U || cs.fmt === FMT_UJ // 反过来？
  rf.io.waddr := rd
  rf.io.wdata := MuxLookup(
    sel_rf_wdata,
    alu.io.out,
    Seq(RF_WDATA_MEM -> mem_out, RF_WDATA_PC4 -> pcp4, RF_WDATA_CSR -> csr.io.out),
  )
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
