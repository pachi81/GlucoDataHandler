package com.microtechmd.blecomm.entity;

import java.io.Serializable;

public class BleMessage implements Serializable {
    private static final long serialVersionUID = -784654219730084400L;
    private byte[] data;
    private int operation;
    private boolean success;

    // Getters and Setters
    public int getOperation() { return operation; }
    public boolean isSuccess() { return success; }
    public byte[] getData() { return data; }
}