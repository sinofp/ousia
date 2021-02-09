import cocotb
from cocotb.clock import Clock
from cocotb.triggers import FallingEdge, Timer

one_second = 1000_000_000  # ns
clk_rate = 50_000_000  # Hz


class UART:
    def __init__(self, uart, baud_rate):
        self.uart = uart
        self.baud_rate = baud_rate  # Hz

    async def reset(self):
        uart = self.uart
        uart.wb_rst_i <= 1
        await FallingEdge(uart.wb_clk_i)
        uart.wb_rst_i <= 0

    async def init(self):
        await self.write(0x80, 0x3)
        await self.write(0x00, 0x1)
        await self.write(clk_rate // 16 // self.baud_rate, 0x0)
        await self.write(0x03, 0x3)

    async def write(self, data, addr):
        uart = self.uart
        uart.wb_adr_i <= addr
        uart.wb_dat_i <= data

        uart.wb_we_i <= 1
        uart.wb_cyc_i <= 1
        uart.wb_stb_i <= 1

        while uart.wb_ack_o != 1:
            await FallingEdge(uart.wb_clk_i)
            # print("Wating ack...")

        print(f"Write 0x{data:02x}/0b{data:08b} to 0x{addr:x} OK.")

        uart.wb_we_i <= 0
        uart.wb_cyc_i <= 0
        uart.wb_stb_i <= 0
        await FallingEdge(uart.wb_clk_i)

    async def read(self, addr):
        uart = self.uart
        uart.wb_adr_i <= addr

        uart.wb_we_i <= 0
        uart.wb_cyc_i <= 1
        uart.wb_stb_i <= 1

        while uart.wb_ack_o != 1:
            await FallingEdge(uart.wb_clk_i)

        uart.wb_cyc_i <= 0
        uart.wb_stb_i <= 0
        rdata = uart.wb_dat_o.value.integer
        await FallingEdge(uart.wb_clk_i)

        return rdata

    async def send(self, char):
        print("Wait last transmission")
        while True:
            line_status = await self.read(0x5)
            if line_status & 0x20 != 0:
                break
        await self.write(ord(char), 0x0)
        print(f"Send start: {char}")

    async def half_baud_period(self):
        await Timer(one_second // self.baud_rate // 2, units="ns")

    async def baud_period(self):
        await Timer(one_second // self.baud_rate, units="ns")

    async def recv(self, tx=None):
        if tx is None:
            tx = self.uart.stx_pad_o
        await FallingEdge(tx)
        print("Recv start")
        await self.half_baud_period()  # center of start bit period
        assert tx == 0

        ch = 0
        for i in range(8):
            await self.baud_period()
            # print(f"tx is {tx.value}")
            ch |= tx.value << i
        print(f"recv: {chr(ch)}")

        return chr(ch)
