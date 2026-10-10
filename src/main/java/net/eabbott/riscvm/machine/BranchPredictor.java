package net.eabbott.riscvm.machine;

import java.util.Arrays;

public class BranchPredictor {
    final long[] branchTargetBuffer; // For jump locations
    final byte[] branchPredictionTable; // For jump predictions
    final boolean enabled;
    final byte defaultPrediction;

    public BranchPredictor(int length, byte defaultPrediction) {
        this.enabled = length == 0;
        this.defaultPrediction = defaultPrediction;
        this.branchTargetBuffer = new long[length];
        Arrays.fill(this.branchTargetBuffer, -1);
        this.branchPredictionTable = new byte[length];
        Arrays.fill(this.branchPredictionTable, defaultPrediction);
    }

    /*
    * Returns the next PC to jump to, accounting for the branch prediction table.
    * Returns -1 if the PC is not in the branch target buffer or the prediction is false.
    * */
    public long getBranch(long programCounter) {
        // Edge case: disabled branch prediction
        if (!this.enabled) return -1;
        int index = Math.toIntExact(programCounter % this.branchTargetBuffer.length);

        // Check if entry is in branch target buffer
        long jumpLocation = this.branchTargetBuffer[index];
        if (jumpLocation == -1) return -1;

        // Check if branch prediction table speculates a jump
        byte prediction = this.branchPredictionTable[index];
        if ((prediction & 0b10) == 0) return -1;

        // Otherwise, return jump location
        return jumpLocation;
    }

    /*
    * Updates the jump location and prediction value for a given instruction address.
    * */
    public void update(long programCounter, long jumpLocation, boolean increment) {
        // Edge case: disabled branch prediction
        if (!this.enabled) return;
        int index = Math.toIntExact(programCounter % this.branchTargetBuffer.length);

        // Update values
        this.branchTargetBuffer[index] = jumpLocation;
        byte prediction = this.branchPredictionTable[index];
        int delta = increment ? 1 : -1;
        this.branchPredictionTable[index] = (byte) ((prediction + delta) & 0b11);
    }
}
