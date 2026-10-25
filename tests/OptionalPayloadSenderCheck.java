package com.wildfire.neoforge.appearance;
public final class OptionalPayloadSenderCheck {
    public static void main(String[] args){
        int[] sent={0};boolean[] available={false};
        if(OptionalPayloadSender.attempt(()->available[0],()->sent[0]++))throw new AssertionError("Unsupported channel sent");
        if(sent[0]!=0)throw new AssertionError("Missing channel must skip transport");
        available[0]=true;
        if(!OptionalPayloadSender.attempt(()->available[0],()->sent[0]++)||sent[0]!=1)throw new AssertionError("Supported packet lost");
        if(OptionalPayloadSender.attempt(()->true,()->{throw new UnsupportedOperationException("Payload may not be sent");}))throw new AssertionError("Rejected packet marked sent");
        available[0]=false;
        OptionalPayloadSender.attempt(()->available[0],()->sent[0]++);
        available[0]=true;
        if(!OptionalPayloadSender.attempt(()->available[0],()->sent[0]++)||sent[0]!=2)throw new AssertionError("Backend reannouncement must recover");
        try{OptionalPayloadSender.attempt(()->true,()->{throw new IllegalArgumentException("unrelated bug");});throw new AssertionError("Unrelated error hidden");}
        catch(IllegalArgumentException expected){}
        System.out.println("Optional payload checks passed: absent, supported, removed, rejected and reannounced channels");
    }
}
