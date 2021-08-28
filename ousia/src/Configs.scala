import chisel3._

trait HasCoreConfigs {
  val xLen: Int = XLEN.into
}

abstract class CoreModule extends Module with HasCoreConfigs
abstract class CoreBundle extends Bundle with HasCoreConfigs

sealed abstract class Field[T](val into: T)
case object XLEN      extends Field[Int](32)
case object DRAM_BASE extends Field[UInt]("h80000000".U)
