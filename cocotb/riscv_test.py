import cocotb
from cocotb.clock import Clock
from cocotb.triggers import FallingEdge
from collections import deque
from os import system


def bin2dec(x):
    return int(str(x.value), 2)


# https://github.com/riscv/riscv-test-env/blob/43d3d53809085e2c8f030d72eed1bdf798bfb31a/p/riscv_test.h#L239
# li      a7,93
# li      a0,0
# ecall
pass_inst = deque(["05d00893", "00000513", "00000073"])
pattern = deque(maxlen=3)


@cocotb.test()
async def test_sw(dut):
    clock = Clock(dut.clk, 1, units="us")  # Create a 1us period clock on port clk
    cocotb.fork(clock.start())  # Start the clock

    while True:
        inst = "{:08x}".format(bin2dec(dut.cpu.io_itcm_inst))
        pattern.append(inst)
        if pattern == pass_inst:
            break
        await FallingEdge(dut.clk)
        print("pc is {:8x} inst is {}".format(bin2dec(dut.cpu.pc), inst))

    a7 = dut.cpu.rf.reg_17
    a0 = dut.cpu.rf.reg_10
    assert a7 == 93, "a7 is not 93, a7 is {}".format(bin2dec(a7))
    assert a0 == 0, "a0 is not 0, a0 is {}".format(bin2dec(a0))
