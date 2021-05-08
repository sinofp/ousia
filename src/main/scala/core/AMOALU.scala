package core

import chipsalliance.rocketchip.config.Config
import chisel3._
import chisel3.util.MuxCase
import ALU._
import Consts._

class AMOALUIO(implicit c: Config) extends CoreBundle {
  val fn     = Input(UInt(SZ_ALU_FN.W))
  val funct5 = Input(UInt(5.W))
  val in1    = Input(UInt(xLen.W))
  val in2    = Input(UInt(xLen.W))
  val out    = Output(UInt(xLen.W))
}

class AMOALU(implicit c: Config) extends CoreModule {
  val io = IO(new AMOALUIO)

  val alu = Module(new ALU)
  alu.io.fn      := io.fn
  alu.io.in1     := io.in1
  alu.io.in2     := io.in2
  alu.io.dw      := false.B
  alu.io.cmp_out := DontCare

  // move into decode?
  import io._
  out := MuxCase(
    alu.io.out,
    Seq(
      (funct5 === AMO_MIN_W_FUNCT5 || funct5 === AMO_MINU_W_FUNCT5) -> Mux(alu.io.out(0), in1, in2),
      (funct5 === AMO_MAX_W_FUNCT5 || funct5 === AMO_MAXU_W_FUNCT5) -> Mux(alu.io.out(0), in2, in1),
      (funct5 === AMO_SWAP_W_FUNCT5)                                -> in2,
    ),
  )
}
