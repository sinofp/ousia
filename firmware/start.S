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
    TEST(and)
    TEST(auipc)
    TEST(bge)
    TEST(jalr)
    TEST(jal)
    TEST(lui)
    TEST(sra)

    # END
    la a1, str_crlf
    jal put_str
    j _start

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
