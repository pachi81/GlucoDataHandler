package com.eveningoutpost.dexdrip.services.broadcastservice.models;

import android.os.Parcel;
import android.os.Parcelable;

public class GraphPoint implements Parcelable {
    public static final Creator<GraphPoint> CREATOR = new Creator<GraphPoint>() {

        @Override
        public GraphPoint createFromParcel(Parcel source) {
            return new GraphPoint(source);
        }

        @Override
        public GraphPoint[] newArray(int size) {
            return new GraphPoint[size];
        }
    };

    public float x;

    public float y;

    public GraphPoint(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public GraphPoint(Parcel parcel) {
        x = parcel.readFloat();
        y = parcel.readFloat();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeFloat(x);
        parcel.writeFloat(y);
    }
}
