import chisel3._
import chisel3.util.log2Up
import firrtl.ir.ReadUnderWrite

class BRAM1[T <: Data](depth: Int, dataType: T) extends Module {
  val we    = IO(Input(Bool()))
  val addr  = IO(Input(UInt(log2Up(dataType.getWidth).W)))
  val write = IO(Input(dataType))  // din
  val read  = IO(Output(dataType)) // dout

  val mem = SyncReadMem(depth, dataType, ReadUnderWrite.New)

  mem.write(addr, write)
  read := mem.read(addr)
}

class BRAM[T <: Data](depth: Int, dataType: T) extends Module {
  val we    = IO(Input(Bool()))
  val addr  = IO(Input(UInt(log2Up(dataType.getWidth).W)))
  val write = IO(Input(dataType))  // din
  val read  = IO(Output(dataType)) // dout

  val mem  = Reg(Vec(depth, dataType))
  val data = Reg(dataType)

  when(we) {
    mem(addr) := write
    data      := write
  } otherwise {
    data := mem(addr)
  }
  read := data
}

object BRAM {
  def apply[T <: Data](size: Int, dataType: T): BRAM[T] = Module(new BRAM(size, dataType))
}
