package core

import chisel3._
import chisel3.util.Fill

object Util {
  implicit class HighLowHalf(lhs: UInt) {
    def highHalf = lhs(lhs.getWidth - 1, lhs.getWidth / 2)
    def lowHalf  = lhs(lhs.getWidth / 2 - 1, 0)
  }
  def ZXT(x: UInt, len: Int = 32) = 0.U((len - x.getWidth).W) ## x
  def SXT(x: UInt, len: Int = 32) = Fill(len - x.getWidth, x(x.getWidth - 1)) ## x
}
