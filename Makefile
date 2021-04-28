RISCV = $(PWD)/tool/riscv
TOOLCHAIN_PREFIX = $(RISCV)/bin/riscv32-unknown-elf-

RVTEST_ISA_PATH = $(RISCV)/share/riscv-tests/isa
TEST_INSTS = rv32mi-p-breakpoint  rv32mi-p-csr  rv32mi-p-illegal  rv32mi-p-ma_addr \
	     rv32mi-p-ma_fetch  rv32mi-p-mcsr  rv32mi-p-sbreak  rv32mi-p-shamt \
	     rv32ui-p-add  rv32ui-p-addi  rv32ui-p-and  rv32ui-p-andi \
	     rv32ui-p-auipc  rv32ui-p-beq  rv32ui-p-bge  rv32ui-p-bgeu \
	     rv32ui-p-blt  rv32ui-p-bltu  rv32ui-p-bne  rv32ui-p-jal \
	     rv32ui-p-jalr  rv32ui-p-lb  rv32ui-p-lbu  rv32ui-p-lh \
	     rv32ui-p-lhu  rv32ui-p-lui  rv32ui-p-lw  rv32ui-p-or \
	     rv32ui-p-ori  rv32ui-p-sb  rv32ui-p-sh  rv32ui-p-simple \
	     rv32ui-p-sll  rv32ui-p-slli  rv32ui-p-slt  rv32ui-p-slti \
	     rv32ui-p-sltiu  rv32ui-p-sltu  rv32ui-p-sra  rv32ui-p-srai \
	     rv32ui-p-srl  rv32ui-p-srli  rv32ui-p-sub  rv32ui-p-sw \
	     rv32ui-p-xor  rv32ui-p-xori  rv32ui-p-fence_i \
	     rv32ui-v-add rv32ui-v-addi rv32ui-v-and rv32ui-v-andi \
	     rv32ui-v-auipc rv32ui-v-bge rv32ui-v-bgeu \
	     rv32ui-v-blt rv32ui-v-bltu rv32ui-v-bne \
	     rv32ui-v-jal rv32ui-v-jalr rv32ui-v-lb rv32ui-v-lbu \
	     rv32ui-v-lh rv32ui-v-lhu rv32ui-v-lui rv32ui-v-lw \
	     rv32ui-v-or rv32ui-v-ori rv32ui-v-sb rv32ui-v-sh \
	     rv32ui-v-simple rv32ui-v-sll rv32ui-v-slli rv32ui-v-slt \
	     rv32ui-v-slti rv32ui-v-sltiu rv32ui-v-sltu rv32ui-v-sra \
	     rv32ui-v-srai rv32ui-v-srl rv32ui-v-srli rv32ui-v-sub \
	     rv32ui-v-sw rv32ui-v-xor rv32ui-v-xori rv32ui-v-beq # rv32mi-p-scall rv32ui-v-fence_i
PYTEST_EXTRA_ARGS = -n auto
RVTEST_VERILOG = $(addprefix meminit/, $(addsuffix .verilog, $(TEST_INSTS)))

# lbu lhu ram_test fence_i
FIRMWARE_RVTEST_OBJS = $(addprefix firmware/, $(addsuffix .o, \
		       addi add andi and auipc beq bge \
		       bgeu blt bltu bne jalr jal lb \
		       lh lui lw ori or sb sh \
		       slli sll slti sltiu slt sltu srai \
		       sra srli srl sub sw xori xor \
		       ))

# [test]
test: build $(RVTEST_VERILOG)
	pytest tb/test.py $(PYTEST_EXTRA_ARGS)

test-inst: build $(RVTEST_VERILOG)
	INSTS='$(TEST_INSTS)' pytest tb/test.py -k inst $(PYTEST_EXTRA_ARGS)

test-misc: build meminit/firmware.verilog
	pytest tb/test.py -k 'not inst' $(PYTEST_EXTRA_ARGS)

meminit:
	mkdir meminit

meminit/rv32%.verilog: $(RVTEST_ISA_PATH)/rv32% meminit
	$(TOOLCHAIN_PREFIX)objcopy $< -O verilog $@
	sed -i 's|@8|@0|g' $@
	cp $<.dump meminit

