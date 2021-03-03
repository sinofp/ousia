from sys import argv
from contextlib import contextmanager
from os.path import splitext

Output_Block_Size = 4

binfile = argv[1]


def checksum(record):
    rec = record[1:]

    rec_sum = 0
    for i in range(0, len(rec), 2):
        rec_sum += int(rec[i : i + 2], 16)

    return ("%02x" % (256 - rec_sum % 256))[-2:].upper()
    #                                       ^ rec_sum = 0, 100[-2:] = 00


@contextmanager
def open_hex(binfile):
    basename = splitext(binfile)[0] + "{:02b}.hex"
    hex_files = [open(basename.format(i), "w") for i in range(Output_Block_Size)]
    try:
        yield hex_files
    finally:
        for f in hex_files:
            f.close()


with open(binfile, "rb") as f:
    with open_hex(binfile) as hex_files:
        for hf in hex_files:
            hf.write(":020000040000FA\n")

        addr = 0
        while word := f.read(Output_Block_Size):
            byte_count = len(word)
            data = word.hex()
            for i in range(byte_count):
                record = ":01{:04x}00{}".format(addr, data[i * 2 : (i + 1) * 2]).upper()
                hex_files[i].write(record + checksum(record) + "\n")
            addr += 1

        for hf in hex_files:
            hf.write(":00000001FF\n")
