`timescale 1ns / 1ps

module wb_ram(
    input             clk,
    input      [31:0] addr,
    input      [31:0] wdata,
    input      [3 :0] sel,
    input             we,
    input             cyc,
    input             stb,
    output reg [31:0] rdata,
    output reg        ack
);

reg  [7 :0] mem[16383:0]; // 16KB
wire [31:0] addr00 = {addr[31:2], 2'b00};

initial begin
    $readmemh("/home/unv/projs/ousia/cocotb/sw.verilog", mem);
end

integer i;
always @(posedge clk) begin
    ack <= 1'b0;
    rdata <= 32'd42; // 101010，debug用，不知道有没有规定不ack时的rdata一定是全零
    if (cyc & stb & ~ack) begin
	ack <= 1'b1;
	rdata <= {mem[addr00 + 3], mem[addr00 + 2], mem[addr00 + 1], mem[addr]};
	for (i=0; i<4; i++) begin
	    if (we & sel[i]) begin
		mem[addr00 + i] <= wdata[8*i +: 8];
	    end
	end
    end
end
endmodule

module tb (
    input clk,
    input reset
);

wire [31:0] addr;
wire [31:0] wdata;
wire [3 :0] sel;
wire        we;
wire        cyc;
wire        stb;
wire [31:0] rdata;
wire        ack;

wb_ram rom (
    .clk(clk),
    .addr(addr),
    .wdata(wdata),
    .sel(sel),
    .we(we),
    .cyc(cyc),
    .stb(stb),
    .rdata(rdata),
    .ack(ack)
);

Naive cpu(
    .clock(clk),
    .reset(reset),
    .io_iwb_addr(addr),
    .io_iwb_wdata(wdata),
    .io_iwb_sel(sel),
    .io_iwb_we(we),
    .io_iwb_cyc(cyc),
    .io_iwb_stb(stb),
    .io_iwb_rdata(rdata),
    .io_iwb_ack(ack)
);
endmodule
