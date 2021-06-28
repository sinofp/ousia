import chisel3._
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}

object Main extends App {
  new ChiselStage execute (Array(), Seq(ChiselGeneratorAnnotation(() => new PassThrough)))
}

class PassThrough extends Module {
  val io = IO(new Bundle() {
    val in = Input(UInt(32.W))
    val out = Output(UInt(32.W))
  })

  io.out := io.in
}