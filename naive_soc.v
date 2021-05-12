module naive_soc (
    input clk,
    input reset,
    // ram
    output [31:0] ram_addr,
    output [31:0] ram_wdata,
    output [3:0] ram_sel,
    output ram_we,
    output ram_cyc,
    output ram_stb,
    input [31:0] ram_rdata,
    input ram_ack,
    // sdram
    output [31:0] sdram_addr,
    output [31:0] sdram_wdata,
    output [3:0] sdram_sel,
    output sdram_we,
    output sdram_cyc,
    output sdram_stb,
    input [31:0] sdram_rdata,
    input sdram_ack,
    // uart
    input uart_rx,
    output uart_tx,
    // gpio(led)
    output [7:0] gpio_o
);

  wire [31:0] inst_addr;
  wire [31:0] inst_rdata;
  wire [31:0] inst_wdata;
  wire [3:0] inst_sel;
  wire inst_we;
  wire inst_cyc;
  wire inst_stb;
  wire inst_ack;

  wire [31:0] data_addr;
  wire [31:0] data_rdata;
  wire [31:0] data_wdata;
  wire [3:0] data_sel;
  wire data_we;
  wire data_cyc;
  wire data_stb;
  wire data_ack;

  Naive cpu (
      .clock(clk),
      .reset(reset),
      .io_iwb_addr(inst_addr),
      .io_iwb_wdata(inst_wdata),
      .io_iwb_sel(inst_sel),
      .io_iwb_we(inst_we),
      .io_iwb_cyc(inst_cyc),
      .io_iwb_stb(inst_stb),
      .io_iwb_rdata(inst_rdata),
      .io_iwb_ack(inst_ack),
      .io_dwb_addr(data_addr),
      .io_dwb_wdata(data_wdata),
      .io_dwb_sel(data_sel),
      .io_dwb_we(data_we),
      .io_dwb_cyc(data_cyc),
      .io_dwb_stb(data_stb),
      .io_dwb_rdata(data_rdata),
      .io_dwb_ack(data_ack)
  );

  wire [31:0] uart_addr;
  wire [31:0] uart_rdata;
  wire [31:0] uart_wdata;
  wire uart_we;
  wire uart_cyc;
  wire uart_stb;
  wire uart_ack;

  assign uart_rdata[31:8] = 24'b0;

  // fusesoc's uart only support 8-bits
  uart_top uart (
      .wb_clk_i (clk),
      .wb_rst_i (reset),
      .wb_adr_i (uart_addr[4:2]),
      .wb_dat_i (uart_wdata[7:0]),
      .wb_we_i  (uart_we),
      .wb_cyc_i (uart_cyc),
      .wb_stb_i (uart_stb),
      .wb_sel_i (),
      .wb_dat_o (uart_rdata[7:0]),
      .wb_ack_o (uart_ack),
      .int_o    (),
      .stx_pad_o(uart_tx),
      .rts_pad_o(),
      .dtr_pad_o(),
      .srx_pad_i(uart_rx),
      .cts_pad_i(1'b0),
      .dsr_pad_i(1'b0),
      .ri_pad_i (1'b0),
      .dcd_pad_i(1'b0)
  );

  wire [31:0] gpio_addr;
  wire [31:0] gpio_wdata;
  wire gpio_we;
  wire gpio_cyc;
  wire gpio_stb;
  wire gpio_ack;

  gpio led (
      .wb_clk(clk),
      .wb_rst(reset),
      .wb_adr_i(gpio_addr[2]),
      .wb_dat_i(gpio_wdata[7:0]),
      .wb_we_i(gpio_we),
      .wb_cyc_i(gpio_cyc),
      .wb_stb_i(gpio_stb),
      .wb_cti_i(),
      .wb_bte_i(),
      .wb_dat_o(),
      .wb_ack_o(gpio_ack),
      .wb_err_o(),
      .wb_rty_o(),
      .gpio_i(),
      .gpio_o(gpio_o),
      .gpio_dir_o()
  );

  wire [2:0] cti = 0;
  wire [1:0] bte = 0;

  wb_intercon intercon (
      .wb_clk_i(clk),
      .wb_rst_i(reset),
      .wb_dbus_adr_i(data_addr),
      .wb_dbus_dat_i(data_wdata),
      .wb_dbus_sel_i(data_sel),
      .wb_dbus_we_i(data_we),
      .wb_dbus_cyc_i(data_cyc),
      .wb_dbus_stb_i(data_stb),
      .wb_dbus_cti_i(cti),
      .wb_dbus_bte_i(bte),
      .wb_dbus_dat_o(data_rdata),
      .wb_dbus_ack_o(data_ack),
      .wb_dbus_err_o(),
      .wb_dbus_rty_o(),
      .wb_ibus_adr_i(inst_addr),
      .wb_ibus_dat_i(inst_rdata),
      .wb_ibus_sel_i(inst_sel),
      .wb_ibus_we_i(inst_we),
      .wb_ibus_cyc_i(inst_cyc),
      .wb_ibus_stb_i(inst_stb),
      .wb_ibus_cti_i(cti),
      .wb_ibus_bte_i(bte),
      .wb_ibus_dat_o(inst_rdata),
      .wb_ibus_ack_o(inst_ack),
      .wb_ibus_err_o(),
      .wb_ibus_rty_o(),
      .wb_ram_adr_o(ram_addr),
      .wb_ram_dat_o(ram_wdata),
      .wb_ram_sel_o(ram_sel),
      .wb_ram_we_o(ram_we),
      .wb_ram_cyc_o(ram_cyc),
      .wb_ram_stb_o(ram_stb),
      .wb_ram_cti_o(),
      .wb_ram_bte_o(),
      .wb_ram_dat_i(ram_rdata),
      .wb_ram_ack_i(ram_ack),
      .wb_ram_err_i(),
      .wb_ram_rty_i(),
      .wb_sdram_adr_o(sdram_addr),
      .wb_sdram_dat_o(sdram_wdata),
      .wb_sdram_sel_o(sdram_sel),
      .wb_sdram_we_o(sdram_we),
      .wb_sdram_cyc_o(sdram_cyc),
      .wb_sdram_stb_o(sdram_stb),
      .wb_sdram_cti_o(),
      .wb_sdram_bte_o(),
      .wb_sdram_dat_i(sdram_rdata),
      .wb_sdram_ack_i(sdram_ack),
      .wb_sdram_err_i(),
      .wb_sdram_rty_i(),
      .wb_uart_adr_o(uart_addr),
      .wb_uart_dat_o(uart_wdata),
      .wb_uart_sel_o(),
      .wb_uart_we_o(uart_we),
      .wb_uart_cyc_o(uart_cyc),
      .wb_uart_stb_o(uart_stb),
      .wb_uart_cti_o(),
      .wb_uart_bte_o(),
      .wb_uart_dat_i(uart_rdata),
      .wb_uart_ack_i(uart_ack),
      .wb_uart_err_i(),
      .wb_uart_rty_i(),
      .wb_gpio_adr_o(gpio_addr),
      .wb_gpio_dat_o(gpio_wdata),
      .wb_gpio_sel_o(),
      .wb_gpio_we_o(gpio_we),
      .wb_gpio_cyc_o(gpio_cyc),
      .wb_gpio_stb_o(gpio_stb),
      .wb_gpio_cti_o(),
      .wb_gpio_bte_o(),
      .wb_gpio_dat_i(),
      .wb_gpio_ack_i(gpio_ack),
      .wb_gpio_err_i(),
      .wb_gpio_rty_i()
  );

endmodule
