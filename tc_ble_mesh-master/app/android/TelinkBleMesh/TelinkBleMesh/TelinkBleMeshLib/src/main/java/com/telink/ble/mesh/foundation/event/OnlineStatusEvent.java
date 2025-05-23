/********************************************************************************************************
 * @file OnlineStatusEvent.java
 *
 * @brief for TLSR chips
 *
 * @author telink
 * @date Sep. 30, 2017
 *
 * @par Copyright (c) 2017, Telink Semiconductor (Shanghai) Co., Ltd. ("TELINK")
 *
 *          Licensed under the Apache License, Version 2.0 (the "License");
 *          you may not use this file except in compliance with the License.
 *          You may obtain a copy of the License at
 *
 *              http://www.apache.org/licenses/LICENSE-2.0
 *
 *          Unless required by applicable law or agreed to in writing, software
 *          distributed under the License is distributed on an "AS IS" BASIS,
 *          WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *          See the License for the specific language governing permissions and
 *          limitations under the License.
 *******************************************************************************************************/
package com.telink.ble.mesh.foundation.event;

import android.os.Parcel;
import android.os.Parcelable;

import com.telink.ble.mesh.entity.OnlineStatusInfo;
import com.telink.ble.mesh.foundation.Event;

import java.util.ArrayList;
import java.util.List;

/**
 * OnlineStatusEvent class represents an event that notifies the online status of a device.
 * It extends the Event class and implements the Parcelable interface.
 */
public class OnlineStatusEvent extends Event<String> implements Parcelable {

    public static final String EVENT_TYPE_ONLINE_STATUS_NOTIFY = "com.telink.ble.mesh.EVENT_TYPE_ONLINE_STATUS_NOTIFY";
    private List<OnlineStatusInfo> onlineStatusInfoList;

    /**
     * Constructor for creating an OnlineStatusEvent object.
     *
     * @param sender           the object that sends the event
     * @param onlineStatusData the raw data of the online status
     */
    public OnlineStatusEvent(Object sender, byte[] onlineStatusData) {
        this(sender, EVENT_TYPE_ONLINE_STATUS_NOTIFY);
        this.onlineStatusInfoList = parseOnlineStatusData(onlineStatusData);
    }

    /**
     * Constructor for creating an OnlineStatusEvent object from a Parcel.
     *
     * @param in the Parcel object
     */
    protected OnlineStatusEvent(Parcel in) {
        onlineStatusInfoList = in.createTypedArrayList(OnlineStatusInfo.CREATOR);
    }

    /**
     * Creator constant for creating OnlineStatusEvent objects from a Parcel.
     */
    public static final Creator<OnlineStatusEvent> CREATOR = new Creator<OnlineStatusEvent>() {
        @Override
        public OnlineStatusEvent createFromParcel(Parcel in) {
            return new OnlineStatusEvent(in);
        }

        @Override
        public OnlineStatusEvent[] newArray(int size) {
            return new OnlineStatusEvent[size];
        }
    };

    /**
     * Parses the raw online status data and returns a list of OnlineStatusInfo objects.
     *
     * @param rawData the raw online status data
     * @return a list of OnlineStatusInfo objects
     */

    private List<OnlineStatusInfo> parseOnlineStatusData(byte[] rawData) {
        if (rawData == null || rawData.length < 4) return null;
        final int len = rawData.length;
        final int MIN_NODE_LEN = 3;
        int index = 0;
        byte type = rawData[index++];
        if (type != 0x62) {
            return null;
        }

        // low 4 bit
        int nodeLen = rawData[index++] & 0x0F;

        int statusLen = nodeLen - MIN_NODE_LEN;
        if (statusLen <= 0) return null;

        int sno = (rawData[index++] & 0xFF) | ((rawData[index++] & 0xFF) << 8);

        List<OnlineStatusInfo> statusInfoList = null;

        int address;
        byte sn;
        byte[] status;
        OnlineStatusInfo statusInfo;
        while (index + nodeLen <= len) {
            // 15 bit
            address = (rawData[index++] & 0xFF) | ((rawData[index++] & 0x7F) << 8);

            sn = rawData[index++];
            status = new byte[statusLen];

            System.arraycopy(rawData, index, status, 0, statusLen);
            index += statusLen;

            if (address == 0x00)
                break;


            statusInfo = new OnlineStatusInfo();
            statusInfo.address = address;
            statusInfo.sn = sn;
            statusInfo.status = status;
            if (statusInfoList == null) {
                statusInfoList = new ArrayList<>();
            }
            statusInfoList.add(statusInfo);
        }

        return statusInfoList;
    }

    /**
     * Constructor for creating an OnlineStatusEvent object.
     *
     * @param sender the object that sends the event
     * @param type   the type of the event
     */
    public OnlineStatusEvent(Object sender, String type) {
        super(sender, type);
    }

    /**
     * Returns the list of OnlineStatusInfo objects.
     *
     * @return the list of OnlineStatusInfo objects
     */
    public List<OnlineStatusInfo> getOnlineStatusInfoList() {
        return onlineStatusInfoList;
    }

    /**
     * Sets the list of OnlineStatusInfo objects.
     *
     * @param onlineStatusInfoList the list of OnlineStatusInfo objects
     */
    public void setOnlineStatusInfoList(List<OnlineStatusInfo> onlineStatusInfoList) {
        this.onlineStatusInfoList = onlineStatusInfoList;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeTypedList(onlineStatusInfoList);
    }
}