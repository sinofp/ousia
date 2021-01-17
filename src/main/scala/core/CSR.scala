package core

import chipsalliance.rocketchip.config.Config
import chisel3._
import chisel3.util._
import Consts._
import Util.HighLowHalf

class CSRIO(val mxLen: Int)(implicit c: Config) extends CoreBundle {
  val pc        = Input(UInt(xLen.W))
  val inst      = Input(UInt(xLen.W))
  val inst_ilgl = Input(Bool())
  val inst_ret  = Input(Bool())
  val mem_addr  = Input(UInt(xLen.W))
  val mem_en    = Input(Bool())
  val mem_rw    = Input(Bool())
  val mem_sz    = Input(UInt(SZ_MEM_SZ.W))
  val rs1IsX0   = Input(Bool())
  val rdIsX0    = Input(Bool())
  val cmd       = Input(UInt(SZ_CSR_CMD.W))
  val in        = Input(UInt(mxLen.W))
  val out       = Output(UInt(mxLen.W))
  val xcpt      = Output(Bool())
  val mtvec     = Output(UInt(xLen.W))
  val xret      = Output(Bool())
  val mepc      = Output(UInt(xLen.W))
}

class CSR(implicit c: Config) extends CoreModule {
  val mxLen = xLen // 这俩啥时候会不等？64位的32位模式？还有为啥会有ZXT？
  val io    = IO(new CSRIO(mxLen))

  val PRV       = RegInit(PRV_M)
  // Machine Information Registers
  val mvendorid = 0.U(mxLen.W)       // 非商业
  val marchid   = 0.U(mxLen.W)       // 未实现
  val mimpid    = 0.U(mxLen.W)
  val mhartid   = 0.U(mxLen.W)
  // Machine Trap Setup
  val mstatus   = Reg(new MStatus)
  val misa      = "b01".U ## 0.U((mxLen - 28).W) ## "I".map(_ - 'A').map(1 << _).reduce(_ | _).U(26.W)
  // medeleg
  // mideleg
  val mie       = Reg(new Mie(mxLen))
  val mtvec     = Reg(UInt(mxLen.W)) // 让操作系统设吧
  // mcounteren
  // Machine Trap Handling
  val mscratch  = Reg(UInt(mxLen.W))
  val mepc      = Reg(UInt(mxLen.W))
  val mcause    = Reg(UInt(mxLen.W))
  val mtval     = Reg(UInt(mxLen.W))
  val mip       = RegInit(0.U.asTypeOf(new Mip(mxLen)))

  // User Counter/Timers
  val cycle   = RegInit(0.U(64.W))
  val time    = RegInit(0.U(64.W))
  val timecmp = Reg(UInt(64.W))
  val instret = RegInit(0.U(64.W))

  // misc
  // BundleLiteral不顶用
  withReset(reset) {
    mstatus.mie := true.B
    mie.mtie    := true.B
    mie.meie    := true.B
    mie.msie    := true.B
  }
  when(io.inst_ret)(instret := instret + 1.U)
  time                      := time + 1.U // cycle?
  cycle                     := cycle + 1.U
  val csr_addr = io.inst(31, 20)

  // interrupt/exception
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
  val misalignedFetch = io.pc(1, 0).orR // C
  val timerInt        = false.B         //time >= timecmp
  val isEcall         = io.cmd === CSR_CMD_P && !csr_addr(0) && !csr_addr(8)
  val isEbreak        = io.cmd === CSR_CMD_P && csr_addr(0) && !csr_addr(8)
  val isXret          = io.cmd === CSR_CMD_P && !csr_addr(0) && csr_addr(8)

  // io
  io.xcpt  := mstatus.mie && (mie.mtie && timerInt || (mie.msie && (io.inst_ilgl || misalignedFetch || misalignedLoad || misalignedStore || isEcall || isEbreak)))
  io.xret  := isXret
  io.mtvec := mtvec
  io.mepc  := mepc

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
  ).map { case (k, v) => BitPat(k.U) -> v }
  val cmd_w      = io.cmd === CSR_CMD_W
  val cmd_s_or_c = io.cmd === CSR_CMD_S || io.cmd === CSR_CMD_C
  val ren        = cmd_s_or_c || cmd_w && !io.rdIsX0 // 不过有必要么？读取唯一的副作用就是写寄存器堆，rd == x0，本来也没法写啊？
  io.out := Mux(ren, Lookup(csr_addr, 0.U, csrFile), 0.U)

  // write
  val wen   = cmd_w || cmd_s_or_c && !io.rs1IsX0
  val wdata =
    MuxLookup(io.cmd, 0.U, Seq(CSR_CMD_W -> io.in, CSR_CMD_S -> (io.out | io.in), CSR_CMD_C -> (io.out & ~io.in)))

  when(io.xcpt) {
    mepc         := io.pc
    // output mtvec, pc := mtvec
    // todo 别忘了中断时最高位是1
    mcause       := MuxCase(
      0.U, {
        import Causes._
        Seq(
          io.inst_ilgl    -> illegal_instruction.U,
          isEcall         -> machine_ecall.U, // todo 其他特权级
          isEbreak        -> breakpoint.U,
          misalignedFetch -> misaligned_fetch.U,
          misalignedStore -> misaligned_store.U,
          misalignedLoad  -> misaligned_load.U,
        )
      },
    )
    mtval        := MuxCase(
      0.U,
      Seq(
        io.inst_ilgl    -> io.inst,
        misalignedFetch -> io.pc,
        misalignedStore -> io.mem_addr,
        misalignedLoad  -> io.mem_addr,
      ),
    )
    mstatus.mie  := false.B
    mstatus.mpie := mstatus.mie
    mstatus.mpp  := PRV
    PRV          := PRV_M
  }.elsewhen(isXret) {
    // pc设为epc
    mstatus.mie := mstatus.mpie
    PRV         := mstatus.mpp
  }.elsewhen(wen) {
    // 抽象出个方法？
    when(csr_addr === CSRs.mscratch.U)(mscratch := wdata)
      .elsewhen(csr_addr === CSRs.mstatus.U)(mstatus := wdata.asTypeOf(new MStatus))
      .elsewhen(csr_addr === CSRs.mie.U)(mie := wdata.asTypeOf(new Mie(mxLen)))
      .elsewhen(csr_addr === CSRs.mtvec.U)(mtvec := wdata)
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
