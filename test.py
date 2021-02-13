from cocotb_test import simulator
from os import system, getcwd, environ
import pytest
from pathlib import Path
from contextlib import contextmanager

insts = [
    "rv32mi-p-breakpoint",  # 其实tdata那些没实现……
    "rv32mi-p-csr",
    "rv32mi-p-illegal",
    "rv32mi-p-ma_addr",
    "rv32mi-p-ma_fetch",
    "rv32mi-p-mcsr",
    "rv32mi-p-sbreak",
    # "rv32mi-p-scall", # write_tohost，spike也在这死循环
    "rv32mi-p-shamt",
    "rv32ui-p-add",
    "rv32ui-p-addi",
    "rv32ui-p-and",
    "rv32ui-p-andi",
    "rv32ui-p-auipc",
    "rv32ui-p-beq",
    "rv32ui-p-bge",
    "rv32ui-p-bgeu",
    "rv32ui-p-blt",
    "rv32ui-p-bltu",
    "rv32ui-p-bne",
    # "rv32ui-p-fence_i",
    "rv32ui-p-jal",
    "rv32ui-p-jalr",
    "rv32ui-p-lb",
    "rv32ui-p-lbu",
    "rv32ui-p-lh",
    "rv32ui-p-lhu",
    "rv32ui-p-lui",
    "rv32ui-p-lw",
    "rv32ui-p-or",
    "rv32ui-p-ori",
    "rv32ui-p-sb",
    "rv32ui-p-sh",
    "rv32ui-p-simple",
    "rv32ui-p-sll",
    "rv32ui-p-slli",
    "rv32ui-p-slt",
    "rv32ui-p-slti",
    "rv32ui-p-sltiu",
    "rv32ui-p-sltu",
    "rv32ui-p-sra",
    "rv32ui-p-srai",
    "rv32ui-p-srl",
    "rv32ui-p-srli",
    "rv32ui-p-sub",
    "rv32ui-p-sw",
    "rv32ui-p-xor",
    "rv32ui-p-xori",
]

if "SIM" not in environ:
    environ["SIM"] = "verilator"

includes = [
    "./build/ousia_0/src/verilog-arbiter_0-r3/src/",
    "./build/ousia_0/src/cdc_utils_0.1/rtl/verilog/",
    "./build/ousia_0/src/wb_intercon_1.2.2-r1/rtl/verilog/",
    "./build/ousia_0/src/ousia-wb_intercon_0/",
    "./build/ousia_0/src/wb_common_1.0.3/",
    "./build/ousia_0/src/uart16550_1.5.5-r1/rtl/verilog/",
    "./build/ousia_0/src/gpio_0/",
]


@contextmanager
def prepare(elf_dir, elf_name):
    top_v = f"cocotb_top_{elf_name}.v"
    memfile = f"{getcwd()}/meminit/{elf_name}.verilog"
    dumpfile = f"{getcwd()}/meminit/{elf_name}.dump"

    if not Path(memfile).is_file():
        system(f"riscv-sifive-elf-objcopy {elf_dir}/{elf_name} -O verilog {memfile}")
        system(f"sed -i 's|@8|@0|g' {memfile}")

    if not Path(dumpfile).is_file():
        system(f"riscv-sifive-elf-objdump -D {elf_dir}/{elf_name} > {dumpfile}")

    system(f"cp cocotb_top.v {top_v}")
    system(f"""sed -i 's|readmemh.*|readmemh("{memfile}", ram.mem);|' {top_v}""")

    environ["DUMPFILE"] = dumpfile  # riscv_test.py 里用

    try:
        yield top_v
    finally:
        system(f"rm {top_v}")


@pytest.mark.parametrize("inst", insts)
def test_inst(inst):
    elf_dir = "/usr/riscv-sifive-elf/share/riscv-tests/isa"
    with prepare(elf_dir, inst) as top_v:
        simulator.run(
            verilog_sources=["Naive.v", "naive_soc.v", top_v],
            includes=includes,
            toplevel="cocotb_top",
            module="riscv_test",
            sim_build="sim_build/" + inst,
            # extra_args=["--trace", "--trace-structs"],
        )


def test_uart():
    simulator.run(
        verilog_sources=[
            "./build/ousia_0/src/uart16550_1.5.5-r1/rtl/verilog/uart_top.v"
        ],
        includes=[
            "./build/ousia_0/src/uart16550_1.5.5-r1/rtl/verilog/",
        ],
        toplevel="uart_top",
        module="uart_test",
        sim_build="sim_build/uart",
    )


def test_firmware():
    with prepare("firmware", "firmware") as top_v:
        simulator.run(
            verilog_sources=["Naive.v", "naive_soc.v", top_v],
            includes=includes,
            # includes=[
            #     "./build/ousia_0/src/verilog-arbiter_0-r3/src/",
            #     "./build/ousia_0/src/cdc_utils_0.1/rtl/verilog/",
            #     "./build/ousia_0/src/wb_intercon_1.2.2-r1/rtl/verilog/",
            #     "./build/ousia_0/src/ousia-wb_intercon_0/",
            #     "./build/ousia_0/src/wb_common_1.0.3/",
            #     "./build/ousia_0/src/uart16550_1.5.5-r1/rtl/verilog/",
            #     "./build/ousia_0/src/gpio_0/",
            # ],
            toplevel="cocotb_top",
            module="firmware_test",
            sim_build="sim_build/firmware",
        )
