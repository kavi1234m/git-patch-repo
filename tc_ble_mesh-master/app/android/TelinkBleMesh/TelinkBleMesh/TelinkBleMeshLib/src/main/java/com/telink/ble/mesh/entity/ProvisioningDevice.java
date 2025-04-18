/********************************************************************************************************
 * @file ProvisioningDevice.java
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
package com.telink.ble.mesh.entity;

import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.os.Parcelable;

import com.telink.ble.mesh.core.provisioning.pdu.ProvisioningCapabilityPDU;
import com.telink.ble.mesh.util.Arrays;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Model for provisioning flow
 * Created by kee on 2019/9/4.
 */

public class ProvisioningDevice implements Parcelable {
    /**
     * 16: key
     * 2: key index
     * 1: flags
     * 4: iv index
     * 2: unicast adr
     */
    private static final int DATA_PDU_LEN = 16 + 2 + 1 + 4 + 2; // Length of the data PDU

    private BluetoothDevice bluetoothDevice; // The Bluetooth device associated with this provisioning device

    protected byte[] deviceUUID; // The UUID of the device

    /**
     * OOB Information
     * Bit - Description
     * 0 - Other
     * 1 - Electronic / URI
     * 2 - 2D machine-readable code
     * 3 - Bar code
     * 4 - Near Field Communication (NFC)
     * 5 - Number
     * 6 - String
     * 7 - Support for certificate-based provisioning
     * 8 - Support for provisioning records
     * 9 - Reserved for Future Use
     * 10 - Reserved for Future Use
     * 11 - On box
     * 12 - Inside box
     * 13 - On piece of paper
     * 14 - Inside manual
     * 15 - On device
     */
    protected int oobInfo; // Out-of-Band (OOB) information
    protected byte[] networkKey; // The network key used for provisioning
    protected int networkKeyIndex; // The index of the network key


    /**
     * 1 bit
     * The key refresh flag
     */
    protected byte keyRefreshFlag;

    /**
     * 1 bit
     * The IV update flag
     */
    protected byte ivUpdateFlag;


    /**
     * 4 bytes
     * The IV index
     */
    protected int ivIndex;

    /**
     * unicast address for primary element
     * 2 bytes
     */
    protected int unicastAddress;


    /**
     * auth value for static oob AuthMethod
     * {@link com.telink.ble.mesh.core.provisioning.AuthenticationMethod#StaticOOB}
     */
    protected byte[] authValue = null;

    /**
     * Flag indicating whether to automatically use no-OOB if auth value is null
     */
    protected boolean autoUseNoOOB = false;

    /**
     * The current provisioning state of the device
     */
    protected int provisioningState;

    /**
     * The device key
     */
    protected byte[] deviceKey = null;

    /**
     * The root certificate
     */
    protected byte[] rootCert = null;


    /**
     * Whether to automatically send start after receiving the Capability
     * if true, the provision-controller will send start pdu
     * otherwise, the user should call
     */
    protected boolean autoStart = true;

    protected ProvisioningCapabilityPDU deviceCapability = null; // The device capability


    /**
     * Constructor for ProvisioningDevice.
     *
     * @param bluetoothDevice The Bluetooth device associated with this provisioning device
     * @param deviceUUID      The UUID of the device
     * @param unicastAddress  The unicast address of the device
     */
    public ProvisioningDevice(BluetoothDevice bluetoothDevice, byte[] deviceUUID, int unicastAddress) {
        this.bluetoothDevice = bluetoothDevice;
        this.deviceUUID = deviceUUID;
        this.unicastAddress = unicastAddress;
    }

    /**
     * Constructor for ProvisioningDevice.
     *
     * @param bluetoothDevice The Bluetooth device associated with this provisioning device
     * @param deviceUUID      The UUID of the device
     * @param networkKey      The network key used for provisioning
     * @param networkKeyIndex The index of the network key
     * @param keyRefreshFlag  The key refresh flag
     * @param ivUpdateFlag    The IV update flag
     * @param ivIndex         The IV index
     * @param unicastAddress  The unicast address of the device
     */
    public ProvisioningDevice(BluetoothDevice bluetoothDevice, byte[] deviceUUID, byte[] networkKey, int networkKeyIndex, byte keyRefreshFlag, byte ivUpdateFlag, int ivIndex, int unicastAddress) {
        this.bluetoothDevice = bluetoothDevice;
        this.deviceUUID = deviceUUID;
        this.networkKey = networkKey;
        this.networkKeyIndex = networkKeyIndex;
        this.keyRefreshFlag = keyRefreshFlag;
        this.ivUpdateFlag = ivUpdateFlag;
        this.ivIndex = ivIndex;
        this.unicastAddress = unicastAddress;
    }

    /**
     * Default constructor for ProvisioningDevice.
     */
    public ProvisioningDevice() {
    }

    /**
     * Constructor for ProvisioningDevice that takes a Parcel as input.
     *
     * @param in The Parcel object containing the object data
     */
    protected ProvisioningDevice(Parcel in) {
        bluetoothDevice = in.readParcelable(BluetoothDevice.class.getClassLoader());
        deviceUUID = in.createByteArray();
        oobInfo = in.readInt();
        networkKey = in.createByteArray();
        networkKeyIndex = in.readInt();
        keyRefreshFlag = in.readByte();
        ivUpdateFlag = in.readByte();
        ivIndex = in.readInt();
        unicastAddress = in.readInt();
        authValue = in.createByteArray();
        provisioningState = in.readInt();
        deviceKey = in.createByteArray();
        rootCert = in.createByteArray();
    }

