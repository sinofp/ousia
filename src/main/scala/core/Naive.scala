package core

import chipsalliance.rocketchip.config.Config
import chisel3._
import chisel3.util._
import Consts._
import Instructions._
import ALU._
import Util._

class Naive(implicit c: Config) extends CoreModule {
  val io = IO(new Bundle {
    val iwb = new WishBoneIO
    val dwb = new WishBoneIO
  })

  val icache    = Module(new MMUSimple)
  val dcache    = Module(new MMUSimple)
  val br_taken  = WireInit(Bool(), false.B)
  val br_target = Wire(UInt(xLen.W))
  val Rrs1      = Wire(UInt(xLen.W))
  val imm       = Wire(UInt(xLen.W))
  val jal       = WireInit(Bool(), false.B)
  val jalr      = WireInit(Bool(), false.B)
  val xcpt      = WireInit(Bool(), false.B)
  val xret      = WireInit(Bool(), false.B)
  val xtvec     = Wire(UInt(xLen.W))
  val xepc      = Wire(UInt(xLen.W))
  val satp      = Wire(new Satp32)
  val PRV       = Wire(UInt(2.W))

  val exec_start = Reg(Bool()) // enable update mem addr
  val next_inst  = Wire(Bool())
  dontTouch(next_inst) // used for trace

