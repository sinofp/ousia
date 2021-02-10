module clk_div #(
    parameter    WIDTH    = 3, // clog?
    parameter    N    = 5
) (
    input  clk,
    input  rst_n,
    output clkout
);
  reg [WIDTH-1:0] cnt_p;
  reg             clk_p;

  always @(posedge clk or negedge rst_n) begin
    if (!rst_n) cnt_p <= 0;
    else if (cnt_p == (N - 1)) cnt_p <= 0;
    else cnt_p <= cnt_p + 1;
  end

  always @(posedge clk or negedge rst_n) begin
    if (!rst_n) clk_p <= 0;
    else if (cnt_p < (N >> 1)) clk_p <= 0;
    else clk_p <= 1;
  end

  assign clkout = (N == 1) ? clk : clk_p;
endmodule

