#include "riscv_test.h"
RVTEST_CODE_BEGIN

    li t0, 0xffffffff
    li t1, 0
wait:
    addi t1, t1, 1
    bne t1, t0, wait

    li t0, 0x30000000
    li t1, 42
    sw t1, 0(t0)
    # lw t2, 0(t0)
    # beq t1, t2, succ

RVTEST_FAIL

succ:
RVTEST_PASS

RVTEST_CODE_END

.data
RVTEST_DATA_BEGIN
RVTEST_DATA_END

