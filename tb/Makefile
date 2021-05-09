SIM ?= verilator
TOPLEVEL_LANG ?= verilog

ROOT_DIR = $(PWD)/..
VERILOG_SOURCES += $(PWD)/cocotb_top_debug.v $(ROOT_DIR)/naive_soc.v $(ROOT_DIR)/Naive.v
VERILOG_SOURCES +=    $(wildcard $(ROOT_DIR)/build/ousia_0/src/verilog-arbiter_0-r3/src/*.v) \
		      $(wildcard $(ROOT_DIR)/build/ousia_0/src/cdc_utils_0.1/rtl/verilog/*.v) \
		      $(wildcard $(ROOT_DIR)/build/ousia_0/src/wb_intercon_1.2.2-r1/rtl/verilog/*.v) \
		      $(wildcard $(ROOT_DIR)/build/ousia_0/src/ousia-wb_intercon_0/*.v) \
		      $(wildcard $(ROOT_DIR)/build/ousia_0/src/uart16550_1.5.5-r1/rtl/verilog/*.v) \
		      $(wildcard $(ROOT_DIR)/build/ousia_0/src/gpio_0/*.v)
EXTRA_ARGS +=  -I$(ROOT_DIR)/build/ousia_0/src/wb_common_1.0.3/ \
	       -I$(ROOT_DIR)/build/ousia_0/src/uart16550_1.5.5-r1/rtl/verilog/ \

TOPLEVEL = cocotb_top

MODULE = debugger

include $(shell cocotb-config --makefiles)/Makefile.sim
