module wb_ram (
    input             clk,
    input             reset,
    input      [31:0] addr,
    input      [31:0] wdata,
    input      [ 3:0] sel,
    input             we,
    input             cyc,
    input             stb,
    output     [31:0] rdata,
    output reg        ack
);

  wire [29:0] address = addr[31:2];
  wire req = cyc & stb & ~ack;
  wire rden = req & ~we;
  wire wren = req & we;
  ram #(
      .init_file("../../../firmware/firmware00.hex")
  ) ram00 (
      .address(address),
      .clock(clk),
      .data(wdata[7:0]),
      .rden(rden & sel[0]),
      .wren(wren & sel[0]),
      .q(rdata[7:0])
  );

  ram #(
      .init_file("../../../firmware/firmware01.hex")
  ) ram01 (
      .address(address),
      .clock(clk),
      .data(wdata[15:8]),
      .rden(rden & sel[1]),
      .wren(wren & sel[1]),
      .q(rdata[15:8])
  );

  ram #(
      .init_file("../../../firmware/firmware10.hex")
  ) ram10 (
      .address(address),
      .clock(clk),
      .data(wdata[23:16]),
      .rden(rden & sel[2]),
      .wren(wren & sel[2]),
      .q(rdata[23:16])
  );

  ram #(
      .init_file("../../../firmware/firmware11.hex")
  ) ram11 (
      .address(address),
      .clock(clk),
      .data(wdata[31:24]),
      .rden(rden & sel[3]),
      .wren(wren & sel[3]),
      .q(rdata[31:24])
  );

  reg ack1;  // 拖一个周期
  always @(posedge clk) begin
    ack1 <= 1'b0;
    if (req & ~ack1) begin
      ack1 <= 1'b1;
    end
  end

  always @(posedge clk or posedge reset) begin
    if (reset) ack <= 1'b0;
    else ack <= ack1;
  end
