package core

import chipsalliance.rocketchip.config.Config
import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils

class PTE extends Bundle {
  val ppn = UInt(22.W)
  val rsw = UInt(2.W) // ignored by hardware
  val d   = Bool()    // dirty
  val a   = Bool()    // accessed
  val g   = Bool()    // valid for all virtual address space, hardware accelerate
  val u   = Bool()    // user page
  val x   = Bool()
  val w   = Bool()
  val r   = Bool()    // rwx = 000, pointer to next table
  val v   = Bool()    // valid
}

class VASv32 extends Bundle {
  val vpn1   = UInt(10.W)
  val vpn0   = UInt(10.W)
  val offset = UInt(12.W)
}

class PASv32 extends Bundle {
  val ppn    = UInt(22.W)
  val offset = UInt(12.W)
}

class MMUSimple(implicit c: Config) extends CoreModule {
  val io = IO(new CacheModuleIO)

  val satp = Wire(new Satp32)
  satp := 42.U.asTypeOf(new Satp32)
  BoringUtils.addSink(satp, "satp") // todo input

  val sIdle :: sWait :: sRead :: Nil = Enum(3)
  val state                          = RegInit(sIdle)

  val va  = io.cpu.req.bits.addr.asTypeOf(new VASv32)
  val pte = RegNext(io.wb.rdata.asTypeOf(new PTE)) // ack后下一个周期可用，RegEnable？

  val translate1 = (ppn: UInt, vpni: UInt) => Cat(ppn, vpni, 0.U(2.W))

  val addr  = Reg(UInt(34.W))
  val level = Reg(UInt(1.W)) // todo Generalize
  val done  = Reg(Bool())    // 能不能直接用level？按spec是不允许，但真的会碰到level应该是负数的情况么？

  io.wb.cyc   := false.B
  io.wb.stb   := false.B
  io.wb.sel   := "b1111".U
  io.wb.we    := false.B
  io.wb.wdata := 42.U
  io.wb.addr  := addr(31, 0) // todo

  io.cpu.resp.valid     := false.B
  io.cpu.resp.bits.data := io.wb.rdata

  switch(state) {
    is(sIdle) {
      when(satp.mode.asBool && io.cpu.req.valid) {
        state := sWait
        addr  := translate1(satp.ppn, va.vpn1)
        level := 1.U
        done  := false.B
      }.otherwise {
        // {
        io.wb.cyc   := io.cpu.req.valid && !io.cpu.abort
        io.wb.stb   := io.cpu.req.valid && !io.cpu.abort
        io.wb.sel   := io.cpu.req.bits.sel
        io.wb.addr  := io.cpu.req.bits.addr // 不用reg addr
        io.wb.wdata := io.cpu.req.bits.data
        io.wb.we    := io.cpu.req.bits.we

        io.cpu.resp.valid := io.wb.ack || io.cpu.abort
      }
    }
    is(sWait) {
      io.wb.cyc := true.B
      io.wb.stb := true.B
      when(io.wb.ack) {
        state := sRead
      }
    }
    is(sRead) {
      when(done) {
        io.cpu.resp.valid := true.B
      }.elsewhen(level === 1.U) {
        // PTE is a pointer to the next level of the page table
        level := 0.U
        addr  := translate1(pte.ppn, va.vpn0)
        state := sWait

        io.wb.cyc := false.B
        io.wb.stb := false.B
      }.elsewhen(level === 0.U) {
        // A leaf PTE has been found
        addr  := pte.ppn ## va.offset
        state := sWait

        io.wb.cyc := false.B
        io.wb.stb := false.B

        done := true.B
      }
    }
  }
}
