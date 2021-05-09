import cocotb
from cocotb.clock import Clock
from cocotb.triggers import FallingEdge
from collections import deque
from os import environ


def b2d(x):
    """binary to decimal"""
    return int(str(x.value), 2)


# True -> v, False -> p
env_v = "-v-" in environ["DUMPFILE"]

pattern = deque(maxlen=2 if env_v else 3)
pass_pattern = deque(
    ["00100513", "00000073"] if env_v else ["05d00893", "00000513", "00000073"]
)
fail_pattern = deque(
    ["00050063", "00156513"] if env_v else ["00018063", "00119193", "0011e193"]
)


@cocotb.test()
async def riscv_test(dut):
    clock = Clock(dut.clk, 1, units="ns")  # Create a 1ns period clock on port clk
    cocotb.fork(clock.start())  # Start the clock

    dut.reset <= 1
    await FallingEdge(dut.clk)
    dut.reset <= 0

    cpu = dut.soc.cpu
    cnt = 0

    while True:
        if cpu.next_inst == 1:
            cnt = 0
            pattern.append("{:08x}".format(b2d(cpu.inst)))
        else:
            cnt += 1
            assert cnt != 100, "Stucked!"
        if pattern == pass_pattern:
            break
        assert pattern != fail_pattern, "Failed at test {:d}".format(
            b2d(cpu.rf.reg_3) // 2
        )
        await FallingEdge(dut.clk)

    if not env_v:
        a7 = cpu.rf.reg_17
        assert a7 == 93, "a7 is not 93, a7 is {}".format(b2d(a7))

        a0 = cpu.rf.reg_10
        assert a0 == 0, "a0 is not 0, a0 is {}".format(b2d(a0))
