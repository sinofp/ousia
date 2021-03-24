package core

import chipsalliance.rocketchip.config.Config
import chisel3._
import chisel3.util._
import Util.HighLowHalf
import Consts._
import Instructions._
import chisel3.util.experimental.BoringUtils

class CSRIO(val mxLen: Int)(implicit c: Config) extends CoreBundle {
  val pc         = Input(UInt(xLen.W))
  val inst       = Input(UInt(xLen.W))
  val inst_ilgl  = Input(Bool())
  val inst_ret   = Input(Bool())
  val mem_addr   = Input(UInt(xLen.W))
  val mem_en     = Input(Bool())
  val mem_rw     = Input(Bool())
  val mem_sz     = Input(UInt(SZ_MEM_SZ.W))
  val rs1IsX0    = Input(Bool())
  val rdIsX0     = Input(Bool())
  val jbr        = Input(Bool())
  val jbr_target = Input(UInt(xLen.W))
  val cmd        = Input(UInt(SZ_CSR_CMD.W))
  val in         = Input(UInt(mxLen.W))
  val out        = Output(UInt(mxLen.W))
  val xcpt       = Output(Bool())
  val mtvec      = Output(UInt(xLen.W))
  val xret       = Output(Bool())
  val xepc       = Output(UInt(xLen.W))
}

class CSR(implicit c: Config) extends CoreModule {
  val mxLen = xLen // 这俩啥时候会不等？64位的32位模式？还有为啥会有ZXT？
  val sxLen = xLen
  val io    = IO(new CSRIO(mxLen))

  val PRV          = RegInit(PRV_M)
  val isMachine    = PRV === PRV_M
  val isSupervisor = PRV === PRV_S
  val isUser       = PRV === PRV_U

  // Machine Information Registers
  val mvendorid = 0.U(mxLen.W)       // 非商业
  val marchid   = 0.U(mxLen.W)       // 未实现
  val mimpid    = 0.U(mxLen.W)
  val mhartid   = 0.U(mxLen.W)
  // Machine Trap Setup
  val mstatus   = RegInit(0.U.asTypeOf(new MStatus))
  val misa      = 1.U(2.W) ## 0.U((mxLen - 28).W) ## "I".map(_ - 'A').map(1 << _).reduce(_ | _).U(26.W)
  val medeleg   = Reg(UInt(mxLen.W)) // todo
  val mideleg   = Reg(UInt(mxLen.W)) // todo
  val mie       = Reg(new Mie(mxLen))
  val mtvec     = Reg(UInt(mxLen.W))
  // mcounteren
  // Machine Trap Handling
  val mscratch  = Reg(UInt(mxLen.W))
  val mepc      = Reg(UInt(mxLen.W))
  val mcause    = Reg(UInt(mxLen.W))
  val mtval     = Reg(UInt(mxLen.W))
  val mip       = RegInit(0.U.asTypeOf(new Mip(mxLen)))
  // Supervisor Trap Handling
  val sepc      = Reg(UInt(sxLen.W))
  val scause    = Reg(UInt(sxLen.W)) // todo
  val stval     = Reg(UInt(sxLen.W)) // todo

  // User Counter/Timers
  val cycle   = RegInit(0.U(64.W))
  val time    = RegInit(0.U(64.W))
  val timecmp = Reg(UInt(64.W))
  val instret = RegInit(0.U(64.W))

  // Paging
  val satp = RegInit(0.U.asTypeOf(new Satp32))
  BoringUtils.addSource(satp, "satp")

  // misc
  when(io.inst_ret)(instret := instret + 1.U)
  time                      := time + 1.U // cycle?
  cycle                     := cycle + 1.U
  val csr_addr = io.inst(31, 20)

  // exception
  val misalignedLoad  = io.mem_en && !io.mem_rw && MuxLookup(
    io.mem_sz,
    false.B,
    Seq(MEM_HU -> io.mem_addr(0), MEM_H -> io.mem_addr(0), MEM_W -> io.mem_addr(1, 0).orR),
  )
  val misalignedStore = io.mem_en && io.mem_rw && MuxLookup(
    io.mem_sz,
    false.B,
    Seq(MEM_H -> io.mem_addr(0), MEM_W -> io.mem_addr(1, 0).orR),
  )
  val misalignedFetch = io.jbr && io.jbr_target(1, 0).orR // C

