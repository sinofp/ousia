#define RVTEST_RV32U
#define TESTNUM gp

#define STR_VALUE(arg) #arg
#define STR(arg) STR_VALUE(arg) // weird `#` usage
#define TEST_NAME_STR STR(TEST_NAME)

#define _RET(arg) arg ## _ret
#define RET(arg) _RET(arg) // weird `##` usage
#define TEST_NAME_RET RET(TEST_NAME)

#define RVTEST_CODE_BEGIN           \
        .globl TEST_NAME;           \
        .globl TEST_NAME_RET;       \
	.globl str_tab;             \
	.globl str_crlf;            \
	.globl str_ok;              \
        .globl str_err;             \
TEST_NAME:                          \
        la a1, str_test_name;       \
	jal put_str;                \
	la a1, str_tab;             \
	jal put_str;

#define RVTEST_CODE_END

#define RVTEST_PASS                 \
        la a1, str_ok;              \
        jal put_str;                \
        la a1, str_crlf;            \
        jal put_str;                \
        j TEST_NAME_RET;

#define RVTEST_FAIL                 \
        la a1, str_err;             \
        jal put_str;                \
        la a1, str_crlf;            \
        jal put_str;                \
        j TEST_NAME_RET;

#define RVTEST_DATA_BEGIN           \
str_test_name:                      \
	.string TEST_NAME_STR;      \
.align 4;

#define RVTEST_DATA_END
