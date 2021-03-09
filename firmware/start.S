#define LED_BASE 0x20000000

#define UART_BASE 0x10000000
#define DLB1 0
#define DLB2 1*4
#define LCR 3*4
#define LS 5*4

#define TEST(name)           \
        .globl name;         \
	.globl name ## _ret; \
	j name;              \
name ## _ret:

.globl _start
.globl put_str
.globl dead_loop
.globl str_tab
.globl str_crlf
.globl str_ok
.globl str_err

_start:
    # INIT
    la t0, xcpt_handler
    csrw mtvec, t0

    # LED
    li a1, 0b00000001
    jal set_led

    # UART
    jal uart_init

    la a1, str_uart
    jal put_str
    la a1, str_tab
    jal put_str
    la a1, str_ok
    jal put_str
    la a1, str_crlf
    jal put_str

    # RVTEST
    TEST(addi)
    TEST(add)
    TEST(andi)
    TEST(and)
    TEST(auipc)
    TEST(beq)
    TEST(bge)
    TEST(bgeu)
    TEST(blt)
    TEST(bltu)
    TEST(bne)
    TEST(jalr)
    TEST(jal)
    TEST(lb)
    # TEST(lbu)
    TEST(lh)
    # TEST(lhu)
    TEST(lw)
    TEST(lui)
    TEST(ori)
    TEST(or)
    TEST(sb)
    TEST(sh)
    TEST(slli)
    TEST(sll)
    TEST(slti)
    TEST(sltiu)
    TEST(slt)
    TEST(sltu)
    TEST(srai)
    TEST(sra)
    TEST(srli)
    TEST(srl)
    TEST(sub)
    TEST(sw)
    TEST(xori)
    TEST(xor)

    # TEST(ram_test)

    li a1, 0b00000000
    jal set_led

    # END
    la a1, str_crlf
    jal put_str
    # j _start
    j dead_loop

xcpt_handler:
    la a1, str_crlf
    jal put_str
    la a1, str_xcpt
    jal put_str
    la a1, str_tab
    jal put_str
    csrr t0, mcause
xcpt_illegal_instruction:
    li t1, 0x2
    bne t0, t1, xcpt_machine_ecall
    la a1, str_illegal_instruction
    jal put_str
    j xcpt_handler_end
xcpt_machine_ecall:
    li t1, 0xb
    bne t0, t1, xcpt_breakpoint
    la a1, str_machine_ecall
    jal put_str
    j xcpt_handler_end
xcpt_breakpoint:
    li t1, 0x3
    bne t0, t1, xcpt_misaligned_fetch
    la a1, str_breakpoint
    jal put_str
    j xcpt_handler_end
xcpt_misaligned_fetch:
    li t1, 0x0
    bne t0, t1, xcpt_misaligned_store
    la a1, str_misaligned_fetch
    jal put_str
    j xcpt_handler_end
xcpt_misaligned_store:
    li t1, 0x6
    bne t0, t1, xcpt_misaligned_load
    la a1, str_misaligned_store
    jal put_str
    j xcpt_handler_end
xcpt_misaligned_load:
    li t1, 0x4
    bne t0, t1, xcpt_other
    la a1, str_misaligned_load
    jal put_str
    j xcpt_handler_end
xcpt_other:
    la a1, str_other
    jal put_str
    j xcpt_handler_end
xcpt_handler_end:
    la a1, str_crlf
    jal put_str
    j dead_loop

set_led: # pass a1, 1 on 0 off
    li a0, LED_BASE
    sb a1, 0(a0)
    ret

uart_init:
    li a0, UART_BASE

    li t0, 0x80
    sb t0, LCR(a0)

    li t0, 0
    sb t0, DLB2(a0)

    li t0, 8
    sb t0, DLB1(a0)

    li t0, 3
    sb t0, LCR(a0)

    ret

put_str: # pass a1 for str addr
    li a0, UART_BASE
    lb t0, 0(a1)

next:
    mv t1, ra # save ra for `jal put_str`
    jal put_byte
    mv ra, t1
    addi a1, a1, 1
    lb t0, 0(a1)
    bne t0, zero, next

    ret # for put_str

put_byte:
    lb t6, LS(a0)
    andi t6, t6, 0x20
    beqz t6, put_byte

    sb t0, 0(a0)
    ret

dead_loop:
    j dead_loop

.data
str_uart:
    .string "UART"
str_tab:
    .string "\t"
str_crlf:
    .string "\r\n"
str_ok:
    .string "OK"
str_err:
    .string "ERR"
str_xcpt:
    .string "exception:"
str_illegal_instruction:
    .string "illegal instruction"
str_machine_ecall:
    .string "machine ecall"
str_breakpoint:
    .string "breakpoint"
str_misaligned_fetch:
    .string "misaligned_fetch"
str_misaligned_store:
    .string "misaligned_store"
str_misaligned_load:
    .string "misaligned_load"
str_other:
    .string "other"