  val isEcall :: isEbreak :: isMRet :: isSRet :: isSFenceVMA :: Nil =
    DecodeLogic(
      io.inst,
      Seq.fill(5)(N),
      Seq(
        ECALL      -> Seq(Y, N, N, N, N),
        EBREAK     -> Seq(N, Y, N, N, N),
        MRET       -> Seq(N, N, Y, N, N),
        SRET       -> Seq(N, N, N, Y, N),
        SFENCE_VMA -> Seq(N, N, N, N, Y),
      ),
    ).map(_.asBool)

  val illegalInstruction =
    io.inst_ilgl || mstatus.tvm && (isSFenceVMA || csr_addr === CSRs.satp.U) || mstatus.tsr && isSRet
  val exception          = illegalInstruction || misalignedFetch || misalignedLoad || misalignedStore || isEcall || isEbreak

  // interrupt
  mip.stip := time >= timecmp
  mip.mtip := time >= timecmp
  val s_software_int = mie.ssie && mip.ssip
  val m_software_int = mie.msie && mip.ssip
  val s_timer_int    = mie.stie && mip.stip
  val m_timer_int    = mie.mtie && mip.mtip
  val s_external_int = mie.seie && mip.seip
  val m_external_int = mie.meie && mip.meip
  val interrupt      =
    mstatus.mie && (s_software_int || m_software_int || s_timer_int || m_timer_int || s_external_int || m_external_int)

  // io
  io.xcpt  := interrupt || exception
  io.xret  := !io.xcpt && (isMRet || isSRet) // 不是xcpt可以隐藏到其他Mux的顺序里，但为了清楚放在这
  io.mtvec := mtvec
  io.xepc  := Mux(isSRet, sepc, mepc)

  // read
  val csrFile    = Seq(
    CSRs.mvendorid -> mvendorid,
    CSRs.marchid   -> marchid,
    CSRs.mimpid    -> mimpid,
    CSRs.mhartid   -> mhartid,
    CSRs.mstatus   -> mstatus.asUInt,
    CSRs.misa      -> misa,
    CSRs.mie       -> mie.asUInt,
    CSRs.mtvec     -> mtvec,
    CSRs.mscratch  -> mscratch,
    CSRs.mepc      -> mepc,
    CSRs.mcause    -> mcause,
    CSRs.mtval     -> mtval,
    CSRs.mip       -> mip.asUInt,
    CSRs.time      -> time.lowHalf,
    CSRs.timeh     -> time.highHalf,
    CSRs.mcycle    -> cycle.lowHalf,
    CSRs.mcycleh   -> cycle.highHalf,
    CSRs.minstret  -> instret.lowHalf,
    CSRs.minstreth -> instret.highHalf,
    CSRs.sepc      -> sepc,
    CSRs.satp      -> satp.asUInt,
  ).map { case (k, v) => BitPat(k.U) -> v }
  val cmd_w      = io.cmd === CSR_CMD_W
  val cmd_s_or_c = io.cmd === CSR_CMD_S || io.cmd === CSR_CMD_C
  val ren        = cmd_s_or_c || cmd_w && !io.rdIsX0 // 不过有必要么？读取唯一的副作用就是写寄存器堆，rd == x0，本来也没法写啊？
  io.out := Mux(ren, Lookup(csr_addr, 0.U, csrFile), 0.U)

  // trap register content
  val cause_int  = MuxCase(
    1.U,
    Seq(m_software_int -> 3.U, s_timer_int -> 5.U, m_timer_int -> 7.U, s_external_int -> 9.U, m_external_int -> 11.U),
  )
  val cause_xcpt = MuxCase(
    0.U,
    Seq(
      illegalInstruction -> Causes.illegal_instruction.U,
      isEcall            -> (Causes.user_ecall.U + PRV),
      isEbreak           -> Causes.breakpoint.U,
      misalignedFetch    -> Causes.misaligned_fetch.U,
      misalignedStore    -> Causes.misaligned_store.U,
      misalignedLoad     -> Causes.misaligned_load.U,
    ),
  )
  val tval       = MuxCase(
    0.U,
    Seq(
      io.inst_ilgl    -> io.inst,
      misalignedFetch -> io.jbr_target,
      misalignedStore -> io.mem_addr,
      misalignedLoad  -> io.mem_addr,
    ),
  )

  // write
  val wen   = cmd_w || cmd_s_or_c && !io.rs1IsX0
  val wdata =
    MuxLookup(io.cmd, 0.U, Seq(CSR_CMD_W -> io.in, CSR_CMD_S -> (io.out | io.in), CSR_CMD_C -> (io.out & ~io.in)))

