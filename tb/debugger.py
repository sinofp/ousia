import cocotb
from cocotb.clock import Clock
from cocotb.triggers import FallingEdge
from prompt_toolkit import PromptSession
from prompt_toolkit.auto_suggest import AutoSuggestFromHistory
from prompt_toolkit.completion import WordCompleter
from prompt_toolkit import print_formatted_text, HTML
from os import environ
from os.path import basename
from time import strftime
import shutil
import re


asm = {}
with open(environ["DUMPFILE"]) as f:
    for line in f.readlines():
        res = re.search(r"(?:.{8}|.{4}):\s+([0-9a-f]{8})\s+([^#<\n]+)", line)
        if res is not None:
            group = res.groups()
            asm[group[0]] = group[1]


def b2d(x) -> int:
    """binary to decimal"""
    return int(str(x.value), 2)


def toHex(x: int) -> str:
    return "0x{:08x}".format(x)


def b2h(x) -> str:
    return toHex(b2d(x))


def dict2str(d: dict) -> str:
    items = sorted(d.items(), key=lambda x: x[0])
    return "|".join([f"{k} = {b2h(v)}" for k, v in items])


def get_status(cpu):
    inst = "{:08x}".format(b2d(cpu.inst))
    return "pc = {:9x} ({}) {}".format(
        b2d(cpu.pc),
        inst,
        asm.get(inst, "???"),
    )


completer = WordCompleter(["8000", "show"])
session = PromptSession(
    ">>> ",
    auto_suggest=AutoSuggestFromHistory(),
    mouse_support=True,
    completer=completer,
)


@cocotb.test()
async def debugger(dut):
    clock = Clock(dut.clk, 1, units="ns")  # Create a 1ns period clock on port clk
    cocotb.fork(clock.start())  # Start the clock

    breakpoints = []
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

    csr = {
        "xcpt": cpu.csr.io_xcpt,
        "mcause": cpu.csr.mcause,
        "PRV": cpu.csr.PRV,
    }

    misc = {
        "pc": cpu.pc,
        "dmem_addr": cpu.dcache_io_cpu_req_bits_addr,
        "dmem_wdata": cpu.dcache_io_cpu_req_bits_data,
        "dmem_sel": cpu.dcache_io_cpu_req_bits_sel,
        "dmem_we": cpu.dcache_io_cpu_req_bits_we,
        "dmem_en": cpu.dcache_io_cpu_req_valid,
        "dmem_ack": cpu.dcache_io_cpu_resp_valid,
        "dmem_rdata": cpu.dcache_io_cpu_resp_bits_data,
    }

    def print_breakpoints():
        print("breakpoints: ", end="")
        for bp in breakpoints:
            print(toHex(bp), end=" ")
        print()

    def add_breakpoint(text):
        bpc = int(text.split()[1], 16)
        breakpoints.append(bpc)
        print_breakpoints()

    def del_breakpoint(text):
        bpc = int(text.split()[1], 16)
        breakpoints.remove(bpc)
        print_breakpoints()

    usage = """
show: show regs, csr, misc
b ???: add breakpoint at 0x???
d ???: delete breakpoint at 0x???
n: next step
<blank>: next step & show
c: continue
r: run"""

    print_formatted_text(HTML("<b><i>WELCOME</i></b>"))
    while True:
        text: str = session.prompt()
        if text == "r":
            _ = dut.reset <= 1
            await FallingEdge(dut.clk)
            _ = dut.reset <= 0
            print_formatted_text(HTML("<b><i>STARTED</i></b>"))
            break
        elif text.startswith("b"):
            add_breakpoint(text)
        elif text.startswith("d"):
            del_breakpoint(text)
        else:
            print(usage)

    def save_exit():
        print_breakpoints()
        log_file = "log" + strftime("%m%d-%H%M%S")
        with open(log_file, "w") as f:
            f.writelines(log)
        dump_file = environ["DUMPFILE"]
        shutil.copy(dump_file, log_file + basename(dump_file))
        exit()

    log = []
    try:
        cnt = 0
        single_step = False
        while True:
            if cpu.next_inst == 1:
                cnt = 0

                txt_pc_inst_asm = get_status(cpu)
                txt_regs = dict2str(rf)
                txt_csr = dict2str(csr)
                txt_misc = dict2str(misc)
                log.append(
                    "|".join([txt_pc_inst_asm, txt_regs, txt_csr, txt_misc]) + "\n"
                )

                print(txt_pc_inst_asm)

                pc = b2d(cpu.pc)
                if pc in breakpoints or single_step:
                    single_step = False
                    while True:
                        text: str = session.prompt().strip()
                        if text.startswith("b"):
                            add_breakpoint(text)
                        elif text.startswith("d"):
                            del_breakpoint(text)
                        elif text == "n":
                            single_step = True
                            break
                        elif text == "show":
                            print_formatted_text(HTML(f"<blue>{txt_regs}</blue>"))
                            print_formatted_text(HTML(f"<red>{txt_csr}</red>"))
                            print_formatted_text(HTML(f"<green>{txt_misc}</green>"))
                            print_breakpoints()
                            print("---")
                        elif text == "":
                            print_formatted_text(HTML(f"<blue>{txt_regs}</blue>"))
                            print_formatted_text(HTML(f"<red>{txt_csr}</red>"))
                            print_formatted_text(HTML(f"<green>{txt_misc}</green>"))
                            print_breakpoints()
                            print("---")
                            single_step = True
                            break
                        elif text == "c":
                            break
                        else:
                            print(usage)
            else:
                cnt += 1
                if cnt == 100:
                    print(get_status(cpu))
                    print_formatted_text(HTML("<b><i>STUCK</i></b>"))
                    save_exit()
            # next cycle
            await FallingEdge(dut.clk)
    except KeyboardInterrupt:
        save_exit()
