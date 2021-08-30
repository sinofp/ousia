import chisel3._
import chisel3.util.{is, log2Up, switch, Enum}

class CpuReq extends CoreBundle {
  val addr  = UInt(32.W) // 34?
  val data  = UInt(xLen.W)
  val we    = Bool()
  val valid = Bool()
}

class CpuRes extends CoreBundle {
  val data  = UInt(xLen.W)
  val ready = Bool()
}

class MemReq(wordPerLine: Int) extends CoreBundle {
  val addr  = UInt(32.W) // 34?
  val data  = Vec(wordPerLine, UInt(xLen.W))
  val we    = Bool()
  val valid = Bool()
}

class MemRes(wordPerLine: Int) extends CoreBundle {
  val data  = Vec(wordPerLine, UInt(xLen.W))
  val ready = Bool()
}

class Cache(linePerWay: Int, wordPerLine: Int = 4) extends CoreModule {
  val indexLen       = log2Up(linePerWay)
  val blockOffsetLen = log2Up(wordPerLine)
  val byteOffsetLen  = 2
  val tagLen         = xLen - indexLen - blockOffsetLen - byteOffsetLen
  val indexSlice     = (xLen - 1 - tagLen, blockOffsetLen + byteOffsetLen)
  val tagSlice       = (xLen - 1, xLen - tagLen)

  val cpuReq = IO(Input(new CpuReq))
  val cpuRes = IO(Output(new CpuRes))
  val memReq = IO(Output(new MemReq(wordPerLine)))
  val memRes = IO(Input(new MemRes(wordPerLine)))

  val tag  = BRAM(
    linePerWay,
    new Bundle() {
      val valid = Bool()
      val dirty = Bool()
      val tag   = UInt(tagLen.W)
    },
  )
  val data = BRAM(linePerWay, Vec(wordPerLine, UInt(xLen.W)))

  val dataRead  = Wire(Vec(wordPerLine, UInt(xLen.W)))
  val dataWrite = Wire(Vec(wordPerLine, UInt(xLen.W)))

  val idle :: compare_tag :: allocate :: write_back :: Nil = Enum(4)
  val state                                                = RegInit(idle)

  // default
  cpuRes.ready := false.B
  cpuRes.data  := DontCare

  tag.we          := false.B
  tag.write.valid := false.B
  tag.write.dirty := false.B
  tag.write.tag   := 0.U
  tag.addr        := cpuReq.addr(indexSlice._1, indexSlice._2)

  data.we    := false.B
  data.addr  := cpuReq.addr(indexSlice._1, indexSlice._2)
  dataRead   := data.read
  data.write := dataWrite

  dataWrite := dataRead
  switch(cpuReq.addr(3, 2)) {
//    (0 until 4).foreach(i => is(i.U(2.W))(dataWrite(i) := cpuReq.data))
    is(0.U(2.W))(dataWrite(0) := cpuReq.data)
    is(1.U(2.W))(dataWrite(1) := cpuReq.data)
    is(2.U(2.W))(dataWrite(2) := cpuReq.data)
    is(3.U(2.W))(dataWrite(3) := cpuReq.data)
  }

  switch(cpuReq.addr(3, 2)) {
    is(0.U(2.W))(cpuRes.data := dataRead(0))
    is(1.U(2.W))(cpuRes.data := dataRead(1))
    is(2.U(2.W))(cpuRes.data := dataRead(2))
    is(3.U(2.W))(cpuRes.data := dataRead(3))
  }

  memReq.addr  := cpuReq.addr
  memReq.data  := dataRead
  memReq.we    := false.B
  memReq.valid := false.B

//  printf("state is %b\n", state)
  switch(state) {
    is(idle)(when(cpuReq.valid)(state := compare_tag))
    is(compare_tag) {
      when(cpuReq.addr(tagSlice._1, tagSlice._2) === tag.read.tag && tag.read.valid) {
        cpuRes.ready := true.B

        when(cpuReq.we) {
          tag.we  := true.B
          data.we := true.B // data.write is in default

          // move to default?
          tag.write.tag   := tag.read.tag
          tag.write.valid := true.B
          tag.write.dirty := true.B
        }

        state := idle
      }.otherwise {
        tag.we          := true.B
        tag.write.valid := true.B
        tag.write.tag   := cpuReq.addr(tagSlice._1, tagSlice._2)
        tag.write.dirty := cpuReq.we

        memReq.valid := true.B
        // 这俩tag.read是tag.we = 1的同时读的，这里要旧的值
        when(!tag.read.valid || !tag.read.dirty)(state := allocate)
          .otherwise {
            memReq.addr := tag.read.tag ## cpuReq.addr(indexSlice._1, 0)
            memReq.we   := true.B
            state       := write_back
          }
      }
    }
    is(allocate)(when(memRes.ready) {
      state     := compare_tag
      dataWrite := memRes.data
      data.we   := true.B
    })
    is(write_back)(when(memRes.ready) {
      memReq.valid := true.B
      memReq.we    := false.B
      state        := allocate
    })
  }
}
