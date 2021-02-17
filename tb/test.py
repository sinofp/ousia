from cocotb_test import simulator
from os import system, getcwd, environ
import pytest
from contextlib import contextmanager

insts = environ.get("INSTS", "rv32ui-p-simple").split()

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
    top_v = f"tb/cocotb_top_{elf_name}.v"
    memfile = f"{getcwd()}/meminit/{elf_name}.verilog"
    dumpfile = f"{getcwd()}/meminit/{elf_name}.dump"

    system(f"cp tb/cocotb_top.v {top_v}")
    system(f"""sed -i 's|readmemh.*|readmemh("{memfile}", ram.mem);|' {top_v}""")

    environ["DUMPFILE"] = dumpfile  # riscv_test.py 里用

    try:
        yield top_v
    finally:
        system(f"rm {top_v}")


@pytest.mark.parametrize("inst", insts)
def test_inst(inst):
    elf_dir = "/usr/riscv32-unknown-elf/share/riscv-tests/isa"
    with prepare(elf_dir, inst) as top_v:
        simulator.run(
            verilog_sources=["build/ousia_0/src/ousia_0/Naive.v", "naive_soc.v", top_v],
            includes=includes,
            toplevel="cocotb_top",
            module="riscv_test",
            sim_build="sim_build/" + inst,
            # extra_args=["--trace", "--trace-structs"],
        )


def test_firmware():
    with prepare("firmware", "firmware") as top_v:
        simulator.run(
            verilog_sources=["build/ousia_0/src/ousia_0/Naive.v", "naive_soc.v", top_v],
            includes=includes,
            toplevel="cocotb_top",
            module="firmware_test",
            sim_build="sim_build/firmware",
        )
