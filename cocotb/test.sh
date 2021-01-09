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
    # lb
    # lh
    lw
    # lbu
    # lhu
    # sb
    # sh
    sw
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

for inst in "${insts[@]}"; do
    memfile="$inst.verilog"
    if [ ! -e "$memfile" ]; then
        riscv-sifive-elf-objcopy "/usr/riscv-sifive-elf/share/riscv-tests/isa/rv32ui-p-$inst" -O verilog "$PWD/$memfile"
        sed -i 's|@.*||g' "$memfile"
    fi
    SECONDS=0
    sed -i "s|\".*\"|\"$PWD/$memfile\"|" Top.v
    sed -i "s|async def test_.*(dut)|async def test_$inst(dut)|" riscv_test.py
    echo ">>>>>>>>>> $inst"
    make SIM=verilator >"$inst.log"
    if grep 'Test Failed' "$inst.log" &>/dev/null; then
        echo "Failed :("
        exit 1
    fi
    echo "<<<<<<<<<< $SECONDS seconds used."
done
