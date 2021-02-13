#define UART_BASE 0x10000000
#define DLB1 0
#define DLB2 1*4
#define LCR 3*4
#define LS 5*4

    li a0, UART_BASE

    li t0, 0x80
    sb t0, LCR(a0)

    li t0, 0
    sb t0, DLB2(a0)

    li t0, 8
    sb t0, DLB1(a0)

    li t0, 3
    sb t0, LCR(a0)

put_str:
    la a1, str
    lb t0, 0(a1)

next:
    jal put_byte
    addi a1, a1, 1
    lb t0, 0(a1)
    bne t0, zero, next

    j put_str # dead loop

put_byte:
    lb t6, LS(a0)
    andi t6, t6, 0x20
    beqz t6, put_byte

    sb t0, 0(a0)
    ret

str:
    .section .data
    .string "UART OK!!\r\n" # ???
