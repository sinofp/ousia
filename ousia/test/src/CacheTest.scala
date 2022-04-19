import chisel3._
import chisel3.util.log2Up
import chiseltest._
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec

class CacheTest extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Cache"

  val linePerWay     = 512
  val wordPerLine    = 4
  val xLen           = 32
  val indexLen       = log2Up(linePerWay)
  val blockOffsetLen = log2Up(wordPerLine)
  val byteOffsetLen  = 2
  val tagLen         = xLen - indexLen - blockOffsetLen - byteOffsetLen
  val indexSlice     = (xLen - 1 - tagLen, blockOffsetLen + byteOffsetLen)
  val tagSlice       = (xLen - 1, xLen - tagLen)

  def toBinary(int: Int, length: Int): String = {
    require(int < math.pow(2, length).toInt - 1)
    s"%${length}s".format(int.toBinaryString).replace(' ', '0')
  }

  def wait[T <: Module](condition: => Boolean, msg: String = "")(implicit c: T) =
    while (!condition) {
      c.clock.step()
      if (msg.nonEmpty) println(msg)
    }

  it should "allocate when first read" in {
    test(new Cache(linePerWay)) { c =>
      implicit val a = c
      // todo 测试dirty
      // read a row from mem
      c.cpuReq.addr.poke(("b" + "1" * tagLen + "1" * indexLen + "00" + "00").U)
      c.cpuReq.we.poke(false.B)
      c.cpuReq.valid.poke(true.B)
      c.clock.step()
      c.cpuReq.valid.poke(false.B)
      //      c.state.expect(1.U)
      c.clock.step(3)
      c.memRes.ready.poke(true.B)
      for (i <- 0 until 4) c.memRes.data(i).poke((i + 42).U)
      c.clock.step()
      c.cpuRes.ready.expect(true.B)
      c.cpuRes.data.expect(42.U(32.W))
      c.clock.step()
      //      c.state.expect(0.U)

      // read that row from cache
      val block = Seq("00", "01", "10", "11")
      for (i <- 1 until 40) {
        val addr = "b" + "1" * tagLen + "1" * indexLen + block(i % 4) + "00"
        c.cpuReq.addr.poke(addr.U)
        c.cpuReq.valid.poke(true.B)
        wait(c.cpuRes.ready.peek().litToBoolean, s"wait $i")
        c.cpuRes.data.expect(((i % 4) + 42).U)
        c.clock.step()
      }
    }
  }

  it should "write back when write miss" in {
    test(new Cache(linePerWay)) { c =>
      implicit val a = c
      // allocate
      c.cpuReq.addr.poke(("b" + "1" * tagLen + "1" * indexLen + "00" + "00").U)
      c.cpuReq.we.poke(false.B)
      c.cpuReq.valid.poke(true.B)
      c.clock.step()
      c.cpuReq.valid.poke(false.B)
      //      c.state.expect(1.U)
      c.clock.step(3)
      c.memRes.ready.poke(true.B)
      //      c.memRes.data.poke(VecInit(Seq.tabulate(4)(_.U(32.W))))
      for (i <- 0 until 4)
        c.memRes.data(i).poke((i + 42).U)
      c.clock.step()
      c.memRes.ready.poke(false.B)
      c.cpuRes.ready.expect(true.B)
      c.cpuRes.data.expect(42.U(32.W))
      c.clock.step()
      //      c.state.expect(0.U)
      println()
      // write
      val block = Seq("00", "01", "10", "11")
      for (i <- 0 until 4) {
        val addr = "b" + "1" * tagLen + "1" * indexLen + block(i) + "00"
        c.cpuReq.addr.poke(addr.U)
        c.cpuReq.valid.poke(true.B)
        c.cpuReq.data.poke((i + 50).U)
        c.cpuReq.we.poke(true.B)
        wait(c.cpuRes.ready.peek().litToBoolean, s"wait $i")
        c.clock.step()
      }
      // write with same index, different tag
      val newAddr = ("b" + "0" * tagLen + "1" * indexLen + "00" + "00").U
      c.cpuReq.addr.poke(newAddr)
      c.cpuReq.we.poke(false.B)
      c.cpuReq.valid.poke(true.B)
      c.clock.step()
      c.cpuReq.valid.poke(false.B)
      //      c.state.expect(1.U)
      println()
      while (!c.memReq.valid.peek().litToBoolean)
        c.clock.step()
      c.memReq.we.expect(true.B)
      for (i <- 0 until 4) c.memReq.data(i).expect((i + 50).U)
      c.clock.step(10)
      // pretend wrote
      c.memRes.ready.poke(true.B)
      // allocate for this new tag
      c.memReq.valid.expect(true.B) // 和memRes.ready同一cycle的
      c.memReq.addr.expect(newAddr)
      c.clock.step()
      c.memRes.ready.poke(false.B)
      c.clock.step(10)
      c.memRes.ready.poke(true.B)
      //      c.memRes.data.poke(VecInit(Seq.tabulate(4)(_.U(32.W))))
      for (i <- 0 until 4)
        c.memRes.data(i).poke((i + 0x99).U)
      c.clock.step()
      c.memRes.ready.poke(false.B)
      // compare_tag, hit
      c.cpuRes.ready.expect(true.B)
      c.clock.step()
      // read wrote value
      for (i <- 0 until 4) {
        val addr = "b" + "0" * tagLen + "1" * indexLen + block(i) + "00"
        c.cpuReq.addr.poke(addr.U)
        c.cpuReq.valid.poke(true.B)
        c.cpuReq.data.poke((i + 99).U)
        c.cpuReq.we.poke(true.B)
        wait(c.cpuRes.ready.peek().litToBoolean, s"wait $i")
        c.clock.step()
      }
    }
  }

  it should "pass test from COAD" in {
    test(new Cache(linePerWay)) { c =>
      implicit val a = c

      c.cpuReq.we.poke(false.B)
      c.cpuReq.addr.poke(("b" + toBinary(0x1234, tagLen) + toBinary(2, indexLen) + "00" + "00").U)
      c.cpuReq.valid.poke(true.B)
      println("[CPU] read addr=%08x".format(c.cpuReq.addr.peek().litValue()))
      // allocate from mem
      c.clock.step(4)
      c.memRes.ready.poke(true.B)
      for (i <- 0 until 4) c.memRes.data(i).poke((i + 0x42).U)
      c.clock.step()
      c.memRes.ready.poke(false.B)
      wait(c.cpuRes.ready.peek().litToBoolean)
      println("[CPU] get data=%08x".format(c.cpuRes.data.peek().litValue()))
      c.cpuReq.valid.poke(false.B)
      c.clock.step()

      //read hit clean line
      c.cpuReq.addr.poke(("b" + toBinary(0x1234, tagLen) + toBinary(2, indexLen) + toBinary(8, 4)).U)
      c.cpuReq.valid.poke(true.B)
      println("[CPU] read addr=%08x".format(c.cpuReq.addr.peek().litValue()))
      wait(c.cpuRes.ready.peek().litToBoolean)
      println("[CPU] get data=%08x".format(c.cpuRes.data.peek().litValue()))
      c.cpuReq.valid.poke(false.B)
      c.clock.step()

      //write hit clean line (cache line is dirty afterwards)
      c.cpuReq.we.poke(true.B)
      c.cpuReq.addr.poke(("b" + toBinary(0x1234, tagLen) + toBinary(2, indexLen) + toBinary(0xa, 4)).U)
      c.cpuReq.data.poke("hdeadbeef".U)
      c.cpuReq.valid.poke(true.B)
      println(
        "[CPU] write addr=%08x with data=%08x".format(c.cpuReq.addr.peek().litValue(), c.cpuReq.data.peek().litValue())
      )
      wait(c.cpuRes.ready.peek().litToBoolean)
      println("[CPU] write done")
      c.cpuReq.valid.poke(false.B)
      c.clock.step()

      //write conflict miss (write back then allocate, cache line dirty)
      // 前面we poke过true了
      c.cpuReq.addr.poke(("b" + toBinary(0x4321, tagLen) + toBinary(2, indexLen) + toBinary(0xa, 4)).U)
      c.cpuReq.data.poke("hcafebeef".U)
      c.cpuReq.valid.poke(true.B)
      println(
        "[CPU] write addr=%08x with data=%08x".format(c.cpuReq.addr.peek().litValue(), c.cpuReq.data.peek().litValue())
      )
      // write back to mem
      c.clock.step(4)
      c.memRes.ready.poke(true.B)
      c.clock.step()
      c.memRes.ready.poke(false.B)
      // allocate from mem
      c.clock.step(4)
      c.memRes.ready.poke(true.B)
      for (i <- 0 until 4) c.memRes.data(i).poke((i + 99).U)
      c.clock.step()
      c.memRes.ready.poke(false.B)
      wait(c.cpuRes.ready.peek().litToBoolean)
      println("[CPU] write done")
      c.cpuReq.valid.poke(false.B)
      c.clock.step()

      //read hit dirty line
      c.cpuReq.we.poke(false.B)
      c.cpuReq.addr.poke(("b" + toBinary(0x4321, tagLen) + toBinary(2, indexLen) + "0000").U)
      c.cpuReq.valid.poke(true.B)
      println("[CPU] read addr=%08x".format(c.cpuReq.addr.peek().litValue()))
      wait(c.cpuRes.ready.peek().litToBoolean)
      println("[CPU] get data=%08x".format(c.cpuRes.data.peek().litValue()))
      c.cpuReq.valid.poke(false.B)
      c.clock.step()

      //read conflict miss dirty line (write back then allocate, cache line is clean)
      c.cpuReq.addr.poke(("b" + toBinary(0x5678, tagLen) + toBinary(2, indexLen) + toBinary(4, 4)).U)
      c.cpuReq.valid.poke(true.B)
      println("[CPU] read addr=%08x".format(c.cpuReq.addr.peek().litValue()))
      // write back to mem
      c.clock.step(4)
      c.memRes.ready.poke(true.B)
      c.clock.step()
      c.memRes.ready.poke(false.B)
      // allocate from mem
      c.clock.step(4)
      c.memRes.ready.poke(true.B)
      for (i <- 0 until 4) c.memRes.data(i).poke((i + 0x66).U)
      c.clock.step()
      c.memRes.ready.poke(false.B)
      wait(c.cpuRes.ready.peek().litToBoolean)
      println("[CPU] get data=%08x".format(c.cpuRes.data.peek().litValue()))
      c.cpuReq.valid.poke(false.B)
      c.clock.step()
    }
  }
}
