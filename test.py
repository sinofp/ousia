from cocotb_test.simulator import run
from os import system, getcwd, environ
import pytest
from pathlib import Path

if "SIM" not in environ:
    environ["SIM"] = "verilator"


@pytest.mark.parametrize(
    "inst",
    [
        "rv32mi-p-breakpoint",  # 其实tdata那些没实现……
        "rv32mi-p-csr",
        "rv32mi-p-illegal",
        "rv32mi-p-ma_addr",
        # "rv32mi-p-ma_fetch",
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
    ],
)
def test_inst(inst):
    isapath = "/usr/riscv-sifive-elf/share/riscv-tests/isa"
    top_v = f"cocotb_top_{inst}.v"
    memfile = f"{getcwd()}/meminit/{inst}.verilog"

    if not Path(memfile).is_file():
        system(f"riscv-sifive-elf-objcopy {isapath}/{inst} -O verilog {memfile}")
        system(f"sed -i 's|@8|@0|g' {memfile}")

    system(f"cp cocotb_top.v {top_v}")
    system(f"""sed -i 's|readmemh.*|readmemh("{memfile}", ram.mem);|' {top_v}""")
    run(
        verilog_sources=["Naive.v", "naive_soc.v", top_v],
        includes=[
            "./build/ousia_0/src/verilog-arbiter_0-r3/src/",
            "./build/ousia_0/src/cdc_utils_0.1/rtl/verilog/",
            "./build/ousia_0/src/wb_intercon_1.2.2-r1/rtl/verilog/",
            "./build/ousia_0/src/ousia-wb_intercon_0/",
            "./build/ousia_0/src/wb_common_1.0.3/",
        ],
        toplevel="cocotb_top",
        module="riscv_test",
        sim_build="sim_build/" + inst,
        # extra_args=["--trace", "--trace-structs"],
    )
    system(f"rm {top_v}")
