package core

import chisel3._

object Util {
  implicit class HighLowHalf(lhs: UInt) {
    def highHalf = lhs(lhs.getWidth - 1, lhs.getWidth / 2)
    def lowHalf  = lhs(lhs.getWidth / 2 - 1, 0)
  }
}