endmodule
module synchronizer (
    input clk,
    input asyncrst_n,
    output reg rst_n
);

  reg rff1;
  always @(posedge clk or negedge asyncrst_n)
    if (!asyncrst_n) {rst_n, rff1} <= 2'b0;
    else {rst_n, rff1} <= {rff1, 1'b1};
endmodule
module cyc10_top (
    output [11:0] sdr_addr,
    output [1:0] sdr_ba,
    output sdr_cas_n,
    output sdr_cke,
    output sdr_cs_n,
    output [15:0] sdr_dq,
    output sdr_clk,
    output sdr_ras_n,
    output sdr_we_n,
    output [2:0] sdr_dqm,
    output reg rgb_led1_r,
    output reg rgb_led1_g,
    output reg rgb_led1_b,
    output reg rgb_led2_r,
    output reg rgb_led2_g,
    output reg rgb_led2_b,
    output [7:0] led,
    input clk_50m,
    input key_c_n,
    input uart_rx,
    output uart_tx
);

  wire clk;
  wire pll_locked;

  pll pll (
      .areset(~key_c_n),
      .inclk0(clk_50m),
      .c0(clk_20m),
      .c1(sdram_clk),
      .c2(sdr_clk),
      .locked(pll_locked)
  );


  wire rst_n;
  synchronizer synchronizer (
      .clk(clk_20m),
      .asyncrst_n(pll_locked),
      .rst_n(rst_n)
  );

  // ram
  wire [31:0] ram_addr;
  wire [31:0] ram_wdata;
  wire [ 3:0] ram_sel;
  wire        ram_we;
  wire        ram_cyc;
  wire        ram_stb;
  wire [31:0] ram_rdata;
  wire        ram_ack;

  wb_ram ram (
      .clk(clk_20m),
      .reset(~rst_n),
      .addr(ram_addr),
      .wdata(ram_wdata),
      .sel(ram_sel),
      .we(ram_we),
      .cyc(ram_cyc),
      .stb(ram_stb),
      .rdata(ram_rdata),
      .ack(ram_ack)
  );

  // sdram
  wire [31:0] sdram_addr;
  wire [31:0] sdram_wdata;
  wire [ 3:0] sdram_sel;
  wire        sdram_we;
  wire        sdram_cyc;
  wire        sdram_stb;
  wire [31:0] sdram_rdata;
  wire        sdram_ack;
  wire [21:0] sdram_ctrl_s1_address;
  wire        sdram_ctrl_s1_write;
  wire        sdram_ctrl_s1_read;
  wire [ 1:0] sdram_ctrl_s1_byteenable;
  wire [15:0] sdram_ctrl_s1_writedata;
  wire [15:0] sdram_ctrl_s1_readdata;
  wire        sdram_ctrl_s1_waitrequest;
  wire        sdram_ctrl_s1_readdatavalid;
  // wire [31:0] half_rate_bridge_s1_address;
  // wire        half_rate_bridge_s1_write;
  // wire        half_rate_bridge_s1_read;
  // wire [ 3:0] half_rate_bridge_s1_byteenable;
  // wire [31:0] half_rate_bridge_s1_writedata;
  // wire [31:0] half_rate_bridge_s1_readdata;
  // wire        half_rate_bridge_s1_waitrequest;
  // wire        half_rate_bridge_s1_readdatavalid;
  wb32_avalon16 bridge (
      .sdram_clk(sdram_clk),
      .clk(clk_20m),
      .reset_n(rst_n),

      // Wishbone Slave Input
      .wishbone_addr_i(sdram_addr),
      .wishbone_data_i(sdram_wdata),
      .wishbone_sel_i (sdram_sel),
      .wishbone_we_i  (sdram_we),
      .wishbone_cyc_i (sdram_cyc),
      .wishbone_stb_i (sdram_stb),
      .wishbone_data_o(sdram_rdata),  //ok
      .wishbone_ack_o (sdram_ack),

      // Avalon Master Output
      .avalon_sdram_address_o(sdram_ctrl_s1_address),
      .avalon_sdram_byteenable_n_o(sdram_ctrl_s1_byteenable_n),
      .avalon_sdram_read_n_o(sdram_ctrl_s1_read),
      .avalon_sdram_readdata_i(sdram_ctrl_s1_readdata),
      .avalon_sdram_chipselect_o(sdram_ctrl_s1_chipselect),  //ok
      .avalon_sdram_write_n_o(sdram_ctrl_s1_write),
      .avalon_sdram_writedata_o(sdram_ctrl_s1_writedata),
      .avalon_sdram_waitrequest_i(sdram_ctrl_s1_waitrequest),
      .avalon_sdram_readdatavalid_i(sdram_ctrl_s1_readdatavalid)
  );
  // wb_to_avalon_bridge wb2avalon (
  //   .wb_clk_i(clk_20m),
  //   .wb_rst_i(~rst_n),
  //   .wb_adr_i(sdram_addr),
  //   .wb_dat_i(sdram_wdata),
  //   .wb_sel_i(sdram_sel),
  //   .wb_we_i(sdram_we),
  //   .wb_cyc_i(sdram_cyc),
  //   .wb_stb_i(sdram_stb),
  //   .wb_cti_i(3'b0),
  //   .wb_bte_i(2'b0),
  //   .wb_dat_o(sdram_rdata),
  //   .wb_ack_o(sdram_ack),
  //   .wb_err_o(),
  //   .wb_rty_o(),
  //   .m_av_address_o(half_rate_bridge_s1_address),
  //   .m_av_byteenable_o(half_rate_bridge_s1_byteenable),
  //   .m_av_read_o(half_rate_bridge_s1_read),
  //   .m_av_readdata_i(half_rate_bridge_s1_readdata),
  //   .m_av_burstcount_o(),
  //   .m_av_write_o(half_rate_bridge_s1_write),
  //   .m_av_writedata_o(half_rate_bridge_s1_writedata),
  //   .m_av_waitrequest_i(half_rate_bridge_s1_waitrequest),
  //   .m_av_readdatavalid_i(half_rate_bridge_s1_readdatavalid)
  // );
  sdram sdram (
      .clk_clk(sdram_clk),  // todo
      .reset_reset_n(rst_n),
      .sdram_ctrl_wire_addr(sdr_addr),
      .sdram_ctrl_wire_ba(sdr_ba),
      .sdram_ctrl_wire_cas_n(sdr_cas_n),
      .sdram_ctrl_wire_cke(sdr_cke),
      .sdram_ctrl_wire_cs_n(sdr_cs_n),
      .sdram_ctrl_wire_dq(sdr_dq),
      .sdram_ctrl_wire_dqm(sdr_dqm),
      .sdram_ctrl_wire_ras_n(sdr_ras_n),
      .sdram_ctrl_wire_we_n(sdr_we_n),
      // .half_rate_bridge_s1_address(half_rate_bridge_s1_address),
      // .half_rate_bridge_s1_byteenable(half_rate_bridge_s1_byteenable),
      // .half_rate_bridge_s1_chipselect(half_rate_bridge_s1_chipselect),
      // .half_rate_bridge_s1_writedata(half_rate_bridge_s1_writedata),
      // .half_rate_bridge_s1_read(half_rate_bridge_s1_read),
      // .half_rate_bridge_s1_write(half_rate_bridge_s1_write),
      // .half_rate_bridge_s1_readdata(half_rate_bridge_s1_readdata),
      // .half_rate_bridge_s1_readdatavalid(half_rate_bridge_s1_readdatavalid),
      // .half_rate_bridge_s1_waitrequest(half_rate_bridge_s1_waitrequest)
      .sdram_ctrl_s1_address(sdram_ctrl_s1_address),
      .sdram_ctrl_s1_byteenable_n(sdram_ctrl_s1_byteenable_n),
      .sdram_ctrl_s1_chipselect(sdram_ctrl_s1_chipselect),
      .sdram_ctrl_s1_writedata(sdram_ctrl_s1_writedata),
      .sdram_ctrl_s1_read_n(sdram_ctrl_s1_read_n),
      .sdram_ctrl_s1_write_n(sdram_ctrl_s1_write_n),
      .sdram_ctrl_s1_readdata(sdram_ctrl_s1_readdata),
      .sdram_ctrl_s1_readdatavalid(sdram_ctrl_s1_readdatavalid),
      .sdram_ctrl_s1_waitrequest(sdram_ctrl_s1_waitrequest)
  );

  wire [7:0] led_n;
  assign led = ~led_n;
  naive_soc soc (
      .clk(clk_20m),
      .reset(~rst_n),
      .ram_addr(ram_addr),
      .ram_wdata(ram_wdata),
      .ram_sel(ram_sel),
      .ram_we(ram_we),
      .ram_cyc(ram_cyc),
      .ram_stb(ram_stb),
      .ram_rdata(ram_rdata),
      .ram_ack(ram_ack),
      .sdram_addr(sdram_addr),
      .sdram_wdata(sdram_wdata),
      .sdram_sel(sdram_sel),
      .sdram_we(sdram_we),
      .sdram_cyc(sdram_cyc),
      .sdram_stb(sdram_stb),
      .sdram_rdata(sdram_rdata),
      .sdram_ack(sdram_ack),
      .uart_rx(uart_rx),
      .uart_tx(uart_tx),
      .gpio_o(led_n)
  );

  // reset test
  always @(posedge clk_20m) begin
    if (!rst_n)
      {rgb_led1_r, rgb_led1_g, rgb_led1_b, rgb_led2_r, rgb_led2_g, rgb_led2_b} <= 6'b000000;  // 时间太快，放开按键时微微一闪
    else {rgb_led1_r, rgb_led1_g, rgb_led1_b, rgb_led2_r, rgb_led2_g, rgb_led2_b} <= 6'b111111;
  end

endmodule
