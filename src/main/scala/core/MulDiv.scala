package core

import chipsalliance.rocketchip.config.Config
import chisel3._
import ALU._
import chisel3.util._
import Util._

class MulDivIO(implicit c: Config) extends CoreBundle {
  val start = Input(Bool())
  val fn    = Input(UInt(SZ_ALU_FN.W))
  val in1   = Input(UInt(xLen.W))
  val in2   = Input(UInt(xLen.W))
  val out   = Valid(Output(UInt(xLen.W)))
}

class MulDiv(implicit c: Config) extends CoreModule {
  val io = IO(new MulDivIO)

  val mul = Module(new Mul)
  val div = Module(new Div)

  val use_div = DecodeLogic(io.fn, Seq(FN_DIV, FN_DIVU, FN_REM, FN_REMU), Seq(FN_MUL, FN_MULH, FN_MULHU, FN_MULHSU))

  mul.io.start := DontCare
  mul.io.fn    := io.fn
  mul.io.in1   := io.in1
  mul.io.in2   := io.in2
  div.io.start := io.start
  div.io.fn    := io.fn
  div.io.in1   := io.in1
  div.io.in2   := io.in2
  io.out       := Mux(use_div.asBool, div.io.out, mul.io.out)
}

class Mul(implicit c: Config) extends CoreModule {
  val io = IO(new MulDivIO)

  val in1 = Mux(io.fn === FN_MULHU, ZXT(io.in1, 64), SXT(io.in1, 64))
  val in2 = Mux(io.fn === FN_MULHU || io.fn === FN_MULHSU, ZXT(io.in2, 64), SXT(io.in2, 64))

  val res = (in1 * in2)(63, 0)

  io.out.valid := true.B
  io.out.bits  := Mux(io.fn === FN_MUL, res.lowHalf, res.highHalf)
}

class Div(implicit c: Config) extends CoreModule {
  val io = IO(new MulDivIO)

  val neg1             = io.in1(xLen - 1)
  val neg2             = io.in2(xLen - 1)
  val signed           = io.fn === FN_DIV || io.fn === FN_REM
  val is_rem           = io.fn === FN_REM || io.fn === FN_REMU
  val not_same_sign    = neg1 ^ neg2
  val division_by_zero = !io.in2.orR

  // 最后一次的余数部分不需要位移，但每次都位移比较方便，所以干脆多记录一位
  val res     = Reg(UInt((xLen * 2 + 1).W))
  val divisor = Reg(UInt(xLen.W))
  val cnt     = Reg(UInt(6.W))

  val busy  = RegInit(Bool(), false.B)
  val busy2 = RegNext(busy)

  def tilde_plus_one(x: UInt): UInt = (~x).asUInt + 1.U
  def MuxNegIf(cond: Bool, x: UInt) = Mux(cond, tilde_plus_one(x), x)

  // 都当成正数来减，所以sub_out有xLen+1位，最高位是符号位
  val sub_out = 0.U ## res(xLen * 2 - 1, xLen) - 0.U ## divisor
  val mux_out = Mux(sub_out(xLen), res(xLen * 2 - 1, xLen), sub_out(xLen - 1, 0))
  when(io.start) {
    // todo: add cache for `DIV xx, yy, zz; REM ww, yy, zz`
    res     := ZXT(MuxNegIf(signed && neg1, io.in1), xLen * 2 + 1)
    divisor := MuxNegIf(signed && neg2, io.in2)
    cnt     := 0.U
    busy    := !division_by_zero // 其实直接给true就行，但是那样cnt会没有意义地自增，心烦
  }.elsewhen(busy) {
    res  := mux_out(xLen - 1, 0) ## res(xLen - 1, 0) ## ~sub_out(xLen)
    cnt  := cnt + 1.U
    busy := cnt =/= xLen.U // finish
  }

  val quotient  = res(xLen - 1, 0)
  val remainder = res(xLen * 2, xLen + 1)
//  dontTouch(quotient) // trace
//  dontTouch(remainder)

  io.out.valid := !busy && busy2 || division_by_zero // 1-cycle ready
  io.out.bits  := Mux(
    division_by_zero,
    Mux(is_rem, io.in1, ("b" + "1" * xLen).U),
    Mux(
      signed,
      Mux(is_rem, MuxNegIf(neg1, remainder), MuxNegIf(not_same_sign, quotient)),
      Mux(is_rem, remainder, quotient),
    ),
  )
}
