import chipsalliance.rocketchip.config.Parameters
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

object Main extends App {
  implicit val p = Parameters.empty
  new ChiselStage execute (Array("--target-dir", "sim", "--output-file", "top"), Seq(
    ChiselGeneratorAnnotation(() => new ALU)
  ))
}
