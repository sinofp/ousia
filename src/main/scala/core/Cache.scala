package core

import chipsalliance.rocketchip.config.Config
import chisel3._
import chisel3.util._

class CacheReq(implicit c: Config) extends CoreBundle {
  val addr = UInt(xLen.W)
  val data = UInt(xLen.W)
  val we   = Bool()
  val sel  = UInt((xLen / 8).W)
}

class CacheResp(implicit c: Config) extends CoreBundle {
  val data = UInt(xLen.W)
}

class CacheIO(implicit val c: Config) extends Bundle {
  val abort = Input(Bool())
  val req   = Flipped(Valid(new CacheReq))
  val resp  = Valid(new CacheResp)
}

class CachePassThrough(implicit c: Config) extends CoreModule {
  val io = IO(new Bundle {
    val cpu = new CacheIO
    val wb  = new WishBoneIO
  })

  io.wb.cyc             := io.cpu.req.valid
  io.wb.stb             := io.cpu.req.valid
  io.wb.sel             := io.cpu.req.bits.sel
  io.wb.addr            := io.cpu.req.bits.addr
  io.wb.wdata           := io.cpu.req.bits.data
  io.wb.we              := io.cpu.req.bits.we
  io.cpu.resp.valid     := io.wb.ack || io.cpu.abort
  io.cpu.resp.bits.data := io.wb.rdata
}

class CacheDirectMap(val c_index: Int)(implicit c: Config) extends CoreModule {
  val io = IO(new Bundle {
    val cpu = new CacheIO
    val wb  = new WishBoneIO
  })

  val t_width  = xLen - c_index - 2 // 1 block = 4 byte
//  val v = RegInit(0.U((1 << c_index).W))
  val validMem = SyncReadMem(1 << c_index, Bool())
  val tagMem   = SyncReadMem(1 << c_index, UInt(t_width.W))
  val dataMem  = SyncReadMem(1 << c_index, Vec(4, UInt(8.W)))
  val index    = io.cpu.req.bits.addr(c_index + 1, 2)
  val tag      = io.cpu.req.bits.addr(xLen - 1, c_index + 2)

  val cache_en    = io.cpu.req.valid
  val cache_write = io.cpu.req.bits.we

  val c_din = Mux(cache_write, io.cpu.req.bits.data, io.wb.rdata)

  // write
  when(cache_en && cache_write) {
    validMem.write(index, true.B)
    tagMem.write(index, tag)
    val wdata = Wire(Vec(4, UInt(8.W)))
//    wdata := io.cpu.req.bits.data
    for (i <- 0 until 4)
      wdata(i) := c_din(((1 + i) * 8) - 1, i * 8)
    dataMem.write(index, wdata, io.cpu.req.bits.sel.asBools)
  }

  // read
  val valid  = validMem.read(index)
  val tagout = tagMem.read(index)

  val hit = valid && tag === tagout

  io.wb.wdata := io.cpu.req.bits.data
  io.wb.addr  := io.cpu.req.bits.addr
  io.wb.we    := cache_en && io.cpu.req.bits.we
  io.wb.cyc   := cache_en && (io.cpu.req.bits.we || !hit)
  io.wb.stb   := cache_en && (io.cpu.req.bits.we || !hit)
  io.wb.sel   := io.cpu.req.bits.sel

  io.cpu.resp.valid     := (!cache_write && hit) || (!hit | cache_write) && io.wb.ack || io.cpu.abort
  io.cpu.resp.bits.data := Mux(hit, dataMem.read(index).asUInt, io.wb.rdata)
}
