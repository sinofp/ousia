RISCV = $(PWD)/tool/riscv
TOOLCHAIN_PREFIX = $(RISCV)/bin/riscv32-unknown-elf-
RVTEST_ISA_PATH = $(RISCV)/share/riscv-tests/isa
RVTEST_OBJS = rv32mi-p-breakpoint \
	      rv32mi-p-csr \
	      rv32mi-p-illegal \
	      rv32mi-p-ma_addr \
	      rv32mi-p-ma_fetch \
	      rv32mi-p-mcsr \
	      rv32mi-p-sbreak \
	      rv32mi-p-shamt \
	      rv32ui-p-add \
	      rv32ui-p-addi \
	      rv32ui-p-and \
	      rv32ui-p-andi \
	      rv32ui-p-auipc \
	      rv32ui-p-beq \
	      rv32ui-p-bge \
	      rv32ui-p-bgeu \
	      rv32ui-p-blt \
	      rv32ui-p-bltu \
	      rv32ui-p-bne \
	      rv32ui-p-jal \
	      rv32ui-p-jalr \
	      rv32ui-p-lb \
	      rv32ui-p-lbu \
	      rv32ui-p-lh \
	      rv32ui-p-lhu \
	      rv32ui-p-lui \
	      rv32ui-p-lw \
	      rv32ui-p-or \
	      rv32ui-p-ori \
	      rv32ui-p-sb \
	      rv32ui-p-sh \
	      rv32ui-p-simple \
	      rv32ui-p-sll \
	      rv32ui-p-slli \
	      rv32ui-p-slt \
	      rv32ui-p-slti \
	      rv32ui-p-sltiu \
	      rv32ui-p-sltu \
	      rv32ui-p-sra \
	      rv32ui-p-srai \
	      rv32ui-p-srl \
	      rv32ui-p-srli \
	      rv32ui-p-sub \
	      rv32ui-p-sw \
	      rv32ui-p-xor \
	      rv32ui-p-xori \
	      # rv32mi-p-scall \
	      rv32ui-p-fence_i \

TEST_INSTS ?= $(RVTEST_OBJS)
PYTEST_EXTRA_ARGS = -n auto

# [test]
test: meminit build
	pytest tb/test.py $(PYTEST_EXTRA_ARGS)

test-inst: meminit build
	INSTS='$(TEST_INSTS)' pytest tb/test.py -k inst $(PYTEST_EXTRA_ARGS)

test-misc: meminit build
	pytest tb/test.py -k 'not inst' $(PYTEST_EXTRA_ARGS)

meminit: firmware/firmware $(RVTEST_ISA_PATH)
	-mkdir meminit
	for RVTEST in $(RVTEST_OBJS); do \
		$(TOOLCHAIN_PREFIX)objcopy $(RVTEST_ISA_PATH)/$$RVTEST -O verilog meminit/$$RVTEST.verilog ; \
		sed -i 's|@8|@0|g' meminit/$$RVTEST.verilog ; \
		cp $(RVTEST_ISA_PATH)/$$RVTEST.dump meminit ; \
		done
	# 分开？
	$(TOOLCHAIN_PREFIX)objcopy $< -O verilog meminit/firmware.verilog
	$(TOOLCHAIN_PREFIX)objdump -D $< > meminit/firmware.dump

# [tools]
verilator:
	git submodule update --init --depth 1 tool/$@
	cd tool/verilator && \
		autoconf && \
		./configure && \
		make -j $$(nproc) && \
		sudo make install

gcc:
	# 不能用submodule，因为riscv-gnu-toolchain/.git必须是目录，不能是文件
	git clone --depth 1 https://github.com/riscv/riscv-gnu-toolchain tool/riscv-gnu-toolchain
	cd tool/riscv-gnu-toolchain && \
		./configure --prefix=$(RISCV) --with-arch=rv32i --with-abi=ilp32 --disable-gdb && \
		make -j $$(nproc)

riscv-tests:
	git submodule update --init --recursive --depth 1 tool/$@
	cd tool/riscv-tests && \
		./configure --prefix=$(RISCV) && \
		sed -i 's|install: all|install: isa|' Makefile && \
		make install RISCV_PREFIX=$(TOOLCHAIN_PREFIX)

# [firmware]
firmware/firmware: firmware/start.S firmware/uart.S firmware/led.S
	$(TOOLCHAIN_PREFIX)gcc -static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles -march=rv32i -T firmware/linker.ld -o $@ $<

firmware/firmware.bin: firmware/firmware
	$(TOOLCHAIN_PREFIX)objcopy $< -O binary $@

firmware/firmware.hex: firmware/firmware.bin
	srec_cat $< -Binary -Output $@ -Intel -Output_Block_Size=4 # 32 bit a row

# [verilog]
Naive.v: src build.sbt
	git submodule update --init --depth 1 tool/api-config-chipsalliance/
	sbt run

build: ousia.core Naive.v
	fusesoc --cores-root=. run ousia

# [board]
cyc10: firmware/firmware.hex build
	fusesoc --cores-root=. run --target=cyc10 ousia

# [misc]
clean:
	-rm -r meminit firmware/firmware* __pycache__ build
	cocotb-clean
	sbt clean

.PHONY: verilator gcc riscv-tests cyc10 clean test-inst test-misc test
