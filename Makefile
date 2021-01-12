TOPLEVEL_LANG = verilog
VERILOG_SOURCES = $(shell ls *.v | grep -v wb_common_params)
TOPLEVEL = cocotb_top
MODULE = riscv_test

include $(shell cocotb-config --makefiles)/Makefile.sim
