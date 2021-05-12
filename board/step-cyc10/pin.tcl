set_global_assignment -name NUM_PARALLEL_PROCESSORS ALL

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

set_location_assignment PIN_L3 -to rgb_led1_r
set_location_assignment PIN_K5 -to rgb_led1_g
set_location_assignment PIN_L4 -to rgb_led1_b
set_location_assignment PIN_F3 -to rgb_led2_r
set_location_assignment PIN_D3 -to rgb_led2_g
set_location_assignment PIN_C3 -to rgb_led2_b

set_location_assignment PIN_D8 -to sdr_addr[0]
set_location_assignment PIN_E6 -to sdr_addr[1]
set_location_assignment PIN_D6 -to sdr_addr[2]
set_location_assignment PIN_D9 -to sdr_addr[3]
set_location_assignment PIN_E9 -to sdr_addr[4]
set_location_assignment PIN_D11 -to sdr_addr[5]
set_location_assignment PIN_F9 -to sdr_addr[6]
set_location_assignment PIN_E11 -to sdr_addr[7]
set_location_assignment PIN_C14 -to sdr_addr[8]
set_location_assignment PIN_E10 -to sdr_addr[9]
set_location_assignment PIN_C6 -to sdr_addr[10]
set_location_assignment PIN_C11 -to sdr_addr[11]
set_location_assignment PIN_E8 -to sdr_ba[0]
set_location_assignment PIN_E7 -to sdr_ba[1]
set_location_assignment PIN_C9 -to sdr_cas_n
set_location_assignment PIN_A10 -to sdr_cke
set_location_assignment PIN_A2 -to sdr_cs_n
set_location_assignment PIN_A6 -to sdr_dq[0]
set_location_assignment PIN_A5 -to sdr_dq[1]
set_location_assignment PIN_B7 -to sdr_dq[2]
set_location_assignment PIN_B5 -to sdr_dq[3]
set_location_assignment PIN_B6 -to sdr_dq[4]
set_location_assignment PIN_A4 -to sdr_dq[5]
set_location_assignment PIN_A7 -to sdr_dq[6]
set_location_assignment PIN_B4 -to sdr_dq[7]
set_location_assignment PIN_A15 -to sdr_dq[8]
set_location_assignment PIN_B11 -to sdr_dq[9]
set_location_assignment PIN_B14 -to sdr_dq[10]
set_location_assignment PIN_A13 -to sdr_dq[11]
set_location_assignment PIN_A14 -to sdr_dq[12]
set_location_assignment PIN_A12 -to sdr_dq[13]
set_location_assignment PIN_B13 -to sdr_dq[14]
set_location_assignment PIN_B12 -to sdr_dq[15]
set_location_assignment PIN_B10 -to sdr_clk
set_location_assignment PIN_A3 -to sdr_ras_n
set_location_assignment PIN_B3 -to sdr_we_n
set_location_assignment PIN_C8 -to sdr_dqm[0]
set_location_assignment PIN_A11 -to sdr_dqm[1]