meminit/firmware.verilog: firmware/firmware meminit
	$(TOOLCHAIN_PREFIX)objcopy $< -O verilog meminit/firmware.verilog
	sed -i 's|@8|@0|g' $@
	$(TOOLCHAIN_PREFIX)objdump -D $< > meminit/firmware.dump

get-meminit: $(RVTEST_VERILOG) meminit/firmware.verilog

# [tools]
verilator:
	git submodule update --init --depth 1 tool/$@
	cd tool/verilator && \
		autoconf && \
		./configure && \
		make -j $$(nproc) && \
		sudo make install

tool/riscv-gnu-toolchain:
	# 不能用submodule，因为riscv-gnu-toolchain/.git必须是目录，不能是文件
	git clone --depth 1 https://github.com/riscv/riscv-gnu-toolchain $@
	cd tool/riscv-gnu-toolchain && \
		./configure --prefix=$(RISCV) --with-arch=rv32i --with-abi=ilp32 --disable-gdb && \
		make -j $$(nproc)

riscv-tests:
	git submodule update --init --recursive --depth 1 tool/$@
	cd tool/riscv-tests && \
		./configure --prefix=$(RISCV) && \
		sed -i 's|install: all|install: isa|' Makefile && \
		sed -i 's|rv32ui,-march=rv32g|rv32ui,-march=rv32if|' isa/Makefile && \
		sed -i 's|rv32si,-march=rv32g|rv32si,-march=rv32if|' isa/Makefile && \
		sed -i 's|rv32mi,-march=rv32g|rv32mi,-march=rv32if|' isa/Makefile && \
		sed -i 's/echo .* | md5/echo rv32ui-v-sw | md5/' isa/Makefile && \
		make install RISCV_PREFIX=$(TOOLCHAIN_PREFIX)

# [firmware]
firmware/firmware: firmware/start.o firmware/linker.ld $(FIRMWARE_RVTEST_OBJS)
	$(TOOLCHAIN_PREFIX)gcc -Os -ffreestanding -nostdlib -o $@ \
		-Wl,-Bstatic,-T,firmware/linker.ld,-Map,firmware/firmware.map,--strip-debug \
		firmware/start.o $(FIRMWARE_RVTEST_OBJS) -lgcc

firmware/firmware.bin: firmware/firmware
	$(TOOLCHAIN_PREFIX)objcopy $< -O binary $@
	[[ '0' == $$(echo "$$(du -h firmware/firmware.bin | cut -f1)>32K" | bc) ]] # too big

firmware/firmware.hex: firmware/firmware.bin firmware/makeihex.py
	python firmware/makeihex.py $< > $@

# 其实00到11都生成了
firmware/firmware00.hex: firmware/firmware.bin firmware/makeihex_8bit.py
	python firmware/makeihex_8bit.py $<

firmware/start.o: firmware/start.S
	$(TOOLCHAIN_PREFIX)gcc -c -march=rv32i -o $@ $<

firmware/ram_test.o: firmware/ram_test.py
	python $< | $(TOOLCHAIN_PREFIX)gcc -c -x assembler-with-cpp -march=rv32i -Ifirmware -DTEST_NAME=ram_test -o $@ -

firmware/%.o: tool/riscv-tests/isa/rv32ui/%.S firmware/riscv_test.h
	$(TOOLCHAIN_PREFIX)gcc -c -march=rv32i -Ifirmware -Itool/riscv-tests/isa/macros/scalar -DTEST_NAME=$(notdir $(basename $@)) -o $@ $<

# [verilog]
Naive.v: tool/api-config-chipsalliance/ $(shell find src -type f) build.sc
	git submodule update --init --recursive --depth 1 $<
	mill ousia.run

build: ousia.core Naive.v
	fusesoc --cores-root=. run ousia

# [board]
cyc10: firmware/firmware00.hex build
	fusesoc --cores-root=. run --target=cyc10 ousia

# [misc]
clean:
	-rm -r meminit firmware/firmware* __pycache__ build
	cocotb-clean
	mill clean

.bsp:
	mill mill.bsp.BSP/install

.PHONY: cyc10 clean test-inst test-misc test get-meminit
