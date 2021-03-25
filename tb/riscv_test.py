import cocotb
from cocotb.clock import Clock
from cocotb.triggers import FallingEdge
from collections import deque
from os import environ, getcwd
import re


def b2d(x):
    """binary to decimal"""
    return int(str(x.value), 2)


pattern = deque(maxlen=3)
pass_pattern = deque(["05d00893", "00000513", "00000073"])
fail_pattern = deque(["00018063", "00119193", "0011e193"])

asm = {}
with open(environ["DUMPFILE"]) as f:
    for line in f.readlines():
        res = re.search(r"(?:.{8}|.{4}):\s+([0-9a-f]{8})\s+([^#<\n]+)", line)
        if res is not None:
            group = res.groups()
            asm[group[0]] = group[1]


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
        if cpu.commit == 1:
            cnt = 0
            inst = "{:08x}".format(b2d(cpu.inst))
            pattern.append(inst)
            print(
                "pc = {:8x} ({}) {}|va={:b}|addr34={:b}|satp.ppn={:b}|pte.ppn={:b}|\n\ts={}|i={}|pf={}|deleg2S={}|trans_on={}|intm={}|ints={}|e={}|iack={}".format(
                    b2d(cpu.pc),
                    inst,
                    asm.get(inst, "???"),
                    b2d(cpu.icache.io_cpu_req_bits_addr),
                    b2d(cpu.icache.addr),
                    b2d(cpu.icache.io_cpu_req_bits_satp_ppn),
                    b2d(cpu.icache.pte_ppn),
                    b2d(cpu.icache.state),
                    b2d(cpu.icache.level),
                    b2d(cpu.icache.io_cpu_resp_bits_page_fault),
                    b2d(cpu.csr.deleg2S),
                    b2d(cpu.icache.trans_on),
                    b2d(cpu.csr.interrupt_m),
                    b2d(cpu.csr.interrupt_s),
                    b2d(cpu.csr.exception),
                    b2d(cpu.icache.io_cpu_resp_valid),
                )
            )
            # if '{:8x}'.format(b2d(cpu.pc)) == '7fc00144':
            #     break
        else:
            cnt += 1
            assert cnt != 100, "Stucked!"
        if pattern == pass_pattern:
            break
        assert pattern != fail_pattern, "Failed at test {:d}".format(
            b2d(cpu.rf.reg_3) // 2
        )
        await FallingEdge(dut.clk)

    a7 = cpu.rf.reg_17
    assert a7 == 93, "a7 is not 93, a7 is {}".format(b2d(a7))

    a0 = cpu.rf.reg_10
    assert a0 == 0, "a0 is not 0, a0 is {}".format(b2d(a0))
