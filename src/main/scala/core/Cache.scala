package core

import chipsalliance.rocketchip.config.Config
import chisel3._
import chisel3.util._

import scala.annotation.nowarn

@nowarn("cat=deprecation")
class Cache(implicit c: Config) extends CoreModule {
  val io = IO(new CacheModuleIO)

  val cache = Module(if (c(CacheType) == "None") new CachePassThrough else new CacheDirectMap(9))
  cache.io <> io
}

class CacheReq(implicit c: Config) extends CoreBundle {
  val addr = UInt(xLen.W)
  val data = UInt(xLen.W)
  val we   = Bool()
  val sel  = UInt((xLen / 8).W)
}

class CacheResp(implicit c: Config) extends CoreBundle {
  val data = UInt(xLen.W)
}

class CacheIO(implicit c: Config) extends CoreBundle {
  val abort = Input(Bool())
  val req   = Flipped(Valid(new CacheReq))
  val resp  = Valid(new CacheResp)
}

class CacheModuleIO(implicit c: Config) extends CoreBundle {
  val cpu = new CacheIO
  val wb  = new WishBoneIO
}

class CachePassThrough(implicit c: Config) extends CoreModule {
  val io = IO(new CacheModuleIO)

  io.wb.cyc             := io.cpu.req.valid && !io.cpu.abort
  io.wb.stb             := io.cpu.req.valid && !io.cpu.abort
  io.wb.sel             := io.cpu.req.bits.sel
  io.wb.addr            := io.cpu.req.bits.addr
  io.wb.wdata           := io.cpu.req.bits.data
  io.wb.we              := io.cpu.req.bits.we
  io.cpu.resp.valid     := io.wb.ack || io.cpu.abort
  io.cpu.resp.bits.data := io.wb.rdata
}

class CacheDirectMap(val c_index: Int)(implicit c: Config) extends CoreModule {
  val io = IO(new CacheModuleIO)

  val sIdle :: sCompareTag :: sAlloc :: sWriteBack :: Nil = Enum(4)
  val state                                               = RegInit(sIdle)

  val nSets    = 1 << c_index
  val t_width  = xLen - c_index - 2 // 1 block = 4 byte
  val validReg = RegInit(0.U(nSets.W))
  val dirtyReg = RegInit(0.U(nSets.W))
  val tagMem   = SyncReadMem(nSets, UInt(t_width.W))
  val dataMem  = SyncReadMem(nSets, UInt(32.W))

  val index = WireInit(io.cpu.req.bits.addr(c_index + 1, 2))
  val tag   = WireInit(io.cpu.req.bits.addr(xLen - 1, c_index + 2))

  val valid   = validReg(index)
  val dirty   = dirtyReg(index)
  val tagOut  = tagMem.read(index)
  val dataOut = dataMem.read(index); dataOut suggestName "dataOut"
  val hit     = valid && tag === tagOut

  def genDataBySel(mem_data: UInt): UInt = {
    val cpu_data = io.cpu.req.bits.data
    MuxLookup(
      io.cpu.req.bits.sel,
      cpu_data,
      Seq(
        // 给这玩意整个函数？
        "b0011".U -> mem_data(31, 16) ## cpu_data(15, 0),
        "b1100".U -> cpu_data(31, 16) ## mem_data(15, 0),
        "b1000".U -> cpu_data(31, 24) ## mem_data(23, 0),
        "b0100".U -> mem_data(31, 24) ## cpu_data(23, 16) ## mem_data(15, 0),
        "b0010".U -> mem_data(31, 16) ## cpu_data(15, 8) ## mem_data(7, 0),
        "b0001".U -> mem_data(31, 8) ## cpu_data(7, 0),
      ),
    )
  }

  val mem_req_valid = RegInit(false.B) // 要记着几个周期，所以不能是Wire
  io.wb.cyc   := mem_req_valid && !io.cpu.abort
  io.wb.stb   := mem_req_valid && !io.cpu.abort
  io.wb.addr  := io.cpu.req.bits.addr
  io.wb.we    := false.B
  io.wb.sel   := "b1111".U
  io.wb.wdata := dataOut // 要写就只有写脏的数据，这个是dataOut

  io.cpu.resp.valid     := false.B // abort
  io.cpu.resp.bits.data := dataOut

  switch(state) {
    is(sIdle) {
      when(io.cpu.req.valid)(state := sCompareTag)
    }
    is(sCompareTag) {
      when(hit) {
        io.cpu.resp.valid := true.B
        when(io.cpu.req.bits.we) {
          // write hit
          // tag，valid不用动，变dirty
          dirtyReg := dirtyReg.bitSet(index, true.B)
          dataMem.write(index, genDataBySel(dataOut))
          //不在这时写，等它换出时才写（write back）
        }
        state             := sIdle
      }.otherwise {
        // cache miss
        // 新建tag
        validReg      := validReg.bitSet(index, true.B)
        tagMem.write(index, tag)
        // 如果是写，脏了
        dirtyReg      := dirtyReg.bitSet(index, io.cpu.req.bits.we)
        // 读/写内存
        mem_req_valid := true.B
        when(valid === 0.U || dirty === 0.U) {
          // 强制miss或miss在了干净的块上
          // 等新的块分配，读
          io.wb.we := false.B
          state    := sAlloc
        }.otherwise {
          // miss在了脏块上，先写
          // 写到旧的地址里，别写错了
          io.wb.addr := tagOut ## index ## 0.U(2.W)
          io.wb.we   := true.B
          state      := sWriteBack
        }
      }
    }
    is(sAlloc) {
      // 读的请求在sCompareTag发出了，这里循环等ack
      when(io.wb.ack) {
        state         := sCompareTag
        // 之所以写Cache也要读，因为可能只写一部分（sb/sh...）。另外之后升级组相联时要一下读一组，只写其中一块。
        // 所以未名中不能直接写，得先读再写
        dataMem.write(index, Mux(io.cpu.req.bits.we, genDataBySel(io.wb.rdata), io.wb.rdata))
        mem_req_valid := false.B
      }
    }
    is(sWriteBack) {
      when(io.wb.ack) {
        // 存完了旧的读新的
        io.wb.addr := io.cpu.req.bits.addr
        io.wb.we   := false.B
        state      := sAlloc
      }
    }
  }
}
