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

  val icache    = Module(new MMUSimple)
  val dcache    = Module(new MMUSimple)
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
  val xcpt      = WireInit(Bool(), false.B)
  val xret      = WireInit(Bool(), false.B)
  val xtvec     = Wire(UInt(xLen.W))
  val mepc      = Wire(UInt(xLen.W))
  val satp      = Wire(new Satp32)
  val PRV       = Wire(UInt(2.W))

  // 这个不应该叫commit，因为发生异常的指令不会commit。这个变量表示该执行下一条指令的意思
  val commit = xcpt || !mem_en && iack2 || mem_en && dack
  val iread  = RegInit(Bool(), true.B)
  iread := Mux(commit || iack, !iread, iread)

  // IF
  val icache_req  = icache.io.cpu.req
  val icache_resp = icache.io.cpu.resp
  val pc          = RegInit(UInt(32.W), 0.U)
  val pcp4        = pc + 4.U
  val jbr         = br_taken || jal || jalr
  val jbr_target  = MuxCase(br_target, Seq(jal -> (pc + imm), jalr -> (Rrs1 + (imm(31, 1) ## 0.U))))
  pc                   := Mux(commit, MuxCase(pcp4, Seq(xcpt -> xtvec, xret -> mepc, jbr -> jbr_target)), pc)
  icache.io.cpu.abort  := false.B
  icache_req.bits.addr := pc
  icache_req.valid     := iread
  icache_req.bits.sel  := "b1111".U
  icache_req.bits.we   := false.B
  icache_req.bits.data := DontCare
  icache_req.bits.satp := satp
  icache_req.bits.PRV  := PRV
  val inst = RegInit(UInt(32.W), "h13".U)
  inst         := MuxCase("h00000013".U, Seq((mem_en && !dack && !xcpt) -> inst, (iack && !xcpt) -> icache_resp.bits.data))
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
    ECALL      -> Seq(Y, FMT_IC, N, N, N, FN_ADD, N, X, MEM_B, CSR_CMD_P),
    EBREAK     -> Seq(Y, FMT_IC, N, N, N, FN_ADD, N, X, MEM_B, CSR_CMD_P),
    URET       -> unimpl(),
    SRET       -> Seq(Y, FMT_IC, N, N, N, FN_ADD, N, X, MEM_B, CSR_CMD_P),
    MRET       -> Seq(Y, FMT_IC, N, N, N, FN_ADD, N, X, MEM_B, CSR_CMD_P),
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

  val default      = unimpl(N)
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
  dcache.io.cpu.abort := xcpt
  val dcache_req   = dcache.io.cpu.req
  val dcache_resp  = dcache.io.cpu.resp
  val _mem_addr    = alu.io.out
  // 有时会出现lw xx, ?(xx)这样同一个寄存器即生成地址又接受数据的情况。因为现在等待访存结束是重复执行那条访存指令，所以同一条指令，地址会变
  val mem_addr_reg = RegEnable(_mem_addr, iack2)
  val mem_addr     = Mux(iack2, _mem_addr, mem_addr_reg)
  dcache_req.bits.satp := satp
  dcache_req.bits.PRV  := PRV
  dcache_req.bits.addr := mem_addr(31, 2) ## 0.U(2.W)
  dcache_req.valid     := cs.mem_en
  // 这玩意写进decode里？还是整个LSU出来
  val dcache_sel = MuxCase(
    "b1111".U,
    Seq(
      ((cs.mem_sz === MEM_HU || cs.mem_sz === MEM_H) && !mem_addr(1))           -> "b0011".U,
      ((cs.mem_sz === MEM_HU || cs.mem_sz === MEM_H) && mem_addr(1))            -> "b1100".U,
      ((cs.mem_sz === MEM_BU || cs.mem_sz === MEM_B) && mem_addr(1, 0) === 0.U) -> "b0001".U,
      ((cs.mem_sz === MEM_BU || cs.mem_sz === MEM_B) && mem_addr(1, 0) === 1.U) -> "b0010".U,
      ((cs.mem_sz === MEM_BU || cs.mem_sz === MEM_B) && mem_addr(1, 0) === 2.U) -> "b0100".U,
      ((cs.mem_sz === MEM_BU || cs.mem_sz === MEM_B) && mem_addr(1, 0) === 3.U) -> "b1000".U,
    ),
  )
  // read
  val mem_out = {
    val rdata = dcache_resp.bits.data
    MuxCase(
      rdata,
      Seq(
        (cs.mem_sz === MEM_HU && dcache_sel === "b0011".U) -> ZXT(rdata(15, 0)),
        (dcache_sel === "b0011".U)                         -> SXT(rdata(15, 0)),
        (cs.mem_sz === MEM_HU && dcache_sel === "b1100".U) -> ZXT(rdata(31, 16)),
        (dcache_sel === "b1100".U)                         -> SXT(rdata(31, 16)),
        (cs.mem_sz === MEM_BU && dcache_sel === "b0001".U) -> ZXT(rdata(7, 0)),
        (dcache_sel === "b0001".U)                         -> SXT(rdata(7, 0)),
        (cs.mem_sz === MEM_BU && dcache_sel === "b0010".U) -> ZXT(rdata(15, 8)),
        (dcache_sel === "b0010".U)                         -> SXT(rdata(15, 8)),
        (cs.mem_sz === MEM_BU && dcache_sel === "b0100".U) -> ZXT(rdata(23, 16)),
        (dcache_sel === "b0100".U)                         -> SXT(rdata(23, 16)),
        (cs.mem_sz === MEM_BU && dcache_sel === "b1000".U) -> ZXT(rdata(31, 24)),
        (dcache_sel === "b1000".U)                         -> SXT(rdata(31, 24)),
      ),
    )
  }
  // write
  dcache_req.bits.we := mem_store
  dcache_req.bits.sel  := dcache_sel
  dcache_req.bits.data := MuxLookup(
    dcache_sel,
    Rrs2,
    Seq(
      "b1100".U -> Rrs2(15, 0) ## 0.U(16.W),
      "b0010".U -> 0.U(16.W) ## Rrs2(7, 0) ## 0.U(8.W),
      "b0100".U -> 0.U(8.W) ## Rrs2(7, 0) ## 0.U(16.W),
      "b1000".U -> Rrs2(7, 0) ## 0.U(24.W),
    ),
  )

  // WB
  val csr = Module(new CSR)
  csr.io.pc              := pc
  csr.io.inst            := inst
  csr.io.inst_ilgl       := !cs.legal
  csr.io.inst_ret        := commit
  csr.io.inst_page_fault := icache_resp.valid && icache_resp.bits.page_fault // valid?
  csr.io.data_page_fault := dcache_resp.valid && dcache_resp.bits.page_fault // valid?
  csr.io.mem_addr        := mem_addr
  csr.io.mem_en          := cs.mem_en
  csr.io.mem_rw          := cs.mem_rw
  csr.io.mem_sz          := cs.mem_sz
  csr.io.cmd             := cs.csr_cmd
  csr.io.rdIsX0          := !rd.orR
  csr.io.rs1IsX0         := !rs1.orR                                         // 这个不只代表rs1不是x0，也意味着CSR??I的uimm不是0
  csr.io.in              := alu.io.out
  csr.io.jbr             := jbr
  csr.io.jbr_target      := jbr_target
  xcpt                   := csr.io.xcpt
  xtvec                  := csr.io.xtvec
  xret                   := csr.io.xret
  mepc                   := csr.io.xepc
  satp                   := csr.io.satp
  PRV                    := csr.io.PRV

  rf.io.wen   := (cs.fmt =/= FMT_S && cs.fmt =/= FMT_SB && cs.fmt =/= FMT_WIP) && !xcpt
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
