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
	      rv32mi-p-scall \
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
	      rv32ui-p-fence_i \
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

verilator:
	cd tool/verilator && \
		autoconf && \
		./configure && \
		make -j $$(nproc) && \
		sudo make install

gcc:
	cd tool/riscv-gnu-toolchain && \
		./configure --prefix=$$PWD/tool/riscv --with-arch=rv32i --with-abi=ilp32 --disable-gdb && \
		make -j $$(nproc)

riscv-tests:
	cd tool/riscv-tests && \
		git submodule update --init --recursive --depth 1 && \
		./configure --prefix=$(RISCV) && \
		sed -i 's|install: all|install: isa|' Makefile && \
		make install RISCV_PREFIX=$(TOOLCHAIN_PREFIX)

firmware/firmware: firmware/start.S firmware/uart.S firmware/led.S
	$(TOOLCHAIN_PREFIX)gcc -static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles -march=rv32i -T firmware/linker.ld -o $@ $<

firmware/firmware.bin: firmware/firmware
	$(TOOLCHAIN_PREFIX)objcopy $< -O binary $@

firmware/firmware.hex: firmware/firmware.bin
	srec_cat $< -Binary -Output $@ -Intel -Output_Block_Size=4 # 32 bit a row

cyc10: firmware/firmware.hex ousia.core board/step-cyc10 # 其实不用写，但写了清楚
	fusesoc --cores-root=. run --target=cyc10 ousia

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

clean:
	-rm -r meminit firmware/firmware* __pycache__ build
	cocotb-clean
	sbt clean

.PHONY: verilator gcc riscv-tests cyc10 clean
