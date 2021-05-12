import cocotb
from cocotb.clock import Clock
from cocotb.triggers import FallingEdge, Timer

one_second = 1000_000_000  # ns
clk_rate = 20_000_000  # Hz


class UART:
    def __init__(self, baud_rate):
        self.baud_rate = baud_rate  # Hz

    async def half_baud_period(self):
        await Timer(one_second // self.baud_rate // 2, units="ns")

    async def baud_period(self):
        await Timer(one_second // self.baud_rate, units="ns")

    async def recv(self, tx):
        await FallingEdge(tx)
        print("Recv start")
        await self.half_baud_period()  # center of start bit period
        assert tx == 0

        ch = 0
        for i in range(8):
            await self.baud_period()
            ch |= tx.value << i
        print(f"recv: {chr(ch)}")

        return chr(ch)


@cocotb.test()
async def firmware_test(dut):
    clock = Clock(dut.clk, one_second // clk_rate, units="ns")
    cocotb.fork(clock.start())

    dut.reset <= 1
    await FallingEdge(dut.clk)
    dut.reset <= 0

    fake_uart = UART(115200)

    await FallingEdge(dut.clk)
    assert dut.gpio_o == 0, "gpio_o should be zero at beginning!"

    string = []
    while True:
        string.append(await fake_uart.recv(dut.soc.uart_tx))
        if "".join(string) == "UA":  # "UART\tOK\r\n":
            break

    assert dut.gpio_o == int("00000001", 2), "gpio_o is {:b}".format(
        dut.gpio_o.value.integer
    )
