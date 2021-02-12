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

  wire [31:0] q;
  assign rdata = {q[7:0], q[15:8], q[23:16], q[31:24]};
  ram mem (
      .address(addr[31:2]),
      .byteena(sel),
      .clock(clk),
      .data(wdata),  // 换顺序？
      .rden(cyc & stb & ~we),
      .wren(cyc & stb & we),
      .q(q)
  );

  reg ack1;  // 拖一个周期
  always @(posedge clk) begin
    ack1 <= 1'b0;
    if (cyc & stb & ~ack & ~ack1) begin
      ack1 <= 1'b1;
    end
  end

  always @(posedge clk or posedge reset) begin
    if (reset) ack <= 1'b0;
    else ack <= ack1;
  end
endmodule
module quartus_top (
    output reg [7:0] led,
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
      .c0(clk),  // 5MHz
      .locked(pll_locked)
  );

  wire [31:0] inst_addr;
  wire [31:0] inst_wdata;
  wire [ 3:0] inst_sel;
  wire        inst_we;
  wire        inst_cyc;
  wire        inst_stb;
  wire [31:0] inst_rdata;
  wire        inst_ack;

  wire [31:0] data_addr;
  wire [31:0] data_wdata;
  wire [ 3:0] data_sel;
  wire        data_we;
  wire        data_cyc;
  wire        data_stb;
  wire [31:0] data_rdata;
  wire        data_ack;

  wire [31:0] ram_addr;
  wire [31:0] ram_wdata;
  wire [ 3:0] ram_sel;
  wire        ram_we;
  wire        ram_cyc;
  wire        ram_stb;
  wire [31:0] ram_rdata;
  wire        ram_ack;

  wb_ram ram (
      .clk(clk),
      .reset(~pll_locked),
      .addr(ram_addr),
      .wdata(ram_wdata),
      .sel(ram_sel),
      .we(ram_we),
      .cyc(ram_cyc),
      .stb(ram_stb),
      .rdata(ram_rdata),
      .ack(ram_ack)
  );

  naive_soc soc (
      .clk(clk),
      .reset(~pll_locked),
      .ram_addr(ram_addr),
      .ram_wdata(ram_wdata),
      .ram_sel(ram_sel),
      .ram_we(ram_we),
      .ram_cyc(ram_cyc),
      .ram_stb(ram_stb),
      .ram_rdata(ram_rdata),
      .ram_ack(ram_ack),
      .uart_rx(uart_rx),
      .uart_tx(uart_tx)
  );

  // led test
  wire clk_1h;
  clk_div #(
      .WIDTH(32),
      .N(5 * 1024 * 1024)
  ) clk_div (
      .clk(clk),
      .rst_n(pll_locked),
      .clkout(clk_1h)
  );

  always @(posedge clk_1h or negedge pll_locked) begin
    if (!pll_locked) led <= 8'b11111110;
    else led <= {led[0], led[7:1]};
  end

endmodule
