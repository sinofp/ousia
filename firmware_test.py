import cocotb
from cocotb.clock import Clock
from cocotb.triggers import FallingEdge
from firmware.uart import UART, one_second, clk_rate


@cocotb.test()
async def firmware_test(dut):
    clock = Clock(dut.clk, one_second / clk_rate, units="ns")
    cocotb.fork(clock.start())

    dut.reset <= 1
    await FallingEdge(dut.clk)
    dut.reset <= 0

    fake_uart = UART("fake", 38400)

    await FallingEdge(dut.clk)
    assert dut.gpio_o == 0, "gpio_o should be zero at beginning!"

    string = []
    while True:
        string.append(await fake_uart.recv(dut.soc.uart_tx))
        if "".join(string) == "UART OK!!\r\n":
            break

    assert dut.gpio_o == int("01010101", 2), "gpio_o is {:b}".format(
        dut.gpio_o.value.integer
    )
