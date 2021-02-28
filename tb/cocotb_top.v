module wb_ram (
    input             clk,
    input      [31:0] addr,
    input      [31:0] wdata,
    input      [ 3:0] sel,
    input             we,
    input             cyc,
    input             stb,
    output reg [31:0] rdata,
    output reg        ack
);

  reg [7:0] mem[32767:0];  // 32KB

  integer i;
  always @(posedge clk) begin
    ack   <= 1'b0;
    rdata <= 32'd42;  // 101010，debug用，不知道有没有规定不ack时的rdata一定是全零
    if (cyc & stb & ~ack) begin
      ack <= 1'b1;
      for (i = 0; i < 4; i++) begin
        rdata[8*i+:8] <= mem[addr+i];
        if (we & sel[i]) begin
          mem[addr+i] <= wdata[8*i+:8];
        end
      end
    end
  end
endmodule

module cocotb_top (
    input clk,
    input reset,
    input uart_rx,
    output uart_tx,
    output [7:0] gpio_o
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
      .addr(ram_addr),
      .wdata(ram_wdata),
      .sel(ram_sel),
      .we(ram_we),
      .cyc(ram_cyc),
      .stb(ram_stb),
      .rdata(ram_rdata),
      .ack(ram_ack)
  );

  initial begin
    $readmemh("", ram.mem);
  end

  naive_soc soc (
      .clk(clk),
      .reset(reset),
      .ram_addr(ram_addr),
      .ram_wdata(ram_wdata),
      .ram_sel(ram_sel),
      .ram_we(ram_we),
      .ram_cyc(ram_cyc),
      .ram_stb(ram_stb),
      .ram_rdata(ram_rdata),
      .ram_ack(ram_ack),
      .uart_rx(uart_rx),
      .uart_tx(uart_tx),
      .gpio_o(gpio_o)
  );

endmodule
