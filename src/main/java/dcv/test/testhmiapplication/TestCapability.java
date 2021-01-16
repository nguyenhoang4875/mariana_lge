package dcv.test.testhmiapplication;

import android.os.Parcel;
import android.os.Parcelable;

public class TestCapability implements Parcelable {
    private boolean isDistanceSupported;
    private boolean isConsumptionSupported;

    public TestCapability(boolean _isDistanceSupported, boolean _isConsumptionSupported) {
        isDistanceSupported = _isDistanceSupported;
        isConsumptionSupported = _isConsumptionSupported;
    }

    protected TestCapability(Parcel in) {
        isDistanceSupported = in.readInt() == 1;
        isConsumptionSupported = in.readInt() == 1;
    }

    public boolean isDistanceSupported() {
        return isDistanceSupported;
    }

    public boolean isConsumptionSupported() {
        return isConsumptionSupported;
    }

    public static final Creator<TestCapability> CREATOR = new Creator<TestCapability>() {
        @Override
        public TestCapability createFromParcel(Parcel in) {
            return new TestCapability(in);
        }

        @Override
        public TestCapability[] newArray(int size) {
            return new TestCapability[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(isDistanceSupported ? 1 : 0);
        dest.writeInt(isConsumptionSupported ? 1 : 0);
    }
}
