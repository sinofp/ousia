//package core
//
//import chipsalliance.rocketchip.config.Config
//import chisel3._
//import _root_.core.Consts.SZ_CSR_CMD
//import chisel3.util.MuxLookup
//
//class CSRFile(implicit c: Config) extends CoreModule {
//  val io = IO(new Bundle {
//    val addr = Input(UInt(12.W))
//    val cmd = Input(UInt(SZ_CSR_CMD.W))
//    val wdata = Input(UInt(xLen.W))
//    val rdata = Output(UInt(xLen.W))
//  })
//
//  val mxLen = xLen
//  val misa = RegInit(UInt(mxLen.W), "00000000100000000000100000_0000_10h".reverse.U) // I,U,32
//  val mvendorid = RegInit(UInt(mxLen.W), 0.U) // 非商业
//  val marchid = RegInit(UInt(mxLen.W), 0.U) // 未实现，这些用wire，还是根本就不显式写出，融合到Mux里？
//  val mimpid = RegInit(UInt(mxLen.W), 0.U)
//  val mhartid = RegInit(UInt(mxLen.W), 0.U)
//  val mstatus = Reg(new MStatus)
//  val mscratch = Reg(UInt(mxLen.W))
//  // todo mtvec medeleg mideleg mip mie mtime mtimecmp...
//
//  def int2UInt (x: Int) : UInt= x.U
//  io.rdata := MuxLookup(io.addr, 0.U, Seq(
//    CSRs.misa -> misa,
//    CSRs.mvendorid -> mvendorid,
//    CSRs.marchid -> marchid,
//    CSRs.mimpid -> mimpid,
//    CSRs.mhartid -> mhartid,
//    CSRs.mstatus -> mstatus.asUInt,
//    CSRs.mscratch -> mscratch,
//  ))
//}
//
//class MStatus extends Bundle {
//  // Figure 3.6
//  val sd = Bool()
//  val zero0 = UInt(8.W)
//  val tsr = Bool()
//  val tw = Bool()
//  val tvm = Bool()
//  val mxr = Bool()
//  val sum = Bool()
//  val mprv = Bool()
//  val xs = UInt(2.W)
//  val fs = UInt(2.W)
//  val mpp = UInt(2.W)
//  val zero1 = UInt(2.W)
//  val spp = UInt(1.W)
//  val mpie = Bool()
//  val zero2 = Bool()
//  val spie = Bool()
//  val upie = Bool()
//  val mie = Bool()
//  val zero3 = Bool()
//  val sie = Bool()
//  val uie = Bool()
//}
