package core

import chipsalliance.rocketchip.config.Config
import chisel3._
import chisel3.util._
import Consts._

class CSRIO(val mxLen: Int)(implicit c: Config) extends CoreBundle {
  val csr     = Input(UInt(12.W))
  val rs1IsX0 = Input(Bool())
  val rdIsX0  = Input(Bool())
  val cmd     = Input(UInt(SZ_CSR_CMD.W))
  val in      = Input(UInt(mxLen.W))
  val out     = Output(UInt(mxLen.W))
}

class CSR(implicit c: Config) extends CoreModule {
  val mxLen = xLen // 这俩啥时候会不等？64位的32位模式？还有为啥会有ZXT？
  val io    = IO(new CSRIO(mxLen))

  val misa      = "00000000100000000000100000_0000_10h".reverse.U // I,U,32
  val mvendorid = 0.U(mxLen.W)                                    // 非商业
  val marchid   = 0.U(mxLen.W)                                    // 未实现
  val mimpid    = 0.U(mxLen.W)
  val mhartid   = 0.U(mxLen.W)
  val mscratch  = Reg(UInt(mxLen.W))

  // read
  val csrFile    = Seq(
    CSRs.misa      -> misa,
    CSRs.mvendorid -> mvendorid,
    CSRs.marchid   -> marchid,
    CSRs.mimpid    -> mimpid,
    CSRs.mhartid   -> mhartid,
    CSRs.mscratch  -> mscratch,
  ).map { case (k, v) => BitPat(k.U) -> v }
  val cmd_w      = io.cmd === CSR_CMD_W
  val cmd_s_or_c = io.cmd === CSR_CMD_S || io.cmd === CSR_CMD_C
  val ren        = cmd_s_or_c || cmd_w && !io.rdIsX0 // 不过有必要么？读取唯一的副作用就是写寄存器堆，rd == x0，本来也没法写啊？
  io.out := Mux(ren, Lookup(io.csr, 0.U, csrFile), 0.U)

  // write
  val wen   = cmd_w || cmd_s_or_c && !io.rs1IsX0
  val wdata =
    MuxLookup(io.cmd, 0.U, Seq(CSR_CMD_W -> io.in, CSR_CMD_S -> (io.out | io.in), CSR_CMD_C -> (io.out & ~io.in)))
  when(wen) {
    when(io.csr === CSRs.mscratch.U) {
      mscratch := wdata
    }
  }
}
