from sys import argv

Output_Block_Size = 4

binfile = argv[1]


def checksum(record):
    rec = record[1:]

    rec_sum = 0
    for i in range(0, len(rec), 2):
        rec_sum += int(rec[i : i + 2], 16)

    return ("%02x" % (256 - rec_sum % 256))[-2:].upper()
    #                                       ^ rec_sum = 0, 100[-2:] = 00


print(":020000040000FA")
with open(binfile, "rb") as f:
    addr = 0
    while word := f.read(Output_Block_Size):
        byte_count = len(word)
        data = word.hex()
        record = ":{:02x}{:04x}00{}".format(byte_count, addr, data).upper()
        addr += byte_count
        print(record + checksum(record))
print(":00000001FF")
