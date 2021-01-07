import org.scalatest._
import chiseltest._
import _root_.core.{Naive, NaiveConfig, WithRF2Top}
import chipsalliance.rocketchip.config.{Config, Parameters}
import chisel3._

class NaiveTest extends FlatSpec with ChiselScalatestTester with Matchers {
  behavior of "Naive"

  it should "work" in {
    implicit val c = new Config(new WithRF2Top ++ new NaiveConfig)
    test(new Naive) { c =>
      c.io.itcm.pc.expect(0.U)
      c.io.itcm.inst.poke("h00100193".U) // li gp, 1
      c.clock.step(1)
      c.io.itcm.pc.expect(4.U)
      c.io.rf.gp.expect(1.U)
      c.io.itcm.inst.poke("h00000513".U) // li a0, 0
      c.clock.step(1)
      c.io.itcm.pc.expect(8.U)
      c.io.rf.a0.expect(0.U)
    }
  }
}
