package com.eveningoutpost.dexdrip.services.broadcastservice.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Keep;

import java.util.ArrayList;
import java.util.List;

public class GraphLine implements Parcelable {
    public static final Creator<GraphLine> CREATOR = new Creator<GraphLine>() {

        @Override
        public GraphLine createFromParcel(Parcel source) {
            return new GraphLine(source);
        }

        @Override
        public GraphLine[] newArray(int size) {
            return new GraphLine[size];
        }
    };
    public List<GraphPoint> values;
    private int color;

    public GraphLine(int col) {
        values = new ArrayList<>();
        color = col;
    }

    @Keep
    public void add(float x,float y) {
        values.add(new GraphPoint(x,y));
    }

    public GraphLine(Parcel parcel) {
        values = parcel.readArrayList(GraphPoint.class.getClassLoader());
        color = parcel.readInt();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeList(values);
        parcel.writeInt(color);
    }
}
