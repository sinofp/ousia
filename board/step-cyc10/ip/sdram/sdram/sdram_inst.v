	sdram u0 (
		.clk_clk                     (<connected-to-clk_clk>),                     //             clk.clk
		.reset_reset_n               (<connected-to-reset_reset_n>),               //           reset.reset_n
		.sdram_ctrl_s1_address       (<connected-to-sdram_ctrl_s1_address>),       //   sdram_ctrl_s1.address
		.sdram_ctrl_s1_byteenable_n  (<connected-to-sdram_ctrl_s1_byteenable_n>),  //                .byteenable_n
		.sdram_ctrl_s1_chipselect    (<connected-to-sdram_ctrl_s1_chipselect>),    //                .chipselect
		.sdram_ctrl_s1_writedata     (<connected-to-sdram_ctrl_s1_writedata>),     //                .writedata
		.sdram_ctrl_s1_read_n        (<connected-to-sdram_ctrl_s1_read_n>),        //                .read_n
		.sdram_ctrl_s1_write_n       (<connected-to-sdram_ctrl_s1_write_n>),       //                .write_n
		.sdram_ctrl_s1_readdata      (<connected-to-sdram_ctrl_s1_readdata>),      //                .readdata
		.sdram_ctrl_s1_readdatavalid (<connected-to-sdram_ctrl_s1_readdatavalid>), //                .readdatavalid
		.sdram_ctrl_s1_waitrequest   (<connected-to-sdram_ctrl_s1_waitrequest>),   //                .waitrequest
		.sdram_ctrl_wire_addr        (<connected-to-sdram_ctrl_wire_addr>),        // sdram_ctrl_wire.addr
		.sdram_ctrl_wire_ba          (<connected-to-sdram_ctrl_wire_ba>),          //                .ba
		.sdram_ctrl_wire_cas_n       (<connected-to-sdram_ctrl_wire_cas_n>),       //                .cas_n
		.sdram_ctrl_wire_cke         (<connected-to-sdram_ctrl_wire_cke>),         //                .cke
		.sdram_ctrl_wire_cs_n        (<connected-to-sdram_ctrl_wire_cs_n>),        //                .cs_n
		.sdram_ctrl_wire_dq          (<connected-to-sdram_ctrl_wire_dq>),          //                .dq
		.sdram_ctrl_wire_dqm         (<connected-to-sdram_ctrl_wire_dqm>),         //                .dqm
		.sdram_ctrl_wire_ras_n       (<connected-to-sdram_ctrl_wire_ras_n>),       //                .ras_n
		.sdram_ctrl_wire_we_n        (<connected-to-sdram_ctrl_wire_we_n>)         //                .we_n
	);