  // IF
  val icache_req  = icache.io.cpu.req
  val icache_resp = icache.io.cpu.resp
  val pc          = RegInit(UInt(32.W), c(DRAM_BASE))
  val pcp4        = pc + 4.U
  val jbr         = br_taken || jal || jalr
  val jbr_target  = MuxCase(br_target, Seq(jal -> (pc + imm), jalr -> (Rrs1 + (imm(31, 1) ## 0.U))))
  val inst        = RegInit(UInt(32.W), NOP_INST)
  icache.io.cpu.abort  := false.B
  icache_req.bits.addr := pc
  icache_req.bits.sel  := "b1111".U
  icache_req.bits.we   := false.B
  icache_req.bits.data := DontCare
  icache_req.bits.satp := satp
  icache_req.bits.PRV  := PRV
  icache.io.wb         <> io.iwb

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
    val amo     = Bool()
    val lrsc    = Bool()
    val mul_div = Bool()
  }

  implicit def uint2BitPat(x: UInt): BitPat  = BitPat(x)
  def unimpl(legal: BitPat = Y): Seq[BitPat] = Seq(legal, FMT_WIP, N, N, N, FN_ADD, N, X, MEM_B, CSR_CMD_N, N, N, N)

  val tableI: Seq[(BitPat, Seq[BitPat])]     = Seq(
    BEQ        -> Seq(Y, FMT_SB, Y, N, N, FN_SEQ, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    BNE        -> Seq(Y, FMT_SB, Y, N, N, FN_SNE, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    BLT        -> Seq(Y, FMT_SB, Y, N, N, FN_SLT, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    BGE        -> Seq(Y, FMT_SB, Y, N, N, FN_SGE, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    BLTU       -> Seq(Y, FMT_SB, Y, N, N, FN_SLTU, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    BGEU       -> Seq(Y, FMT_SB, Y, N, N, FN_SGEU, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    JALR       -> Seq(Y, FMT_I, N, Y, N, FN_ADD, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    JAL        -> Seq(Y, FMT_UJ, N, N, N, FN_ADD, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    LUI        -> Seq(Y, FMT_U, N, N, Y, FN_ADD, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    AUIPC      -> Seq(Y, FMT_U, N, N, N, FN_ADD, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    ADDI       -> Seq(Y, FMT_I, N, N, N, FN_ADD, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    SLLI       -> Seq(Y, FMT_I, N, N, N, FN_SL, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    SLTI       -> Seq(Y, FMT_I, N, N, N, FN_SLT, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    SLTIU      -> Seq(Y, FMT_I, N, N, N, FN_SLTU, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    XORI       -> Seq(Y, FMT_I, N, N, N, FN_XOR, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    SRLI       -> Seq(Y, FMT_I, N, N, N, FN_SR, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    SRAI       -> Seq(Y, FMT_I, N, N, N, FN_SRA, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    ORI        -> Seq(Y, FMT_I, N, N, N, FN_OR, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    ANDI       -> Seq(Y, FMT_I, N, N, N, FN_AND, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    ADD        -> Seq(Y, FMT_R, N, N, N, FN_ADD, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    SUB        -> Seq(Y, FMT_R, N, N, N, FN_SUB, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    SLL        -> Seq(Y, FMT_R, N, N, N, FN_SL, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    SLT        -> Seq(Y, FMT_R, N, N, N, FN_SLT, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    SLTU       -> Seq(Y, FMT_R, N, N, N, FN_SLTU, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    XOR        -> Seq(Y, FMT_R, N, N, N, FN_XOR, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    SRL        -> Seq(Y, FMT_R, N, N, N, FN_SR, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    SRA        -> Seq(Y, FMT_R, N, N, N, FN_SRA, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    OR         -> Seq(Y, FMT_R, N, N, N, FN_OR, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    AND        -> Seq(Y, FMT_R, N, N, N, FN_AND, N, X, MEM_DC, CSR_CMD_N, N, N, N),
    LB         -> Seq(Y, FMT_I, N, N, N, FN_ADD, Y, N, MEM_B, CSR_CMD_N, N, N, N),
    LH         -> Seq(Y, FMT_I, N, N, N, FN_ADD, Y, N, MEM_H, CSR_CMD_N, N, N, N),
    LW         -> Seq(Y, FMT_I, N, N, N, FN_ADD, Y, N, MEM_W, CSR_CMD_N, N, N, N),
    LBU        -> Seq(Y, FMT_I, N, N, N, FN_ADD, Y, N, MEM_BU, CSR_CMD_N, N, N, N),
    LHU        -> Seq(Y, FMT_I, N, N, N, FN_ADD, Y, N, MEM_HU, CSR_CMD_N, N, N, N),
    SB         -> Seq(Y, FMT_S, N, N, N, FN_ADD, Y, Y, MEM_B, CSR_CMD_N, N, N, N),
    SH         -> Seq(Y, FMT_S, N, N, N, FN_ADD, Y, Y, MEM_H, CSR_CMD_N, N, N, N),
    SW         -> Seq(Y, FMT_S, N, N, N, FN_ADD, Y, Y, MEM_W, CSR_CMD_N, N, N, N),
    FENCE      -> unimpl(),
    FENCE_I    -> unimpl(),
    ECALL      -> Seq(Y, FMT_IC, N, N, N, FN_ADD, N, X, MEM_DC, CSR_CMD_P, N, N, N),
    EBREAK     -> Seq(Y, FMT_IC, N, N, N, FN_ADD, N, X, MEM_DC, CSR_CMD_P, N, N, N),
    URET       -> unimpl(),
    SRET       -> Seq(Y, FMT_IC, N, N, N, FN_ADD, N, X, MEM_DC, CSR_CMD_P, N, N, N),
    MRET       -> Seq(Y, FMT_IC, N, N, N, FN_ADD, N, X, MEM_DC, CSR_CMD_P, N, N, N),
    DRET       -> unimpl(),
    SFENCE_VMA -> unimpl(),
    WFI        -> unimpl(),
  )
  val tableZicsr: Seq[(BitPat, Seq[BitPat])] = Seq(
    CSRRW  -> Seq(Y, FMT_IC, N, N, N, FN_ADD, N, X, MEM_DC, CSR_CMD_W, N, N, N),
    CSRRS  -> Seq(Y, FMT_IC, N, N, N, FN_ADD, N, X, MEM_DC, CSR_CMD_S, N, N, N),
    CSRRC  -> Seq(Y, FMT_IC, N, N, N, FN_ADD, N, X, MEM_DC, CSR_CMD_C, N, N, N),
    CSRRWI -> Seq(Y, FMT_ICI, N, N, N, FN_ADD, N, X, MEM_DC, CSR_CMD_W, N, N, N),
    CSRRSI -> Seq(Y, FMT_ICI, N, N, N, FN_ADD, N, X, MEM_DC, CSR_CMD_S, N, N, N),
    CSRRCI -> Seq(Y, FMT_ICI, N, N, N, FN_ADD, N, X, MEM_DC, CSR_CMD_C, N, N, N),
  )
  val tableA: Seq[(BitPat, Seq[BitPat])]     = Seq(
    AMOADD_W  -> Seq(Y, FMT_R, N, N, N, FN_ADD, Y, N, MEM_W, CSR_CMD_N, Y, N, N),
    AMOXOR_W  -> Seq(Y, FMT_R, N, N, N, FN_XOR, Y, N, MEM_W, CSR_CMD_N, Y, N, N),
    AMOOR_W   -> Seq(Y, FMT_R, N, N, N, FN_OR, Y, N, MEM_W, CSR_CMD_N, Y, N, N),
    AMOAND_W  -> Seq(Y, FMT_R, N, N, N, FN_AND, Y, N, MEM_W, CSR_CMD_N, Y, N, N),
    AMOMIN_W  -> Seq(Y, FMT_R, N, N, N, FN_SLT, Y, N, MEM_W, CSR_CMD_N, Y, N, N),
    AMOMAX_W  -> Seq(Y, FMT_R, N, N, N, FN_SLT, Y, N, MEM_W, CSR_CMD_N, Y, N, N),
    AMOMINU_W -> Seq(Y, FMT_R, N, N, N, FN_SLTU, Y, N, MEM_W, CSR_CMD_N, Y, N, N),
    AMOMAXU_W -> Seq(Y, FMT_R, N, N, N, FN_SLTU, Y, N, MEM_W, CSR_CMD_N, Y, N, N),
    AMOSWAP_W -> Seq(Y, FMT_R, N, N, N, FN_X, Y, N, MEM_W, CSR_CMD_N, Y, N, N),
    LR_W      -> Seq(Y, FMT_R, N, N, N, FN_ADD, Y, N, MEM_W, CSR_CMD_N, N, Y, N),
    SC_W      -> Seq(Y, FMT_R, N, N, N, FN_ADD, Y, Y, MEM_W, CSR_CMD_N, N, Y, N),
  )
  val tableM: Seq[(BitPat, Seq[BitPat])]     = Seq(
    MUL    -> Seq(Y, FMT_R, N, N, N, FN_MUL, N, X, MEM_DC, CSR_CMD_N, N, N, Y),
    MULH   -> Seq(Y, FMT_R, N, N, N, FN_MULH, N, X, MEM_DC, CSR_CMD_N, N, N, Y),
    MULHSU -> Seq(Y, FMT_R, N, N, N, FN_MULHSU, N, X, MEM_DC, CSR_CMD_N, N, N, Y),
    MULHU  -> Seq(Y, FMT_R, N, N, N, FN_MULHU, N, X, MEM_DC, CSR_CMD_N, N, N, Y),
    DIV    -> Seq(Y, FMT_R, N, N, N, FN_DIV, N, X, MEM_DC, CSR_CMD_N, N, N, Y),
    DIVU   -> Seq(Y, FMT_R, N, N, N, FN_DIVU, N, X, MEM_DC, CSR_CMD_N, N, N, Y),
    REM    -> Seq(Y, FMT_R, N, N, N, FN_REM, N, X, MEM_DC, CSR_CMD_N, N, N, Y),
    REMU   -> Seq(Y, FMT_R, N, N, N, FN_REMU, N, X, MEM_DC, CSR_CMD_N, N, N, Y),
  )
  val table: Seq[(BitPat, Seq[BitPat])]      = tableI ++
    (if (c(ExtZicsr)) tableZicsr else Seq()) ++
    (if (c(ExtA)) tableA else Seq()) ++
    (if (c(ExtM)) tableM else Seq())

  val default      = unimpl(N)
  val firstDecoder = DecodeLogic(inst, default, table)
  val cs           = Wire(new FirstCtrlSigs) // 按性能讲，是不是一口气decode完更快？但那样代码不是纵向长就是横向长
  cs.getElements.reverse zip firstDecoder foreach { case (data, int) => data := int }

  jal := cs.fmt === FMT_UJ
  jalr := cs.jalr

  val rs1 = inst(19, 15)
  val rs2 = inst(24, 20)
  val rd  = inst(11, 7)

  val rf = Module(new RegFile(2))
  rf.io.raddr(0) := rs1
  rf.io.raddr(1) := rs2

  Rrs1 := rf.io.rdata(0)
  val Rrs2 = rf.io.rdata(1)

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

  val is_sc    = Wire(Bool())
  val sel_alu1 = MuxCase(
    A1_RS1,
    Seq((cs.fmt === FMT_ICI || cs.lui) -> A1_ZERO, (cs.fmt === FMT_UJ || cs.fmt === FMT_U && !cs.lui) -> A1_PC),
  )
  val sel_alu2 =
    MuxCase(
      A2_RS2,
      Seq(
        (cs.fmt === FMT_I || cs.fmt === FMT_U || cs.fmt === FMT_UJ || cs.fmt === FMT_S || cs.fmt === FMT_ICI) -> A2_IMM,
        (cs.fmt === FMT_IC || cs.amo || is_sc)                                                                -> A2_ZERO,
      ),
    )

  val dcache_resp = dcache.io.cpu.resp
  val mem_valid_2 = Counter(0 until 2, dcache_resp.valid, exec_start)._1 === 1.U
  val mem_rw      = Mux(cs.amo, mem_valid_2, cs.mem_rw)
  val mem_load    = cs.mem_en && !mem_rw
  val mem_store   = cs.mem_en && mem_rw
  val mem_size    = cs.mem_sz

  val sel_rf_wdata =
    MuxCase(
      RF_WDATA_ALU,
      Seq(
        (jalr || jal)              -> RF_WDATA_PC4,
        mem_load                   -> RF_WDATA_MEM,
        (cs.csr_cmd =/= CSR_CMD_N) -> RF_WDATA_CSR,
        is_sc                      -> RF_WDATA_SCW, // 其实可以改改数据同路，消掉这个选项
      ),
    )

  // EX
  val alu = Module(new ALU)
  alu.io.in1       := MuxLookup(sel_alu1, 0.U, Seq(A1_PC -> pc, A1_RS1 -> Rrs1))
  alu.io.in2       := MuxLookup(sel_alu2, 0.U, Seq(A2_IMM -> imm, A2_RS2 -> Rrs2))
  alu.io.fn        := Mux(cs.amo, FN_ADD, cs.alu_fn)
  alu.io.dw        := false.B
  alu.io.adder_out := DontCare

  val (aluOut, mulDivValid) = {
    if (c(ExtM)) {
      val mulDiv = Module(new MulDiv)
      mulDiv.io.start := cs.mul_div && exec_start
      mulDiv.io.in1   := Rrs1
      mulDiv.io.in2   := Rrs2
      mulDiv.io.fn    := cs.alu_fn
      (Mux(cs.mul_div, mulDiv.io.out.bits, alu.io.out), mulDiv.io.out.valid)
    } else (alu.io.out, true.B)
  }

  // rocket core不知道为啥在mem阶段判断br_target，因为EX已经很复杂了？但我看13年的图，判断是在EX
  br_taken  := alu.io.cmp_out && cs.fmt === FMT_SB
  br_target := pc + imm

  // MEM
  val mem_addr      = Wire(UInt(xLen.W))
  // lr/sc
  val lrsc_flag_set = cs.lrsc && !mem_rw
  val lrsc_addr     = RegEnable(mem_addr, lrsc_flag_set)
  is_sc := cs.lrsc && mem_rw
  // 好像只有其他hart执行store才会取消预约；另外貌似也没说异常后要取消
  val lrsc_flag_clr = is_sc && mem_addr === lrsc_addr // && xcpt
  val lrsc_flag     = RegEnable(lrsc_flag_set | !lrsc_flag_clr, lrsc_flag_set | lrsc_flag_clr)
  val _sc_succ      = lrsc_flag && is_sc && mem_addr === lrsc_addr
  val sc_succ       = Mux(exec_start, _sc_succ, RegEnable(_sc_succ, exec_start))
  // misc
  io.dwb              <> dcache.io.wb
  dcache.io.cpu.abort := xcpt
  val dcache_req = dcache.io.cpu.req
  // 有时会出现lw xx, ?(xx)这样同一个寄存器即生成地址又接受数据的情况。因为现在等待访存结束是重复执行那条访存指令，所以同一条指令，地址会变
  mem_addr             := Mux(exec_start, aluOut, RegEnable(aluOut, exec_start))
  dcache_req.bits.satp := satp
  dcache_req.bits.PRV  := PRV
  dcache_req.bits.addr := mem_addr(31, 2) ## 0.U(2.W)
  //                                                        v  interval between AMO single-read & single-write
  dcache_req.valid     := Mux(is_sc, sc_succ, cs.mem_en && !RegNext(dcache_resp.valid))
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
  dcache_req.bits.sel := dcache_sel
  // sc xx, xx, (yy)会把xx的值写成成功与否，这时store回(yy)的xx值就不是以前的值了，这里把写入内存的R[rs2]固定一下
  val _wdataRrs2 = MuxLookup(
    dcache_sel,
    Rrs2,
    Seq(
      "b1100".U -> Rrs2(15, 0) ## 0.U(16.W),
      "b0010".U -> 0.U(16.W) ## Rrs2(7, 0) ## 0.U(8.W),
      "b0100".U -> 0.U(8.W) ## Rrs2(7, 0) ## 0.U(16.W),
      "b1000".U -> Rrs2(7, 0) ## 0.U(24.W),
    ),
  )
  val wdataRrs2  = Mux(exec_start, _wdataRrs2, RegEnable(_wdataRrs2, exec_start))
  dcache_req.bits.data := {
    if (c(ExtA)) {
      // amo
      val amoAlu = Module(new AMOALU)
      amoAlu.io.funct5 := inst(31, 27)
      amoAlu.io.fn     := cs.alu_fn
      amoAlu.io.in1    := mem_out
      amoAlu.io.in2    := Rrs2
      val amoAluOut = RegEnable(amoAlu.io.out, dcache_resp.valid)
      Mux(cs.amo, amoAluOut, wdataRrs2)
    } else wdataRrs2
  }

  // WB
  val csr = Module(new CSR)
  csr.io.pc              := pc
  csr.io.inst            := inst
  csr.io.inst_ilgl       := !cs.legal
  csr.io.inst_ret        := next_inst
  csr.io.inst_page_fault := icache_resp.valid && icache_resp.bits.page_fault // valid?
  csr.io.data_page_fault := dcache_resp.valid && dcache_resp.bits.page_fault // valid?
  csr.io.mem_addr        := mem_addr
  csr.io.mem_en          := dcache_req.valid
  csr.io.mem_rw          := mem_rw
  csr.io.mem_sz          := cs.mem_sz
  csr.io.cmd             := cs.csr_cmd
  csr.io.rdIsX0          := !rd.orR
  csr.io.rs1IsX0         := !rs1.orR                                         // 这个不只代表rs1不是x0，也意味着CSR??I的uimm不是0
  csr.io.in              := aluOut
  csr.io.jbr             := jbr
  csr.io.jbr_target      := jbr_target
  xcpt                   := csr.io.xcpt
  xtvec                  := csr.io.xtvec
  xret                   := csr.io.xret
  xepc                   := csr.io.xepc
  satp                   := csr.io.satp
  PRV                    := csr.io.PRV

  //                                                                                           v   only update rf when read in AMO
  rf.io.wen   := ((cs.fmt =/= FMT_S && cs.fmt =/= FMT_SB && cs.fmt =/= FMT_WIP && !cs.amo) || !mem_rw && dcache_resp.valid) && !xcpt
  rf.io.waddr := rd
  rf.io.wdata := MuxLookup(
    sel_rf_wdata,
    aluOut,
    Seq(RF_WDATA_MEM -> mem_out, RF_WDATA_PC4 -> pcp4, RF_WDATA_CSR -> csr.io.out, RF_WDATA_SCW -> ZXT(!sc_succ)),
  )

  // state machine (temp)
  val sFetch :: sExec :: Nil = Enum(2)
  val state                  = RegInit(sFetch)

  def go_fetch(next_pc: UInt): Unit = {
    // Who’s a good boy?
    pc        := next_pc
    inst      := NOP_INST
    state     := sFetch
    next_inst := true.B
  }

  icache_req.valid := false.B
  next_inst        := false.B
  exec_start       := false.B
  switch(state) {
    is(sFetch) {
      icache_req.valid := true.B
      when(xcpt) {
        pc        := xtvec
        next_inst := true.B
      }.elsewhen(icache_resp.valid) {
        // valid且没有exception（大概率是inst page fault，也有可能是没对齐）
        inst       := icache_resp.bits.data
        state      := sExec
        exec_start := true.B
      }
    }
    is(sExec) {
      when(xcpt) {
        go_fetch(xtvec)
      }.elsewhen(xret) {
        go_fetch(xepc)
      }.elsewhen(jbr) {
        go_fetch(jbr_target)
      }.elsewhen(
        Mux(
          cs.mul_div,
          mulDivValid,
          !cs.mem_en || !cs.amo && dcache_resp.valid || cs.amo && mem_valid_2 && dcache_resp.valid || is_sc && !sc_succ,
        )
      ) {
        // 不用memory，或者用，但是已经用完了 -> commit
        go_fetch(pcp4)
      }
    }
  }
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
