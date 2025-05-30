/********************************************************************************************************
 * @file ModelPublicationStatusMessage.java
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
package com.telink.ble.mesh.core.message.config;

import android.os.Parcel;
import android.os.Parcelable;

import com.telink.ble.mesh.core.MeshUtils;
import com.telink.ble.mesh.core.message.StatusMessage;
import com.telink.ble.mesh.entity.ModelPublication;

import java.nio.ByteOrder;

/**
 * The Config Model Publication Status is an unacknowledged message used to report the model Publication state of an outgoing message that is published by the model.
 * The ModelPublicationStatusMessage class represents an unacknowledged message used to report the model Publication state of an outgoing message that is published by the model.
 * It extends the StatusMessage class and implements the Parcelable interface for easy serialization and deserialization.
 */
public class ModelPublicationStatusMessage extends StatusMessage implements Parcelable {

    /**
     * The status code for the requesting message.
     */
    private byte status;

    /**
     * The model publication information.
     */
    private ModelPublication publication;

    /**
     * Default constructor for the ModelPublicationStatusMessage class.
     */
    public ModelPublicationStatusMessage() {
    }

    /**
     * Constructor for the ModelPublicationStatusMessage class that takes a Parcel as input and initializes the object.
     * This constructor is used for deserialization.
     *
     * @param in The Parcel object containing the serialized data.
     */
    protected ModelPublicationStatusMessage(Parcel in) {
        status = in.readByte();
        publication = in.readParcelable(ModelPublication.class.getClassLoader());
    }

    /**
     * The Creator object that is used to create instances of the ModelPublicationStatusMessage class from a Parcel.
     */
    public static final Creator<ModelPublicationStatusMessage> CREATOR = new Creator<ModelPublicationStatusMessage>() {
        @Override
        public ModelPublicationStatusMessage createFromParcel(Parcel in) {
            return new ModelPublicationStatusMessage(in);
        }

        @Override
        public ModelPublicationStatusMessage[] newArray(int size) {
            return new ModelPublicationStatusMessage[size];
        }
    };

    /**
     * Parses the byte array data and populates the fields of the ModelPublicationStatusMessage object.
     *
     * @param data The byte array data to parse.
     */
    @Override
    public void parse(byte[] data) {
        int index = 0;
        this.status = data[index++];

        ModelPublication modelPublication = new ModelPublication();
        modelPublication.elementAddress = MeshUtils.bytes2Integer(data, index, 2, ByteOrder.LITTLE_ENDIAN);
        index += 2;
        modelPublication.publishAddress = MeshUtils.bytes2Integer(data, index, 2, ByteOrder.LITTLE_ENDIAN);
        index += 2;
        modelPublication.appKeyIndex = (data[index++] & 0xFF) | ((data[index] & 0b1111) << 8);
        modelPublication.credentialFlag = (data[index] >> 4) & 0b1;
        modelPublication.rfu = (data[index] >> 5) & 0b111;
        index++;
        modelPublication.ttl = data[index++];
        modelPublication.period = data[index++];
        modelPublication.retransmitCount = (data[index] >> 5) & 0b111;
        modelPublication.retransmitIntervalSteps = data[index++] & 0x1F;
        modelPublication.modelId = (data[index++] & 0xFF) | ((data[index++] & 0xFF) << 8);

        if ((index + 2) <= data.length) {
            modelPublication.sig = false;
            modelPublication.modelId |= ((data[index++] & 0xFF) << 16) | ((data[index] & 0xFF) << 24);
        }

        this.publication = modelPublication;
    }

    /**
     * Returns a bitmask indicating the set of special object types marshaled by this Parcelable object instance.
     *
     * @return A bitmask indicating the set of special object types marshaled by this Parcelable object instance.
     */
    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Flatten this object into a Parcel.
     *
     * @param dest  The Parcel object to write the data to.
     * @param flags Additional flags about how the object should be written.
     */
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeByte(status);
        dest.writeParcelable(publication, flags);
    }

    /**
     * Returns the status code for the requesting message.
     *
     * @return The status code for the requesting message.
     */
    public byte getStatus() {
        return status;
    }

    /**
     * Returns the model publication information.
     *
     * @return The model publication information.
     */
    public ModelPublication getPublication() {
        return publication;
    }
}