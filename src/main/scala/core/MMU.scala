package core

import chipsalliance.rocketchip.config.Config
import chisel3._
import chisel3.util._
import Consts._

class PTE extends Bundle {
  val ppn1 = UInt(12.W)
  val ppn0 = UInt(10.W)
  val rsw  = UInt(2.W) // ignored by hardware
  val d    = Bool()    // dirty
  val a    = Bool()    // accessed
  val g    = Bool()    // valid for all virtual address space, hardware accelerate
  val u    = Bool()    // user page
  val x    = Bool()
  val w    = Bool()
  val r    = Bool()    // rwx = 000, pointer to next table
  val v    = Bool()    // valid

  def ppn = ppn1 ## ppn0
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

class MMUReq(implicit c: Config) extends CacheReq {
  val satp = new Satp32
  val PRV  = UInt(2.W)
}

class MMUResp(implicit c: Config) extends CacheResp {
  val page_fault = Bool()
}

class MMUIO(implicit c: Config) extends CoreBundle {
  val abort = Input(Bool())
  val req   = Flipped(Valid(new MMUReq))
  val resp  = Valid(new MMUResp)
}

class MMUModuleIO(implicit c: Config) extends CoreBundle {
  val cpu = new MMUIO
  val wb  = new WishBoneIO
}

class MMUSimple(implicit c: Config) extends CoreModule {
  val io = IO(new MMUModuleIO)

  val satp = io.cpu.req.bits.satp

  val isMachine = Wire(Bool())
  isMachine := io.cpu.req.bits.PRV === PRV_M

  val trans_on = satp.mode.asBool && !isMachine

  val va      = io.cpu.req.bits.addr.asTypeOf(new VASv32)
  val pte     = RegNext(io.wb.rdata.asTypeOf(new PTE)) // ack后下一个周期可用，RegEnable？
  val pte_ppn = pte.ppn
  dontTouch(pte_ppn) // todo delete

  val translate1 = (ppn: UInt, vpni: UInt) => Cat(ppn, vpni, 0.U(2.W))

  val addr       = Reg(UInt(34.W))
  val level      = Reg(UInt(1.W)) // todo Generalize
  val last_round = Reg(Bool())

  io.wb.cyc   := false.B
  io.wb.stb   := false.B
  io.wb.sel   := Mux(last_round, io.cpu.req.bits.sel, "b1111".U)
  io.wb.we    := last_round && io.cpu.req.bits.we // 最后一轮使用cpu给的we，否则就是false，读取
  io.wb.wdata := io.cpu.req.bits.data
  io.wb.addr  := addr(31, 0)                      // todo

  io.cpu.resp.valid     := false.B
  io.cpu.resp.bits.data := io.wb.rdata

  io.cpu.resp.bits.page_fault := false.B
  def page_fault(reason: String): Unit = {
    io.cpu.resp.valid           := true.B
    io.cpu.resp.bits.page_fault := true.B
    state                       := sIdle
    printf(p"=E page-fault: $reason\n")
  }

  val sIdle :: sWait :: sRead :: Nil = Enum(3)
  val state                          = RegInit(sIdle)
  switch(state) {
    is(sIdle) {
      when(trans_on && io.cpu.req.valid) {
        state      := sWait
        addr       := translate1(satp.ppn, va.vpn1)
        level      := 1.U
        last_round := false.B
      }.otherwise {
        io.wb.cyc  := io.cpu.req.valid     //&& !io.cpu.abort
        io.wb.stb  := io.cpu.req.valid     //&& !io.cpu.abort
        io.wb.sel  := io.cpu.req.bits.sel
        io.wb.addr := io.cpu.req.bits.addr // 不用reg addr
        io.wb.we   := io.cpu.req.bits.we

        io.cpu.resp.valid := io.wb.ack //|| io.cpu.abort
      }
    }
    is(sWait) {
      io.wb.cyc := true.B
      io.wb.stb := true.B
      when(io.wb.ack) {
        when(last_round) {
          state             := sIdle
          io.cpu.resp.valid := true.B
        }.otherwise {
          state := sRead
        }
      }
    }
    is(sRead) {
      io.wb.cyc := false.B
      io.wb.stb := false.B
      when(!pte.v || !pte.r && pte.w) {
        page_fault("PTE is not valid") // step 3
      }.otherwise {
        // PTE is valid
        when(pte.r || pte.x) {
          // step 5, a leaf PTE has been found
          //todo pte.twxu

          // step 6
          when(level === 1.U && pte.ppn0.orR)(page_fault("misaligned superpage"))

          // todo step 7
          // when(!pte.a || io.cpu.req.bits.we && pte.d)(page_fault("a = 0 || store && d = 0"))

          // step 8, successful
          when(level === 1.U) {
            addr := Cat(pte.ppn1, va.vpn0, va.offset)
          }.otherwise {
            addr := pte.ppn ## va.offset
          }
          state := sWait

          last_round := true.B
        }.otherwise {
          // PTE is a pointer to the next level of the page table
          when(level === 0.U)(page_fault("i < 0"))

          level := 0.U // level -= 1
          addr  := translate1(pte.ppn, va.vpn0)
          state := sWait
        }
      }
    }
  }
}
