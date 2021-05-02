import chipsalliance.rocketchip.config.Config
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import core._

import scala.annotation.{nowarn, tailrec}

object Main extends App {
  type OptionMap = Map[String, String]

  @nowarn
  @tailrec
  def parse(map: OptionMap, list: List[String]): OptionMap =
    list match {
      case Nil                                    => Map("dir" -> ".") ++ map
      case ("-d" | "--target-dir") :: dir :: tail => parse(map ++ Map("dir" -> dir), tail)
    }

  val options = parse(Map(), args.toList)
  println(s"[info] output to ${options("dir")}")

  implicit val c: Config = new Config(new WithExtA ++ new NaiveConfig)
  new ChiselStage execute (Array("--target-dir", options("dir")), Seq(ChiselGeneratorAnnotation(() => new Naive)))
}
