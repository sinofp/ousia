import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import core._

object Main extends App {
  implicit val c = new NaiveConfig
  new ChiselStage execute (Array("--target-dir", "cocotb"), Seq(ChiselGeneratorAnnotation(() => new Naive)))
}
