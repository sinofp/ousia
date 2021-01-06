package core

import chipsalliance.rocketchip.config.Config
import chisel3._

trait HasCoreConfigs {
  val xLen = 32
}

abstract class CoreModule(implicit val c: Config) extends Module with HasCoreConfigs
