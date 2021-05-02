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
case object CacheType extends Field[String]("None") // 'None 多好，可惜deprecate了
case object ExtA      extends Field[Boolean](false)
case object ExtM      extends Field[Boolean](false)
case object ExtZicsr  extends Field[Boolean](true)

class NaiveConfig
    extends Config((_, _, _) => {
      case XLEN      => 32
      case DRAM_BASE => "h80000000".U
    })
class WithCacheDirectMap extends Config((_, _, _) => { case CacheType => "DirectMapping" })
class WithExtA           extends Config((_, _, _) => { case ExtA => true })
class WithExtM           extends Config((_, _, _) => { case ExtM => true })
