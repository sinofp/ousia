#!/usr/bin/env bash
set -euo pipefail

insts=(
    beq
    bne
    blt
    bge
    bltu
    bgeu
    jalr
    jal
    lui
    auipc
    addi
    slli
    slti
    sltiu
    xori
    srli
    srai
    ori
    andi
    add
    sub
    sll
    slt
    sltu
    xor
    srl
    sra
    or
    and
    # lb  # test 2
    # lh  # test 2
    # lw  # test 2
    # lbu # test 2
    # lhu # test 2
    # sb  # test 2
    # sh  # test 2
    # sw  # test 2
    # fence
    # fence_i
    # amoadd_w
    # amoxor_w
    # amoor_w
    # amoand_w
    # amomin_w
    # amomax_w
    # amominu_w
    # amomaxu_w
    # amoswap_w
    # lr_w
    # sc_w
    # ecall
    # ebreak
    # uret
    # sret
    # mret
    # dret
    # sfence_vma
    # wfi
    # csrrw
    # csrrs
    # csrrc
    # csrrwi
    # csrrsi
    # csrrci
)

STATUS=0
ISAPATH="/usr/riscv-sifive-elf/share/riscv-tests/isa"
for inst in "${insts[@]}"; do
    memfile="$inst.verilog"
    if [ ! -e "$memfile" ]; then
        riscv-sifive-elf-objcopy "$ISAPATH/rv32ui-p-$inst" --only-section .text.init -O verilog "$PWD/$memfile"
        sed -i 's|@.*||g' "$memfile"
	sed -i 's|6F 00 C0 04|6F 00 C0 17|' "$memfile" # 直接跳到test_2
    fi
    SECONDS=0
    sed -i "s|\".*\"|\"$PWD/$memfile\"|" tb.v
    sed -i "s|async def test_.*(dut)|async def test_$inst(dut)|" riscv_test.py
    logfile="$inst.log"
    echo ">>> $inst"
    # EXTRA_ARGS="--trace --trace-structs" make SIM=verilator >"$logfile"
    make SIM=verilator >"$logfile"
    if grep 'Test Failed' "$logfile" &>/dev/null; then
	grep -Po '\K(Attribute|Assertion)Error:.*' "$logfile"
        echo ":("
        STATUS=1
    fi
    echo "<<< $SECONDS seconds used."
done
exit $STATUS
