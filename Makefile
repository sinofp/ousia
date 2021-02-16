TOOLCHAIN_PREFIX = tool/riscv/bin/riscv32-unknown-elf-

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

firmware/firmware: firmware/start.S firmware/uart.S firmware/led.S
	$(TOOLCHAIN_PREFIX)gcc -static -mcmodel=medany -fvisibility=hidden -nostdlib -nostartfiles -march=rv32i -T firmware/linker.ld -o $@ $<

firmware/firmware.bin: firmware/firmware
	$(TOOLCHAIN_PREFIX)objcopy $< -O binary $@

firmware/firmware.hex: firmware/firmware.bin
	srec_cat $< -Binary -Output $@ -Intel -Output_Block_Size=4 # 32 bit a row

cyc10: firmware/firmware.hex ousia.core board/step-cyc10 # 其实不用写，但写了清楚
	fusesoc --cores-root=. run --target=cyc10 ousia

.PHONY: verilator gcc cyc10
