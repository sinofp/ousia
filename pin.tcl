package require ::quartus::project

set_location_assignment PIN_M15 -to clk_50m
set_location_assignment PIN_D15 -to key_c_n
set_location_assignment PIN_T2 -to uart_tx
set_location_assignment PIN_R1 -to uart_rx

set_location_assignment PIN_M6 -to led[0]
set_location_assignment PIN_J1 -to led[1]
set_location_assignment PIN_J2 -to led[2]
set_location_assignment PIN_G1 -to led[3]
set_location_assignment PIN_G2 -to led[4]
set_location_assignment PIN_D1 -to led[5]
set_location_assignment PIN_C2 -to led[6]
set_location_assignment PIN_B1 -to led[7]

