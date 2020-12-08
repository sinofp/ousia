package cpu.part

import chisel3._
import chiseltest._
import org.scalatest._

class RegFileTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "RegFile"

  it should "save" in {
    test(new RegFile(2)) { c =>
      c.io.wen.poke(true.B)
      c.io.waddr.poke(1.U)
      c.io.wdata.poke(2.U)
      c.clock.step(1)
      c.io.wen.poke(false.B)
      c.io.raddr(0).poke(1.U)
      c.io.rdata(0).expect(2.U)
    }
  }
}
