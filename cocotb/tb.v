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

integer i;
always @(posedge clk) begin
    ack <= 1'b0;
    rdata <= 32'd42; // 101010，debug用，不知道有没有规定不ack时的rdata一定是全零
    if (cyc & stb & ~ack) begin
	ack <= 1'b1;
	for (i=0; i<4; i++) begin
	    rdata[8*i +: 8] <= mem[addr + i];
	    if (we & sel[i]) begin
		mem[addr + i] <= wdata[8*i +: 8];
	    end
	end
    end
end
endmodule

module tb (
    input clk,
    input reset
);

wire [31:0] inst_addr;
wire [31:0] inst_wdata;
wire [3 :0] inst_sel;
wire        inst_we;
wire        inst_cyc;
wire        inst_stb;
wire [31:0] inst_rdata;
wire        inst_ack;

wire [31:0] data_addr;
wire [31:0] data_wdata;
wire [3 :0] data_sel;
wire        data_we;
wire        data_cyc;
wire        data_stb;
wire [31:0] data_rdata;
wire        data_ack;

wb_ram inst_rom (
    .clk(clk),
    .addr(inst_addr),
    .wdata(inst_wdata),
    .sel(inst_sel),
    .we(inst_we),
    .cyc(inst_cyc),
    .stb(inst_stb),
    .rdata(inst_rdata),
    .ack(inst_ack)
);

wb_ram data_rom (
    .clk(clk),
    .addr(data_addr),
    .wdata(data_wdata),
    .sel(data_sel),
    .we(data_we),
    .cyc(data_cyc),
    .stb(data_stb),
    .rdata(data_rdata),
    .ack(data_ack)
);

initial begin
    $readmemh("/home/unv/projs/ousia/cocotb/sw.verilog", inst_rom.mem);
end

Naive cpu(
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
endmodule
