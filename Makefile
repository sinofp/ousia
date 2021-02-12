TOOLCHAIN_PREFIX = riscv-sifive-elf-

verilator:
	cd verilator && \
	autoconf && \
	./configure && \
	make -j $$(nproc) && \
	sudo make install

firmware/firmware: firmware/start.S
	$(TOOLCHAIN_PREFIX)gcc -static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles -march=rv32i -T firmware/linker.ld -o $@ $<

firmware/firmware.bin: firmware/firmware
	$(TOOLCHAIN_PREFIX)objcopy $< -O binary $@

firmware/firmware.hex: firmware/firmware.bin
	srec_cat $< -Binary -Output $@ -Intel -Output_Block_Size=4 # 32 bit a row

board: firmware/firmware.hex *.v ousia.core *.qip # todo
	fusesoc --cores-root=. run --target=quartus ousia

.PHONY: verilator board
