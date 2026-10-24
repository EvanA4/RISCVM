package net.eabbott.riscvm.machine.register;

/*
* The class representation of a pipeline register. Each pipeline has four of these.
* A special "tick" function rotates the write-only half of the buffer to the read-only half.
* */
public class PipelineRegister {
    public byte[][] inst;
    public int[] pc, left, right, result, disp, strval;
    public byte[] rd, memop, aluop, amoop, amorl, amoaq;

    public PipelineRegister() {
        this.inst = new byte[2][0];
        this.pc = new int[2];
        this.left = new int[2];
        this.right = new int[2];
        this.result = new int[2];
        this.disp = new int[2];
        this.strval = new int[2];
        this.rd = new byte[2];
        this.memop = new byte[2];
        this.aluop = new byte[2];
        this.amoop = new byte[2];
        this.amorl = new byte[2];
        this.amoaq = new byte[2];
    }

    /*
    * Rotates the write-only half of the buffer to the read-only half.
    * */
    public void tick() {
        // Shift write-only values to read-only
        this.inst[1] = this.inst[0];
        this.pc[1] = this.pc[0];
        this.left[1] = this.left[0];
        this.right[1] = this.right[0];
        this.result[1] = this.result[0];
        this.disp[1] = this.disp[0];
        this.strval[1] = this.strval[0];
        this.rd[1] = this.rd[0];
        this.memop[1] = this.memop[0];
        this.aluop[1] = this.aluop[0];
        this.amoop[1] = this.amoop[0];
        this.amorl[1] = this.amorl[0];
        this.amoaq[1] = this.amoaq[0];

        // Flush write-only values
        this.inst[0] = new byte[0];
        this.pc[0] = 0;
        this.left[0] = 0;
        this.right[0] = 0;
        this.result[0] = 0;
        this.disp[0] = 0;
        this.strval[0] = 0;
        this.rd[0] = 0;
        this.memop[0] = 0;
        this.aluop[0] = 0;
        this.amoop[0] = 0;
        this.amorl[0] = 0;
        this.amoaq[0] = 0;
    }

    /*
    * Getters and setters for the pipeline register.
    * Getters read from the read-only half, while the
    * setters write to the write-only half.
    * */

    public byte[] getInst() {
        return inst[1];
    }

    public void setInst(byte[] inst) {
        this.inst[0] = inst;
    }

    public int getPC() {
        return pc[1];
    }

    public void setPC(int pc) {
        this.pc[0] = pc;
    }

    public int getLeft() {
        return left[1];
    }

    public void setLeft(int left) {
        this.left[0] = left;
    }

    public int getRight() {
        return right[1];
    }

    public void setRight(int right) {
        this.right[0] = right;
    }

    public int getResult() {
        return result[1];
    }

    public void setResult(int result) {
        this.result[0] = result;
    }

    public int getDisp() {
        return disp[1];
    }

    public void setDisp(int disp) {
        this.disp[0] = disp;
    }

    public int getStrval() {
        return strval[1];
    }

    public void setStrval(int strval) {
        this.strval[0] = strval;
    }

    public byte getRd() {
        return rd[1];
    }

    public void setRd(byte rd) {
        this.rd[0] = rd;
    }

    public byte getMemop() {
        return memop[1];
    }

    public void setMemop(byte memop) {
        this.memop[0] = memop;
    }

    public byte getAluop() {
        return aluop[1];
    }

    public void setALUOp(byte aluop) {
        this.aluop[0] = aluop;
    }

    public byte getAMOOp() {
        return amoop[1];
    }

    public void setAMOOp(byte amoop) {
        this.amoop[0] = amoop;
    }

    public byte getAMORl() {
        return amorl[1];
    }

    public void setAMORl(byte amorl) {
        this.amorl[0] = amorl;
    }

    public byte getAMOAq() {
        return amoaq[1];
    }

    public void setAMOAq(byte amoaq) {
        this.amoaq[0] = amoaq;
    }
}
