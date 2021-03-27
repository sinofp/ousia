package core

import chipsalliance.rocketchip.config.{Config, Field}
import chisel3._

trait HasCoreConfigs {
  implicit val c: Config
  val xLen = c(XLEN)
}

abstract class CoreModule(implicit val c: Config) extends Module with HasCoreConfigs // with RequireAsyncReset
abstract class CoreBundle(implicit val c: Config) extends Bundle with HasCoreConfigs

case object XLEN      extends Field[Int]
case object DRAM_BASE extends Field[UInt]
case object CacheType extends Field[String](default = "None") // 'None 多好，可惜deprecate了

class NaiveConfig
    extends Config((site, here, up) => {
      case XLEN      => 32
      case DRAM_BASE => "h80000000".U
    })
class WithCacheDirectMap
    extends Config((site, here, up) => { case CacheType =>
      "DirectMapping"
    })
