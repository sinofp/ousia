TOOLCHAIN_PREFIX = riscv-sifive-elf-

verilator:
	cd verilator && \
	autoconf && \
	./configure && \
	make -j $$(nproc) && \
	sudo make install

firmware/firmware: firmware/start.S
	$(TOOLCHAIN_PREFIX)gcc -static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles -march=rv32i -T firmware/linker.ld -o $@ $<

.PHONY: verilator
