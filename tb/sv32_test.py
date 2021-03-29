import cocotb
from cocotb.clock import Clock
from cocotb.triggers import FallingEdge, RisingEdge

satp_ppn = "0000000000000000000000"

vpn1 = "0000000000"
vpn0 = "1111111111"
offset = "010101010101"
va = int(vpn1 + vpn0 + offset, 2)

pte1_ppn = "1111111111111111111111"
pte1 = int(pte1_ppn + "0000000001", 2)  # v = 1

pte2_ppn = "1000000000000000000000"
pte2 = int(pte2_ppn + "0000000011", 2)  # v = 1, r = 1 -> leaf PTE


def bin2dec(x):
    return int(str(x.value), 2)


@cocotb.test()
async def sv32_test(dut):
    clock = Clock(dut.clock, 1, units="ns")  # Create a 1ns period clock on port clock
    cocotb.fork(clock.start())  # Start the clock

    dut.reset <= 1
    await FallingEdge(dut.clock)
    dut.reset <= 0

    dut.io_cpu_req_bits_PRV <= 0
    dut.io_cpu_req_bits_satp_mode <= 1
    dut.io_cpu_req_bits_satp_ppn <= int(satp_ppn, 2)

    dut.io_cpu_req_valid <= 1
    dut.io_cpu_req_bits_addr <= va

    # 1
    await RisingEdge(dut.io_wb_cyc)
    assert dut.io_wb_addr == int(satp_ppn + vpn1 + "00", 2)

    await FallingEdge(dut.clock)
    await FallingEdge(dut.clock)
    dut.io_wb_ack <= 1
    dut.io_wb_rdata <= pte1
    await FallingEdge(dut.clock)
    dut.io_wb_ack <= 0

    # 2
    await RisingEdge(dut.io_wb_cyc)
    print("============addr==", "{:b}".format(bin2dec(dut.io_wb_addr)))
    print("==========myaddr==", "{:b}".format(int(pte1_ppn[2:] + vpn0 + "00", 2)))
    assert dut.io_wb_addr == int(pte1_ppn[2:] + vpn0 + "00", 2)  # todo 34 addr

    await FallingEdge(dut.clock)
    await FallingEdge(dut.clock)
    dut.io_wb_ack <= 1
    dut.io_wb_rdata <= pte2
    await FallingEdge(dut.clock)
    dut.io_wb_ack <= 0

    # 3
    await RisingEdge(dut.io_wb_cyc)
    assert dut.io_wb_addr == int(pte2_ppn[2:] + offset, 2)

    await FallingEdge(dut.clock)
    await FallingEdge(dut.clock)
    dut.io_wb_ack <= 1
    dut.io_wb_rdata <= 42
    assert dut.io_cpu_resp_valid == 0
    assert dut.last_round == 1
    await RisingEdge(dut.clock)
    dut.io_wb_ack <= 0

    # resp
    # assert dut.last_round == 0
    assert dut.io_cpu_resp_valid == 1
    assert dut.io_cpu_resp_bits_data == 42
