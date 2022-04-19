import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

object Main extends App {
  new ChiselStage execute (Array("--target-dir", "sim", "--output-file", "top"), Seq(
    ChiselGeneratorAnnotation(() => new InOrder)
  ))
}
