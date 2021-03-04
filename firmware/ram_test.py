begin = """#include "riscv_test.h"
RVTEST_CODE_BEGIN
"""

middle = """
RVTEST_PASS

RVTEST_CODE_END

.data
RVTEST_DATA_BEGIN"""

end = """RVTEST_DATA_END"""

put_str = """la a1, str{}
jal put_str
la a1, str_crlf
jal put_str
"""

dot_string = '''str{}:
    .string "{:x}"'''

num = 10

print(begin)

for i in range(num):
    print(put_str.format(i))

print(middle)

for i in range(num):
    print(dot_string.format(i, i))

print(end)
