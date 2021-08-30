import chiseltest._
import chisel3._
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec

class BramTest extends AnyFlatSpec with ChiselScalatestTester with Matchers {
  behavior of "BRAM"

  it should "write first" in {
    test(new BRAM(4, UInt(32.W))) { c =>
      c.addr.poke(0.U)
      c.we.poke(true.B)
      c.write.poke(42.U)
      c.clock.step()
      c.read.expect(42.U)
    }
  }

  it should "read delay one cycle" in {
    test(new BRAM(4, UInt(32.W))) { c =>
      for (i <- 0 until 4) {
        c.addr.poke(i.U)
        c.we.poke(true.B)
        c.write.poke(i.U)
        c.clock.step()
      }
      for (i <- 0 until 4) {
        c.addr.poke(i.U)
        c.we.poke(false.B)
        c.read.expect(((i + 3) % 4).U) // 上一周期的地址里的内容
        c.clock.step()
        c.read.expect(i.U) // 这个地址对应的内容
      }
    }
  }
}
