import Consts.{RF_WDATA_ALU, SZ_SEL_RF_WDATA}
import chisel3._
import chisel3.util.{Fill, MuxCase, MuxLookup}
import chisel3.util.experimental.InlineInstance

class InOrder extends CoreModule {
  val memReq = IO(Output(new MemReq(4)))
  val memRes = IO(Input(new MemRes(4)))

  val iCache = Module(new CacheDM(512, 4))
//  val dCache = Module(new Cache(512))
  memReq := iCache.memReq
  iCache.memRes := memRes

  val fe_pc = Reg(UInt())
  val fe_ir = RegInit(0x13.U(32.W))

  val ew_pc = Reg(UInt())
  val ew_ir = RegInit(0x13.U(32.W))
  val ew_rf_we = RegInit(false.B)
  val ew_sel_rf_wdata = Reg(UInt(SZ_SEL_RF_WDATA.W))
  val ew_alu_out = Reg(UInt(xLen.W))

  // Fetch
  val stall = !iCache.cpuRes.ready // || ??? // ew_{load,store} = 1 && d$_cnt.value != 1 (每次新的指令流入wb时，reset d$_cnt)
  val pc    = RegInit(UInt(xLen.W), DRAM_BASE.into)
  val npc   = pc + 4.U
  iCache.cpuReq.addr  := pc
  iCache.cpuReq.valid := true.B
  iCache.cpuReq.we    := false.B
  iCache.cpuReq.data  := DontCare
  when(!stall) {
    pc    := npc
    fe_pc := pc
    fe_ir := iCache.cpuRes.data
  }

  // EX: Decode & Execute
  val decode  = Module(new Decode with InlineInstance)
  decode.inst := fe_ir

  val rd_addr  = fe_ir(11, 7)
  val rs1_addr = fe_ir(19, 15)
  val rs2_addr = fe_ir(24, 20)
  val rf = Module(new RegFile(2) with InlineInstance)
  rf.io.raddr(0) := rs1_addr
  rf.io.raddr(1) := rs2_addr

  // bypass
  val ew_rd_addr = ew_ir(11, 7)
  val rs1hazard = ew_rf_we && rs1_addr.orR && (rs1_addr === ew_rd_addr)
  val rs2hazard = ew_rf_we && rs2_addr.orR && (rs2_addr === ew_rd_addr)
  val rs1 = Mux(ew_sel_rf_wdata === RF_WDATA_ALU && rs1hazard, ew_alu_out, rf.io.rdata(0))
  val rs2 = Mux(ew_sel_rf_wdata === RF_WDATA_ALU && rs2hazard, ew_alu_out, rf.io.rdata(1))

  def ZXT(x: UInt, len: Int = 32) = 0.U((len - x.getWidth).W) ## x
  def SXT(x: UInt, len: Int = 32) = Fill(len - x.getWidth, x(x.getWidth - 1)) ## x
  import Consts._
  val imm  = MuxLookup(decode.sigs.sel_imm, 0.U, Seq(
    IMM_S  -> SXT(fe_ir(31, 25) ## fe_ir(11, 7)),
    IMM_SB -> SXT(fe_ir(31) ## fe_ir(7) ## fe_ir(30, 25) ## fe_ir(11, 8) ## 0.U),
    IMM_U  -> fe_ir(31, 12) ## 0.U(12.W),
    IMM_UJ -> SXT(fe_ir(31) ## fe_ir(19, 12) ## fe_ir(20) ## fe_ir(30, 21) ## 0.U),
    IMM_I  -> SXT(fe_ir(31, 20)),
    IMM_Z  -> ZXT(rs1_addr),
  ))

  val alu = Module(new ALU with InlineInstance)
  alu.io.in1 := MuxLookup(decode.sigs.sel_alu1, rs1, Seq(
    A1_ZERO -> 0.U,
    A1_PC -> fe_pc,
  ))
  alu.io.in2 := MuxLookup(decode.sigs.sel_alu2, rs2, Seq(
    A2_ZERO -> 0.U,
    A2_IMM -> imm,
  ))
  alu.io.fn :=  decode.sigs.alu_fn

  when(reset.asBool) {
    // todo load csr...
    ew_rf_we := false.B
  }.elsewhen(!stall) {
    ew_pc := fe_pc
    ew_ir := fe_ir
    ew_alu_out := alu.io.out
    ew_rf_we := decode.sigs.rf_we
    ew_sel_rf_wdata := decode.sigs.sel_rf_wdata
  }

  rf.io.wen := ew_rf_we
  rf.io.waddr := ew_rd_addr
  rf.io.wdata := MuxLookup(ew_sel_rf_wdata, ew_alu_out, Seq(
//    RF_WDATA_MEM ->
    RF_WDATA_PC4 -> (ew_pc +  4.U)
  ))

  printf("\nSTALL: %x\n", stall)
  printf("\tPC: %x, IR: %x\n", pc, iCache.cpuRes.data)
  printf("\tPC: %x, IR: %x, RF_WEN: %x\n", fe_pc, fe_ir, decode.sigs.rf_we)
  printf("\tPC: %x, IR: %x, RF_WEN: %x, REG[%d] <- %x\n", ew_pc, ew_ir, rf.io.wen, ew_rd_addr, rf.io.wdata)
}
