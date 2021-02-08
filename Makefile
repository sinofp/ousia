verilator:
	cd verilator && \
	autoconf && \
	./configure && \
	make -j $$(nproc) && \
	sudo make install

.PHONY: verilator
