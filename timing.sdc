create_clock -name clk_50m -period 20.000ns [get_ports {clk_50m}]
derive_pll_clocks
derive_clock_uncertainty

