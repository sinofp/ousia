import chipsalliance.rocketchip.config.{Config, Parameters}
import chisel3.stage.{ChiselGeneratorAnnotation, ChiselStage}
import core._

import scala.annotation.{nowarn, tailrec}

sealed trait CmdOption
case object GenType   extends CmdOption
case object OutputDir extends CmdOption

object Main extends App {
  type CmdOptionMap = Map[CmdOption, String]

  @tailrec
  @nowarn("msg=match may not be exhaustive")
  def nextCmdOption(map: CmdOptionMap, list: List[String]): CmdOptionMap =
    list match {
      case Nil                                    => Map(GenType -> "full", OutputDir -> ".") ++ map
      case ("-d" | "--target-dir") :: dir :: tail => nextCmdOption(map ++ Map(OutputDir -> dir), tail)
      case ("-g" | "--gen") :: tpe :: tail        => nextCmdOption(map ++ Map(GenType -> tpe), tail)
    }

  val options = nextCmdOption(Map(), args.toList)
  println(s"[info] output to ${options(OutputDir)}")

  implicit val c: Config = new Config(
    if (options(GenType) == "mini") Parameters.empty else new WithExtM ++ new WithExtA
  )
  new ChiselStage execute (Array("--target-dir", options(OutputDir)), Seq(ChiselGeneratorAnnotation(() => new Naive)))
}
