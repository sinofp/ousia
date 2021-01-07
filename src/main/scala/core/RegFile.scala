package core

import chipsalliance.rocketchip.config.Config
import chisel3._
import chisel3.util.experimental.BoringUtils

class RegFile(readPorts: Int)(implicit c: Config) extends CoreModule {
  require(readPorts >= 0)
  val io = IO(new Bundle() {
    val wen   = Input(Bool())
    val waddr = Input(UInt(5.W))
    val wdata = Input(UInt(32.W))
    val raddr = Input(Vec(readPorts, UInt(5.W)))
    val rdata = Output(Vec(readPorts, UInt(32.W)))
  })

  val reg = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))

  import io._

  when(wen) {
    reg(waddr) := wdata
  }

  for (i <- 0 until readPorts)
    when(raddr(i) === 0.U) {
      rdata(i) := 0.U
    }.otherwise {
      rdata(i) := reg(raddr(i))
    }

  if (rf2Top)
    "zero ra sp gp tp t0 t1 t2 s0 s1 a0 a1 a2 a3 a4 a5 a6 a7 s2 s3 s4 s5 s6 s7 s8 s9 s10 s11 t3 t4 t5 t6"
      .split(' ')
      .zipWithIndex
      .foreach { case (str, i) => BoringUtils.addSource(reg(i), str) }
}
