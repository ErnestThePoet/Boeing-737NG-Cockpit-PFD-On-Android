package com.ecui.efis;

public class SWFilter {
    private int size=10;
    private int filledSize=0;
    private int nextValueIndex=0;
    private final float[] values;

    public SWFilter(int size){
        this.size=size;
        this.values=new float[size];
    }

    public float filter(float value){
        this.values[this.nextValueIndex++]=value;
        if(this.nextValueIndex==this.size){
            this.nextValueIndex=0;
        }
        if(this.filledSize<size){
            this.filledSize++;
        }

        return this.calculateValueMean();
    }

    private float calculateValueMean(){
        float result=0.0f;
        for(int i=0;i<this.filledSize;i++){
            result+=this.values[i];
        }

        return result/this.filledSize;
    }
}
