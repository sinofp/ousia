import cocotb
from cocotb.clock import Clock
from cocotb.triggers import FallingEdge
from collections import deque
from os import environ
import re


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
    rf = {
        "ra": cpu.rf.reg_1,
        "sp": cpu.rf.reg_2,
        "gp": cpu.rf.reg_3,
        "tp": cpu.rf.reg_4,
        "t0": cpu.rf.reg_5,
        "t1": cpu.rf.reg_6,
        "t2": cpu.rf.reg_7,
        "s0": cpu.rf.reg_8,
        "fp": cpu.rf.reg_8,
        "s1": cpu.rf.reg_9,
        "a0": cpu.rf.reg_10,
        "a1": cpu.rf.reg_11,
        "a2": cpu.rf.reg_12,
        "a3": cpu.rf.reg_13,
        "a4": cpu.rf.reg_14,
        "a5": cpu.rf.reg_15,
        "a6": cpu.rf.reg_16,
        "a7": cpu.rf.reg_17,
        "s2": cpu.rf.reg_18,
        "s3": cpu.rf.reg_19,
        "s4": cpu.rf.reg_20,
        "s5": cpu.rf.reg_21,
        "s6": cpu.rf.reg_22,
        "s7": cpu.rf.reg_23,
        "s8": cpu.rf.reg_24,
        "s9": cpu.rf.reg_25,
        "s10": cpu.rf.reg_26,
        "s11": cpu.rf.reg_27,
        "t3": cpu.rf.reg_28,
        "t4": cpu.rf.reg_29,
        "t5": cpu.rf.reg_30,
        "t6": cpu.rf.reg_31,
    }

    cnt = 0

    while True:
        if cpu.next_inst == 1:
            cnt = 0
            inst = "{:08x}".format(b2d(cpu.inst))
            pattern.append(inst)
            print(
                "pc = {:8x} ({}) {}|va={:x}|pa={:x}|satp_ppn={:x}|pte_ppn={:x}|s={}|pf={}|xcpt={}|scause={}|deleg2S={}|rdata={}|wdata={}|we={}|a0={:x}|a1={:x}|a3={:x}|a4={:x}|a5={:x}|a7={:x}|t0={:x}".format(
                    b2d(cpu.pc),
                    inst,
                    asm.get(inst, "???"),
                    b2d(cpu.dcache.io_cpu_req_bits_addr),
                    b2d(cpu.dcache.addr),
                    b2d(cpu.dcache.io_cpu_req_bits_satp_ppn),
                    b2d(cpu.dcache.pte_ppn),
                    b2d(cpu.dcache.state),
                    # b2d(cpu.dcache.level),
                    b2d(cpu.dcache.io_cpu_resp_bits_page_fault),
                    b2d(cpu.csr.exception),
                    # b2d(cpu.csr.mcause),
                    b2d(cpu.csr.scause),
                    b2d(cpu.csr.deleg2S),
                    # b2d(cpu.dcache.trans_on),
                    # b2d(cpu.dcache.io_cpu_resp_valid),
                    b2d(cpu.dcache.io_wb_rdata),
                    b2d(cpu.dcache.io_wb_wdata),
                    b2d(cpu.dcache.io_wb_we),
                    # b2d(cpu.csr.mscratch),
                    # b2d(cpu.csr.sscratch),
                    b2d(rf["a0"]),
                    b2d(rf["a1"]),
                    b2d(rf["a3"]),
                    b2d(rf["a4"]),
                    b2d(rf["a5"]),
                    b2d(rf["a7"]),
                    b2d(rf["t0"]),
                )
            )
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
