import chisel3._
import chisel3.util.log2Up

class ALUGeneric(ops: (UInt, (UInt, UInt) => UInt)*) extends CoreModule {
  val io = IO(new Bundle() {
    val in1 = Input(UInt(xLen.W))
    val in2 = Input(UInt(xLen.W))
    val fn  = Input(UInt(log2Up(ops.size).W))
    val out = Output(UInt(xLen.W))
  })

  val result = {
    val _ops = ops.map { case (op, f) => (op, f(io.in1, io.in2)) }
    util.MuxLookup(io.fn, 0.U, _ops).asUInt
  }

  io.out := result(xLen - 1, 0)
}

import Consts._

class ALU
    extends ALUGeneric(
      ALU_ADD  -> ((a: UInt, b: UInt) => a + b),
      ALU_SL   -> ((a: UInt, b: UInt) => a << b(4, 0)),
      ALU_SEQ  -> ((a: UInt, b: UInt) => a === b),
      ALU_SNE  -> ((a: UInt, b: UInt) => a =/= b),
      ALU_XOR  -> ((a: UInt, b: UInt) => a ^ b),
      ALU_SR   -> ((a: UInt, b: UInt) => a >> b),
      ALU_OR   -> ((a: UInt, b: UInt) => a | b),
      ALU_AND  -> ((a: UInt, b: UInt) => a & b),
      ALU_SUB  -> ((a: UInt, b: UInt) => a - b),
      ALU_SRA  -> ((a: UInt, b: UInt) => (a.asSInt >> b(4, 0)).asUInt),
      ALU_SLT  -> ((a: UInt, b: UInt) => a.asSInt < b.asSInt),
      ALU_SGE  -> ((a: UInt, b: UInt) => a.asSInt >= b.asSInt),
      ALU_SLTU -> ((a: UInt, b: UInt) => a < b),
      ALU_SGEU -> ((a: UInt, b: UInt) => a >= b),
    )

class AMOALU
    extends ALUGeneric(
      ALU_ADD  -> ((a: UInt, b: UInt) => a + b),
      ALU_XOR  -> ((a: UInt, b: UInt) => a ^ b),
      ALU_OR   -> ((a: UInt, b: UInt) => a | b),
      ALU_AND  -> ((a: UInt, b: UInt) => a & b),
      ALU_SLT  -> ((a: UInt, b: UInt) => a.asSInt < b.asSInt),
      ALU_SLTU -> ((a: UInt, b: UInt) => a < b),
      ALU_SUB  -> ((_: UInt, b: UInt) => b),// SWAP
    )
