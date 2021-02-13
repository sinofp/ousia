#define LED_BASE 0x20000000

    li a0, LED_BASE

    li t0, 0b01010101
    sb t0, 0(a0)
