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
    jal  # test 3
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
    lb  # test 2
    lh  # test 2
    lw  # test 2
    lbu # test 2
    lhu # test 2
    sb  # test 2
    sh  # test 2
    sw  # test 2
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
    fi
    SECONDS=0
    sed -i "s|\".*\"|\"$PWD/$memfile\"|" tb.v
    sed -i "s|async def test_.*(dut)|async def test_$inst(dut)|" riscv_test.py
    logfile="$inst.log"
    echo ">>> $inst"
    make SIM=verilator >"$logfile"
    if grep 'Failed at' "$logfile" &>/dev/null; then
        grep -Po 'AssertionError: \KFailed at test \d' "$logfile"
        echo ":("
        STATUS=1
    fi
    echo "<<< $SECONDS seconds used."
done
exit $STATUS