  when(io.xcpt) {
    mepc         := io.pc
    mcause       := Mux(interrupt, (1 << mxLen).asUInt | cause_int, cause_xcpt)
    mtval        := tval
    mstatus.mie  := false.B
    mstatus.mpie := mstatus.mie
    mstatus.mpp  := PRV
    PRV          := PRV_M
  }.elsewhen(isMRet) {
    mstatus.mie := mstatus.mpie
    PRV         := mstatus.mpp
    // spec说xPIE is set to 1，但设成1以后rv32ui-p-illegal会在mret卡住
    // mstatus.mpie := 1.U
    mstatus.mpp := PRV_U
  }.elsewhen(isSRet) {
    mstatus.sie := mstatus.spie
    PRV         := mstatus.spp
    // mstatus.spie := 1.U
    mstatus.spp := PRV_U
  }.elsewhen(wen) {
    // 抽象出个方法？
    when(csr_addr === CSRs.mscratch.U)(mscratch := wdata)
      .elsewhen(csr_addr === CSRs.mstatus.U)(mstatus := wdata.asTypeOf(new MStatus))
      .elsewhen(csr_addr === CSRs.mie.U)(mie := wdata.asTypeOf(new Mie(mxLen)))
      .elsewhen(csr_addr === CSRs.mtvec.U)(mtvec := wdata(mxLen - 1, 2) ## 0.U(2.W)) // 只支持直接模式
      .elsewhen(csr_addr === CSRs.mepc.U)(mepc := wdata)
      .elsewhen(csr_addr === CSRs.mcause.U)(mcause := wdata)
      .elsewhen(csr_addr === CSRs.mtval.U)(mtval := wdata)
      .elsewhen(csr_addr === CSRs.mip.U)(mip := wdata.asTypeOf(new Mip(mxLen)))
      .elsewhen(csr_addr === CSRs.time.U)(time := time.highHalf ## wdata)
      .elsewhen(csr_addr === CSRs.timeh.U)(time := wdata ## time.lowHalf)
      .elsewhen(csr_addr === CSRs.mcycle.U)(cycle := cycle.highHalf ## wdata)
      .elsewhen(csr_addr === CSRs.mcycleh.U)(cycle := wdata ## cycle.lowHalf)
      .elsewhen(csr_addr === CSRs.minstret.U)(instret := instret.highHalf ## wdata)
      .elsewhen(csr_addr === CSRs.minstreth.U)(instret := wdata ## instret.lowHalf)
      .elsewhen(csr_addr === CSRs.sepc.U)(sepc := wdata)
      .elsewhen(csr_addr === CSRs.satp.U)(satp := wdata.asTypeOf(new Satp32))
  }
}

class MStatus extends Bundle {
  // Figure 3.6
  val sd    = Bool()
  val zero0 = UInt(8.W)
  val tsr   = Bool()
  val tw    = Bool()
  val tvm   = Bool()
  val mxr   = Bool()
  val sum   = Bool()
  val mprv  = Bool()
  val xs    = UInt(2.W)
  val fs    = UInt(2.W)
  val mpp   = UInt(2.W)
  val zero1 = UInt(2.W)
  val spp   = UInt(1.W)
  val mpie  = Bool()
  val zero2 = Bool()
  val spie  = Bool()
  val upie  = Bool()
  val mie   = Bool()
  val zero3 = Bool()
  val sie   = Bool()
  val uie   = Bool()
}

class Mip(val mxLen: Int) extends Bundle {
  val zero0 = UInt((mxLen - 12).W)
  val meip  = Bool()
  val zero1 = Bool()
  val seip  = Bool()
  val ueip  = Bool()
  val mtip  = Bool()
  val zero2 = Bool()
  val stip  = Bool()
  val utip  = Bool()
  val msip  = Bool()
  val zero3 = Bool()
  val ssip  = Bool()
  val usip  = Bool()
}

class Mie(val mxLen: Int) extends Bundle {
  val zero0 = UInt((mxLen - 12).W)
  val meie  = Bool()
  val zero1 = Bool()
  val seie  = Bool()
  val ueie  = Bool()
  val mtie  = Bool()
  val zero2 = Bool()
  val stie  = Bool()
  val utie  = Bool()
  val msie  = Bool()
  val zero3 = Bool()
  val ssie  = Bool()
  val usie  = Bool()
}

class Satp32 extends Bundle {
  val mode = UInt(1.W)
  val asid = UInt(9.W)
  val ppn  = UInt(22.W)
}
