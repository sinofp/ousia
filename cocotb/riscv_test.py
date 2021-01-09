import cocotb
from cocotb.clock import Clock
from cocotb.triggers import FallingEdge
from collections import deque
from subprocess import run, PIPE


def bin2dec(x):
    return int(str(x.value), 2)


pattern = deque(maxlen=3)
# rg -A6 '<pass>:' rv32ui-p-*
pass_pattern = deque(["00000513", "00000073", "c0001073"])
# rg -A6 '<fail>:' rv32ui-p-*
fail_pattern = deque(["0ff0000f", "00018063", "00119193"])


@cocotb.test()
async def test_sw(dut):
    clock = Clock(dut.clk, 1, units="us")  # Create a 1us period clock on port clk
    cocotb.fork(clock.start())  # Start the clock

    while True:
        inst = "{:08x}".format(bin2dec(dut.cpu.inst))
        if inst != "00000000":  # stall时行插入了空指令
            pattern.append(inst)
            asm = run(
                [
                    "rasm2",
                    "-a",
                    "riscv",
                    "-d",
                    inst[-2:] + inst[-4:-2] + inst[2:4] + inst[:2],  # -e 有问题
                ],
                stdout=PIPE,
            ).stdout.decode("utf-8")
            print(
                "pc = {:8x} | inst = {} | asm = {}".format(bin2dec(dut.cpu.pc), inst, asm),
                end="", # asm有回车
            )
        if pattern == pass_pattern:
            break
        assert pattern != fail_pattern, "Failed at test {:d}".format(
            bin2dec(dut.cpu.rf.reg_3)
        )
        await FallingEdge(dut.clk)

    a7 = dut.cpu.rf.reg_17
    assert a7 == 93, "a7 is not 93, a7 is {}".format(bin2dec(a7))

    a0 = dut.cpu.rf.reg_10
    assert a0 == 0, "a0 is not 0, a0 is {}".format(bin2dec(a0))
