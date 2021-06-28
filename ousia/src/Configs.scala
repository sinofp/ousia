import chipsalliance.rocketchip.config.{Field, Parameters}
import chisel3._

trait HasCoreConfigs {
  implicit val p: Parameters
  val xLen = p(XLEN)
}

abstract class CoreModule(implicit val p: Parameters) extends Module with HasCoreConfigs
abstract class CoreBundle(implicit val p: Parameters) extends Bundle with HasCoreConfigs

case object XLEN      extends Field[Int](32)
case object DRAM_BASE extends Field[UInt]("h80000000".U)
