package com.ecui.efis;

public class SWFilter {
    private final int size;
    private int filledSize;
    private int nextValueIndex;
    private float[] values;

    public SWFilter(int size) {
        this.size = size;
        this.filledSize = 0;
        this.nextValueIndex = 0;
        this.values = new float[size];
    }

    public float filter(float value) {
        this.values[this.nextValueIndex++] = value;
        if (this.nextValueIndex == this.size) {
            this.nextValueIndex = 0;
        }
        if (this.filledSize < size) {
            this.filledSize++;
        }

        return this.calculateValueMean();
    }

    public void clear() {
        this.filledSize = 0;
        this.nextValueIndex = 0;
    }

    private float calculateValueMean() {
        float result = 0.0f;
        for (int i = 0; i < this.filledSize; i++) {
            result += this.values[i];
        }

        return result / this.filledSize;
    }
}
