module naive_soc (
    input clk,
    input reset,
    output [31:0] ram_addr,
    output [31:0] ram_wdata,
    output [3:0] ram_sel,
    output ram_we,
    output ram_cyc,
    output ram_stb,
    input [31:0] ram_rdata,
    input ram_ack
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

  wire dontcare_i;
  wire dontcare_o;
  wire [1:0] bte_o = 0;
  wire [2:0] cti_o;
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
      .wb_dbus_err_o(dontcare_o),
      .wb_dbus_rty_o(dontcare_o),
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
      .wb_ibus_err_o(dontcare_o),
      .wb_ibus_rty_o(dontcare_o),
      .wb_ram_adr_o(ram_addr),
      .wb_ram_dat_o(ram_wdata),
      .wb_ram_sel_o(ram_sel),
      .wb_ram_we_o(ram_we),
      .wb_ram_cyc_o(ram_cyc),
      .wb_ram_stb_o(ram_stb),
      .wb_ram_cti_o(cti_o),
      .wb_ram_bte_o(bte_o),
      .wb_ram_dat_i(ram_rdata),
      .wb_ram_ack_i(ram_ack),
      .wb_ram_err_i(dontcare_i),
      .wb_ram_rty_i(dontcare_i)
  );

endmodule
