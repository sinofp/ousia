package core

import chipsalliance.rocketchip.config.{Config, Field}
import chisel3._

trait HasCoreConfigs {
  implicit val c: Config
  val xLen   = c(XLEN)
  val rf2Top = c(RF2Top)
}

abstract class CoreModule(implicit val c: Config) extends Module with HasCoreConfigs
abstract class CoreBundle(implicit val c: Config) extends Bundle with HasCoreConfigs

case object XLEN   extends Field[Int]
case object RF2Top extends Field[Boolean](false)
case object Cache  extends Field[String](default = "None") // 'None 多好，可惜deprecate了

class NaiveConfig
    extends Config((site, here, up) => { case XLEN =>
      32
    })
class WithRF2Top
    extends Config((site, here, up) => { case RF2Top =>
      true
    })
class WithCacheDirectMap
    extends Config((site, here, up) => { case Cache =>
      "DirectMapping"
    })
