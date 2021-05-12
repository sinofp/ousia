
module sdram (
	clk_clk,
	reset_reset_n,
	sdram_ctrl_s1_address,
	sdram_ctrl_s1_byteenable_n,
	sdram_ctrl_s1_chipselect,
	sdram_ctrl_s1_writedata,
	sdram_ctrl_s1_read_n,
	sdram_ctrl_s1_write_n,
	sdram_ctrl_s1_readdata,
	sdram_ctrl_s1_readdatavalid,
	sdram_ctrl_s1_waitrequest,
	sdram_ctrl_wire_addr,
	sdram_ctrl_wire_ba,
	sdram_ctrl_wire_cas_n,
	sdram_ctrl_wire_cke,
	sdram_ctrl_wire_cs_n,
	sdram_ctrl_wire_dq,
	sdram_ctrl_wire_dqm,
	sdram_ctrl_wire_ras_n,
	sdram_ctrl_wire_we_n);	

	input		clk_clk;
	input		reset_reset_n;
	input	[21:0]	sdram_ctrl_s1_address;
	input	[3:0]	sdram_ctrl_s1_byteenable_n;
	input		sdram_ctrl_s1_chipselect;
	input	[31:0]	sdram_ctrl_s1_writedata;
	input		sdram_ctrl_s1_read_n;
	input		sdram_ctrl_s1_write_n;
	output	[31:0]	sdram_ctrl_s1_readdata;
	output		sdram_ctrl_s1_readdatavalid;
	output		sdram_ctrl_s1_waitrequest;
	output	[11:0]	sdram_ctrl_wire_addr;
	output	[1:0]	sdram_ctrl_wire_ba;
	output		sdram_ctrl_wire_cas_n;
	output		sdram_ctrl_wire_cke;
	output		sdram_ctrl_wire_cs_n;
	inout	[31:0]	sdram_ctrl_wire_dq;
	output	[3:0]	sdram_ctrl_wire_dqm;
	output		sdram_ctrl_wire_ras_n;
	output		sdram_ctrl_wire_we_n;
endmodule