    /**
     * Creator constant for Parcelable.Creator.
     */
    public static final Creator<ProvisioningDevice> CREATOR = new Creator<ProvisioningDevice>() {
        @Override
        public ProvisioningDevice createFromParcel(Parcel in) {
            return new ProvisioningDevice(in);
        }

        @Override
        public ProvisioningDevice[] newArray(int size) {
            return new ProvisioningDevice[size];
        }
    };

    /**
     * Generates the provisioning data for the device.
     *
     * @return The provisioning data as a byte array
     */
    public byte[] generateProvisioningData() {
        byte flags = (byte) ((keyRefreshFlag & 0b01) | (ivUpdateFlag & 0b10));
        ByteBuffer buffer = ByteBuffer.allocate(DATA_PDU_LEN).order(ByteOrder.BIG_ENDIAN);
        buffer.put(networkKey)
                .putShort((short) networkKeyIndex)
                .put(flags)
                .putInt(ivIndex)
                .putShort((short) unicastAddress);
        return buffer.array();
    }

    // Getters and setters

    public int getOobInfo() {
        return oobInfo;
    }

    public void setOobInfo(int oobInfo) {
        this.oobInfo = oobInfo;
    }

    public BluetoothDevice getBluetoothDevice() {
        return bluetoothDevice;
    }

    public byte[] getDeviceUUID() {
        return deviceUUID;
    }

    public byte[] getNetworkKey() {
        return networkKey;
    }

    public void setNetworkKey(byte[] networkKey) {
        this.networkKey = networkKey;
    }

    public int getNetworkKeyIndex() {
        return networkKeyIndex;
    }

    public void setNetworkKeyIndex(int networkKeyIndex) {
        this.networkKeyIndex = networkKeyIndex;
    }

    public byte getKeyRefreshFlag() {
        return keyRefreshFlag;
    }

    public void setKeyRefreshFlag(byte keyRefreshFlag) {
        this.keyRefreshFlag = keyRefreshFlag;
    }

    public byte getIvUpdateFlag() {
        return ivUpdateFlag;
    }

    public void setIvUpdateFlag(byte ivUpdateFlag) {
        this.ivUpdateFlag = ivUpdateFlag;
    }

    public int getIvIndex() {
        return ivIndex;
    }

    public void setIvIndex(int ivIndex) {
        this.ivIndex = ivIndex;
    }

    public int getUnicastAddress() {
        return unicastAddress;
    }

    public byte[] getAuthValue() {
        return authValue;
    }

    public void setAuthValue(byte[] authValue) {
        this.authValue = authValue;
    }

    public int getProvisioningState() {
        return provisioningState;
    }

    public void setProvisioningState(int provisioningState) {
        this.provisioningState = provisioningState;
    }

    public byte[] getDeviceKey() {
        return deviceKey;
    }

    public void setDeviceKey(byte[] deviceKey) {
        this.deviceKey = deviceKey;
    }

    public ProvisioningCapabilityPDU getDeviceCapability() {
        return deviceCapability;
    }

    public void setDeviceCapability(ProvisioningCapabilityPDU deviceCapability) {
        this.deviceCapability = deviceCapability;
    }

    public void setUnicastAddress(int unicastAddress) {
        this.unicastAddress = unicastAddress;
    }

    public boolean isAutoUseNoOOB() {
        return autoUseNoOOB;
    }

    public void setAutoUseNoOOB(boolean autoUseNoOOB) {
        this.autoUseNoOOB = autoUseNoOOB;
    }

    public byte[] getRootCert() {
        return rootCert;
    }

    public void setRootCert(byte[] rootCert) {
        this.rootCert = rootCert;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(bluetoothDevice, flags);
        dest.writeByteArray(deviceUUID);
        dest.writeInt(oobInfo);
        dest.writeByteArray(networkKey);
        dest.writeInt(networkKeyIndex);
        dest.writeByte(keyRefreshFlag);
        dest.writeByte(ivUpdateFlag);
        dest.writeInt(ivIndex);
        dest.writeInt(unicastAddress);
        dest.writeByteArray(authValue);
        dest.writeInt(provisioningState);
        dest.writeByteArray(deviceKey);
        dest.writeByteArray(rootCert);
    }

    @Override
    public String toString() {
        return "ProvisioningDevice{" +
                "deviceUUID=" + Arrays.bytesToHexString(deviceUUID) +
                ", oobInfo=0b" + Integer.toBinaryString(oobInfo) +
                ", networkKey=" + Arrays.bytesToHexString(networkKey) +
                ", networkKeyIndex=" + networkKeyIndex +
                ", keyRefreshFlag=" + keyRefreshFlag +
                ", ivUpdateFlag=" + ivUpdateFlag +
                ", ivIndex=0x" + Long.toHexString(ivIndex) +
                ", unicastAddress=0x" + Integer.toHexString(unicastAddress) +
                ", authValue=" + Arrays.bytesToHexString(authValue) +
                ", autoUseNoOOB=" + autoUseNoOOB +
                ", provisioningState=" + provisioningState +
                ", deviceKey=" + Arrays.bytesToHexString(deviceKey) +
                '}';
    }
}