import cocotb
from cocotb.clock import Clock
from cocotb.triggers import FallingEdge
from firmware.uart import UART, one_second, clk_rate


@cocotb.test()
async def uart_test(dut):
    clock = Clock(dut.wb_clk_i, one_second / clk_rate, units="ns")
    cocotb.fork(clock.start())

    uart = UART(dut, 115200)
    await uart.reset()
    await uart.init()

    # send
    for char in "abcABC":
        await uart.send(char)
        await uart.recv()
