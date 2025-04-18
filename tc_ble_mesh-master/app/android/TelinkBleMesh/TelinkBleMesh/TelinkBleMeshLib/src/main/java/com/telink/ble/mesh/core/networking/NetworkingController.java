/********************************************************************************************************
 * @file NetworkingController.java
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
package com.telink.ble.mesh.core.networking;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.SparseArray;
import android.util.SparseLongArray;

import com.telink.ble.mesh.core.Encipher;
import com.telink.ble.mesh.core.MeshUtils;
import com.telink.ble.mesh.core.ble.GattConnection;
import com.telink.ble.mesh.core.message.MeshMessage;
import com.telink.ble.mesh.core.message.Opcode;
import com.telink.ble.mesh.core.networking.beacon.MeshBeaconPDU;
import com.telink.ble.mesh.core.networking.beacon.MeshPrivateBeacon;
import com.telink.ble.mesh.core.networking.beacon.SecureNetworkBeacon;
import com.telink.ble.mesh.core.networking.transport.lower.LowerTransportPDU;
import com.telink.ble.mesh.core.networking.transport.lower.SegmentAcknowledgmentMessage;
import com.telink.ble.mesh.core.networking.transport.lower.SegmentedAccessMessagePDU;
import com.telink.ble.mesh.core.networking.transport.lower.TransportControlMessagePDU;
import com.telink.ble.mesh.core.networking.transport.lower.UnsegmentedAccessMessagePDU;
import com.telink.ble.mesh.core.networking.transport.lower.UnsegmentedControlMessagePDU;
import com.telink.ble.mesh.core.networking.transport.upper.UpperTransportAccessPDU;
import com.telink.ble.mesh.core.proxy.ProxyAddAddressMessage;
import com.telink.ble.mesh.core.proxy.ProxyConfigurationMessage;
import com.telink.ble.mesh.core.proxy.ProxyConfigurationPDU;
import com.telink.ble.mesh.core.proxy.ProxyFilterStatusMessage;
import com.telink.ble.mesh.core.proxy.ProxyFilterType;
import com.telink.ble.mesh.core.proxy.ProxyPDU;
import com.telink.ble.mesh.core.proxy.ProxySetFilterTypeMessage;
import com.telink.ble.mesh.foundation.MeshConfiguration;
import com.telink.ble.mesh.util.Arrays;
import com.telink.ble.mesh.util.MeshLogger;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * managing networking operations.
 * networking packet partition and composition
 * Created by kee on 2019/7/31.
 */
public class NetworkingController {

    private static final String LOG_TAG = "Networking";

    // include mic(4)
    public static final int UNSEGMENTED_TRANSPORT_PAYLOAD_MAX_LENGTH = 15;

    public static final int UNSEGMENTED_ACCESS_PAYLOAD_MAX_LENGTH_DEFAULT = 11;

//    private static final int SEGMENTED_ACCESS_PAYLOAD_MAX_LENGTH = 12;

    public static final int UNSEGMENTED_ACCESS_PAYLOAD_MAX_LENGTH_LONG = 225;

//    private static final int SEGMENTED_ACCESS_PAYLOAD_MAX_LENGTH = UNSEGMENTED_ACCESS_PAYLOAD_MAX_LENGTH + 1;

    private ExtendBearerMode extendBearerMode = ExtendBearerMode.NONE;

    // segmentedAccessLength = unsegmentedAccessLength + 1
//    public int unsegmentedAccessLength = UNSEGMENTED_ACCESS_PAYLOAD_MAX_LENGTH_DEFAULT;


    private static final int DEFAULT_SEQUENCE_NUMBER_UPDATE_STEP = 0x100;

    /**
     * executing iv update procedure when connect proxy node success
     * based on sequence number value >= THRESHOLD_SEQUENCE_NUMBER
     *
     * @see #checkSequenceNumber(byte[], byte[])
     */
    private final static int THRESHOLD_SEQUENCE_NUMBER = 0xC00000;

//    private final static int THRESHOLD_SEQUENCE_NUMBER = 0x88; // for test ivIndex Update


    /**
     * receive
     */
    private final static int TRANSPORT_IN = 0x00;

    /**
     * transmit
     */
    private final static int TRANSPORT_OUT = 0x01;

    /**
     * AtomicInteger  variable used to generate a sequence number
     */
    private AtomicInteger mSequenceNumber = new AtomicInteger(0x0001);

    /**
     * boolean variable that indicates whether a private beacon has been received.
     */
    private boolean privateBeaconReceived = false;

    /**
     * boolean variable that indicates whether the initialization vector (IV) is being updated.
     */
    private boolean isIvUpdating = false;

    /**
     * nid
     */
    private byte nid;

    /**
     * byte array used to store an encryption key
     */
    private byte[] encryptionKey;

    /**
     * byte array used to store a privacy key.
     */
    private byte[] privacyKey;

    /**
     * integer variable used to store a network key index.
     */
    private int netKeyIndex;

    /**
     * deviceKey and unicastAddress map
     */
    private SparseArray<byte[]> deviceKeyMap;

    /**
     * 0x1122334400AABBCC
     * ivIndex: 0x11223344
     * sequenceNumber: 0xAABBCC
     * save device sequence number(lower 24 bits) and ivIndex(higher 32 bits), compare with sequence number in received network pdu
     * if sequence number in network pud is not larger than saved sequence number, drop this pdu
     */
    private SparseLongArray deviceSequenceNumberMap = new SparseLongArray();

    /**
     * appKey and appKeyIndex map
     */
    private SparseArray<byte[]> appKeyMap;

    /**
     * from mesh configuration
     */
    private long initIvIndex = 0;


    /**
     * unsigned 32-bit integer
     */
    private long ivIndex = 0;

    /**
     * provisioner address
     */
    private int localAddress = 0x7FFF;

    /**
     * direct connected node mesh address
     */
    private int directAddress = 0;

    /**
     * model message transition id
     * All tid in message(if contains) will be valued by this variable
     */
    private AtomicInteger tid = new AtomicInteger(0);

    /**
     * 13 bits
     */
    private static final int SEQ_ZERO_LIMIT = 0x1FFF;

    /**
     * received segmented message buffer by notification
     */
    private SparseArray<SegmentedAccessMessagePDU> receivedSegmentedMessageBuffer = new SparseArray<>();


    private static final int SEQ_AUTH_BUF_CAPACITY = 10;

    /**
     * segment completed auth buffer
     */
    private SparseLongArray completedSeqAuthBuffer = new SparseLongArray();

    /**
     * segment busy auth buffer
     */
    private SparseLongArray busySeqAuthBuffer = new SparseLongArray();

    /**
     * sent segmented message buffer
     */
    private SparseArray<SegmentedAccessMessagePDU> sentSegmentedMessageBuffer = new SparseArray<>();

//    private SparseArray<byte[]> receivedSegmentedMessageBuffer;

    /**
     * last seqAuth in segmented pdu
     * 0: segment idle
     * others: segment busy
     */
    private long lastSeqAuth = 0;

    /**
     * last seqAuth source address
     */
    private int lastSegSrc = 0;

    // if last RX segment packets complete
    private boolean lastSegComplete = true;


    private NetworkingBridge mNetworkingBridge;

    private int mSnoUpdateStep = DEFAULT_SEQUENCE_NUMBER_UPDATE_STEP;

    private Handler mDelayHandler;

    private SegmentAckMessageSentTask mAccessSegCheckTask = new SegmentAckMessageSentTask();

    // waiting for segment ack message
    private SegmentBlockWaitingTask mSegmentBlockWaitingTask = new SegmentBlockWaitingTask();

    private static final int BLOCK_ACK_WAITING_TIMEOUT = 15 * 1000;

    private boolean segmentedBusy = false;

    private Runnable segmentedMessageTimeoutTask = new SegmentedMessageTimeoutTask();

    /**
     * sending message with ack
     * only one reliable message can be sent at one time
     * <p>
     * for reliable message,
     */
    private MeshMessage mSendingReliableMessage;

    private boolean reliableBusy = false;

    // reliable
    private final Object RELIABLE_SEGMENTED_LOCK = new Object();

    private Set<Integer> mResponseMessageBuffer = new LinkedHashSet<>();

    private int[] whiteList;

    private int proxyFilterInitStep = 0;

    private static final int PROXY_FILTER_INIT_STEP_SET_TYPE = 1;

    private static final int PROXY_FILTER_SET_STEP_ADD_ADR = 2;

    private static final int PROXY_FILTER_INIT_TIMEOUT = 5 * 1000;

    /**
     * networking pdu sending prepared queue
     */
    private final Queue<byte[]> mNetworkingQueue = new ConcurrentLinkedQueue<>();

    /**
     * used in blob transfer (firmware update)
     */
    public static final long NETWORK_INTERVAL_FOR_FU = 180; // 240 ms // 320


    public static final long NETWORK_INTERVAL_DEFAULT = 240; // 240 ms // 320

//    public static final long NETWORK_INTERVAL_DEFAULT = 10; // for test

    /**
     * network packet sent to un-direct connected node should push to queue, and send periodically
     */
    public static long netPktSendInterval = NETWORK_INTERVAL_DEFAULT; // 240 ms // 320

    /**
     * used as a lock object for synchronization purposes.
     */
    private final Object mNetworkBusyLock = new Object();


    private boolean networkingBusy = false;

    /**
     * store a private beacon key
     */
    private byte[] privateBeaconKey = null;

    /**
     * initializes a new Handler object using the looper from the HandlerThread
     *
     * @param handlerThread thread
     */
    public NetworkingController(HandlerThread handlerThread) {
        this.mDelayHandler = new Handler(handlerThread.getLooper());
        this.appKeyMap = new SparseArray<>();
        this.deviceKeyMap = new SparseArray<>();
    }

    /**
     * setter of networkingBridge
     *
     * @param networkingBridge bridge
     */
    public void setNetworkingBridge(NetworkingBridge networkingBridge) {
        this.mNetworkingBridge = networkingBridge;
    }

    /**
     * This method is used to set up the Mesh network configuration.
     * It takes a MeshConfiguration object as a parameter
     * and initializes various variables and keys based on the configuration provided.
     *
     * @param configuration config
     */
    public void setup(MeshConfiguration configuration) {
        this.clear();
        this.resetDirectAddress();
        this.initIvIndex = configuration.ivIndex & MeshUtils.UNSIGNED_INTEGER_MAX;
        this.ivIndex = initIvIndex;
        int seqNo = configuration.sequenceNumber;
        this.mSequenceNumber.set(initSequenceNumber(seqNo));
        this.netKeyIndex = configuration.netKeyIndex;
        byte[][] k2Output = Encipher.calculateNetKeyK2(configuration.networkKey);
        this.nid = (byte) (k2Output[0][15] & 0x7F);
        this.encryptionKey = k2Output[1];
        this.privacyKey = k2Output[2];
        this.privateBeaconKey = Encipher.generatePrivateBeaconKey(configuration.networkKey);
        this.appKeyMap = configuration.appKeyMap;
        this.deviceKeyMap = configuration.deviceKeyMap;

        this.localAddress = configuration.localAddress;
        this.whiteList = configuration.proxyFilterWhiteList;
    }

    /**
     * This method is used to clear/reset the state of various variables
     * and data structures in the class.
     */
    public void clear() {
        if (mDelayHandler != null) {
            mDelayHandler.removeCallbacksAndMessages(null);
        }

        this.networkingBusy = false;
        this.segmentedBusy = false;
        this.reliableBusy = false;
        this.mNetworkingQueue.clear();
        // last lastSeqAuth and lastSegComplete should keep to avoid the complete seqAuth not saved in completedSeqAuthBuffer
        // for example , after key bind success, app send publish(segment packet) immediately,
        // if the device resend the aggregator segment packet, network
//        this.lastSeqAuth = 0;
//        this.lastSegSrc = 0;
        this.lastSegComplete = true;
        this.deviceSequenceNumberMap.clear();
        this.receivedSegmentedMessageBuffer.clear();
        this.sentSegmentedMessageBuffer.clear();
        this.mResponseMessageBuffer.clear();
        this.isIvUpdating = false;
        this.lastSegComplete = true;
        this.privateBeaconReceived = false;
    }

    /**
     * This method resets the direct address to 0.
     */
    public void resetDirectAddress() {
        this.directAddress = 0;
    }

    /**
     * add a device key to the device key map. The device key is associated with a specific unicast address.
     *
     * @param unicastAddress address
     * @param deviceKey      device key value
     */
    public void addDeviceKey(int unicastAddress, byte[] deviceKey) {
        this.deviceKeyMap.put(unicastAddress, deviceKey);
    }

    /**
     * removes a device key from the device key map based on the provided unicast address.
     *
     * @param unicastAddress address
     */
    public void removeDeviceKey(int unicastAddress) {
        this.deviceKeyMap.remove(unicastAddress);
    }


    /**
     * Sets the extend bearer mode.
     *
     * @param extendBearerMode The extend bearer mode to be set.
     */
    public void setExtendBearerMode(ExtendBearerMode extendBearerMode) {
        log("setExtendBearerMode: " + extendBearerMode);
        this.extendBearerMode = extendBearerMode;
    }

    /**
     * Retrieves the extend bearer mode.
     *
     * @return The extend bearer mode.
     */
    public ExtendBearerMode getExtendBearerMode() {
        return extendBearerMode;
    }

    /**
     * This method calculates and returns the segment access length based on the destination address and opcode.
     * If the GATT connection MTU is less than UNSEGMENTED_ACCESS_PAYLOAD_MAX_LENGTH_LONG,
     * then the default unsegmented access payload max length is returned.
     * If the destination address matches the direct address and the opcode is BLOB_CHUNK_TRANSFER,
     * then the unsegmented access payload max length for long packets is returned.
     * If the extend bearer mode is set to NONE, then the default unsegmented access payload max length is returned.
     * If the extend bearer mode is set to GATT and the destination address matches the direct address,
     * then the unsegmented access payload max length for long packets is returned,
     * otherwise the default unsegmented access payload max length is returned.
     * If the extend bearer mode is set to GATT_ADV, then the unsegmented access payload max length for long packets is returned.
     * If none of the above conditions are met, the default unsegmented access payload max length is returned.
     *
     * @param dstAddress The destination address
     * @param opcode     The opcode
     * @return The segment access length
     */
    private int getSegmentAccessLength(int dstAddress, int opcode) {
        if (GattConnection.mtu < UNSEGMENTED_ACCESS_PAYLOAD_MAX_LENGTH_LONG) {
            return UNSEGMENTED_ACCESS_PAYLOAD_MAX_LENGTH_DEFAULT;
        }
        if (dstAddress == directAddress && opcode == Opcode.BLOB_CHUNK_TRANSFER.value) {
            return UNSEGMENTED_ACCESS_PAYLOAD_MAX_LENGTH_LONG;
        }
        switch (extendBearerMode) {
            case NONE:
                return UNSEGMENTED_ACCESS_PAYLOAD_MAX_LENGTH_DEFAULT;
            case GATT:
                return dstAddress == directAddress ? UNSEGMENTED_ACCESS_PAYLOAD_MAX_LENGTH_LONG : UNSEGMENTED_ACCESS_PAYLOAD_MAX_LENGTH_DEFAULT;
            case GATT_ADV:
                return UNSEGMENTED_ACCESS_PAYLOAD_MAX_LENGTH_LONG;
        }
        return UNSEGMENTED_ACCESS_PAYLOAD_MAX_LENGTH_DEFAULT;
    }

    /**
     * This method is used to save the completed sequence authentication value for a given source.
     * It is synchronized to ensure thread safety.
     *
     * @param src     The source value for which the sequence authentication is being saved.
     * @param seqAuth The sequence authentication value to be saved.
     */
    private synchronized void saveCompletedSeqAuth(int src, long seqAuth) {
        log(String.format(Locale.getDefault(), "save complete seqAuth src: 0x%04X -- seqAuth: 0x%014X", src, seqAuth));
        this.completedSeqAuthBuffer.put(src, seqAuth);
        /*if (this.completedSeqAuthBuffer.size() > SEQ_AUTH_BUF_CAPACITY) {
            log("remove buffer");
            this.completedSeqAuthBuffer.removeAt(SEQ_AUTH_BUF_CAPACITY);
        }*/
    }

    /**
     * checks if a specific authentication sequence exists in the completed authentication buffer for a given source.
     * It compares the authentication sequence number (seqAuth) against the 0
     *
     * @param src     source address
     * @param seqAuth seqAuth
     * @return is target auth exists
     */
    private boolean isCompleteAuthExists(int src, long seqAuth) {
        return this.completedSeqAuthBuffer.get(src, 0) == seqAuth;
    }

    /**
     * used to save the busy sequence authentication for a particular source.
     *
     * @param src     source address
     * @param seqAuth seqAuth
     */
    private synchronized void saveBusySeqAuth(int src, long seqAuth) {
        log(String.format(Locale.getDefault(), "save busy seqAuth src: 0x%04X -- seqAuth: 0x%014X", src, seqAuth));
        this.busySeqAuthBuffer.put(src, seqAuth);
    }

    /**
     * checks if a specific authentication sequence exists in the busy authentication buffer for a given source.
     * It compares the authentication sequence number (seqAuth) against the 0
     *
     * @param src     source address
     * @param seqAuth seqAuth
     * @return is target auth exists
     */
    private boolean isBusyAuthExists(int src, long seqAuth) {
        return this.busySeqAuthBuffer.get(src, 0) == seqAuth;
    }


    /**
     * Checks the sequence number and updates the IV index if necessary.
     * If the IV index is being updated by a remote device, logs a message.
     * Otherwise, sets the IV index to the updated value if necessary.
     * If a private beacon has been received, creates and sends an IV updating beacon.
     * Otherwise, creates and sends a secure network beacon with the updated IV index.
     *
     * @param networkId The network ID.
     * @param beaconKey The beacon key.
     */
    public void checkSequenceNumber(byte[] networkId, byte[] beaconKey) {
        final boolean updatingNeeded = this.mSequenceNumber.get() >= THRESHOLD_SEQUENCE_NUMBER;

        if (isIvUpdating) {
            log("beacon updating status changed by remote device ");
        } else {
            this.isIvUpdating = updatingNeeded;
            if (isIvUpdating) {
                this.ivIndex = initIvIndex + 1;
            }
        }

        if (privateBeaconReceived) {
            MeshPrivateBeacon beacon = MeshPrivateBeacon.createIvUpdatingBeacon((int) this.initIvIndex, privateBeaconKey, isIvUpdating);
            sendMeshBeaconPdu(beacon);
        } else {
            SecureNetworkBeacon networkBeacon = SecureNetworkBeacon.createIvUpdatingBeacon((int) this.initIvIndex, networkId, beaconKey, isIvUpdating);
            log("send beacon: " + networkBeacon.toString());
            sendMeshBeaconPdu(networkBeacon);
        }
    }

    /**
     * called when the ivIndex is updated.
     *
     * @param newIvIndex new IvIndex
     */
    private void onIvUpdated(long newIvIndex) {
        if (newIvIndex > initIvIndex || this.initIvIndex == MeshUtils.IV_MISSING) {
            log(String.format(" iv updated to %08X", newIvIndex));
            this.initIvIndex = (int) newIvIndex;
            this.deviceSequenceNumberMap.clear();
            this.mSequenceNumber.set(0);
            if (mNetworkingBridge != null) {
                mNetworkingBridge.onNetworkInfoUpdate(mSequenceNumber.get(), (int) newIvIndex);
            }
        } else {
            log(" iv not updated");
        }
    }

    /**
     * This method is used to handle the received IV (Initialization Vector) index from a remote device in a Mesh network. The IV index is a value used for encryption and security purposes in the network.
     * <p>
     * The method takes two parameters: the remote IV index value and a boolean indicating whether the IV index is being updated. It logs the received IV index and the update status.
     * <p>
     * If the local IV index is missing (initialized as MeshUtils.IV_MISSING), it updates the local IV index with the received remote IV index and sets the IV update status accordingly. It then calls the onIvUpdated() method to handle the IV update.
     * <p>
     * If the local IV index is not missing, it calculates the difference (d-value) between the remote and local IV indices. If the d-value is zero, it means that the remote node has completed its IV update. If the local node is also in the process of updating its IV index, it sets the IV update status to false and calls the onIvUpdated() method.
     * <p>
     * If the d-value is greater than zero, it means that a larger IV index has been received. If the d-value is less than or equal to 42 (a predefined threshold), it updates the local IV index with the received remote IV index and calls the onIvUpdated() method with the updated IV index. If the d-value is greater than 42, it logs a message indicating that the d-value is too large.
     * <p>
     * If the d-value is negative, it means that a smaller IV index has been received, which is unexpected. It logs a warning message.
     *
     * @param remoteIvIndex ivIndex
     * @param updating      is updating
     */
    private void onIvIndexReceived(long remoteIvIndex, boolean updating) {
        log(String.format("iv index received iv: %08X -- updating: %b -- localIv: %08X -- updating: %b ",
                remoteIvIndex,
                updating,
                this.ivIndex,
                this.isIvUpdating));
        if (this.ivIndex == MeshUtils.IV_MISSING) {
            this.isIvUpdating = updating;
            this.ivIndex = remoteIvIndex;
            this.onIvUpdated(remoteIvIndex);
            return;
        }
        // d-value
        long dVal = remoteIvIndex - this.ivIndex;

        if (dVal == 0) {
            // remote node iv update complete
            if (!updating && this.isIvUpdating) {
                this.isIvUpdating = false;
                this.onIvUpdated(remoteIvIndex);
            }
        } else if (dVal > 0) {
            log("larger iv index received");
            if (dVal <= 42) {
                this.isIvUpdating = updating;
                this.ivIndex = remoteIvIndex;

                this.onIvUpdated(updating ? remoteIvIndex - 1 : remoteIvIndex);
            } else {
                log("iv index dVal greater than 42");
            }

        } else {
            log(" smaller iv index received", MeshLogger.LEVEL_WARN);
        }
    }

    /**
     * This method is used to initialize the sequence number for a specific operation.
     * It takes in the current sequence number as a parameter and returns the initialized sequence number.
     *
     * @param sequenceNumber sno
     * @return init sno
     */
    private int initSequenceNumber(int sequenceNumber) {
        if (mSnoUpdateStep == 0 || mSnoUpdateStep == 1) return sequenceNumber;
        int initSno = (sequenceNumber / mSnoUpdateStep + 1) * mSnoUpdateStep;
        onSequenceNumberUpdate(initSno);
        log("init sno: " + initSno);
        return initSno;
    }

    /**
     * send proxy config message
     *
     * @param message prepared message
     */
    private void sendProxyConfigurationMessage(ProxyConfigurationMessage message) {
        byte[] transportPdu = message.toByteArray();
        ProxyConfigurationPDU networkLayerPDU = createProxyConfigurationPdu(transportPdu,
                localAddress, getTransmitIvIndex(), this.mSequenceNumber.get());
        sendProxyNetworkPdu(networkLayerPDU);
    }

    /**
     * Sends a mesh message to the specified destination address.
     *
     * @param meshMessage the mesh message to be sent
     * @return true if the message was sent successfully, false otherwise
     */
    public boolean sendMeshMessage(MeshMessage meshMessage) {

        int dst = meshMessage.getDestinationAddress();
        if (!validateDestinationAddress(dst)) {
            log("invalid dst address: " + String.format("%04X", dst), MeshLogger.LEVEL_WARN);
            return false;
        }

        AccessType accessType = meshMessage.getAccessType();
        byte[] encryptionKey;
        if (accessType == AccessType.APPLICATION) {
            encryptionKey = getAppKey(meshMessage.getAppKeyIndex());
        } else {
            encryptionKey = getDeviceKey(meshMessage.getDestinationAddress());
        }

        if (encryptionKey == null) {
            log("access key not found : " + accessType, MeshLogger.LEVEL_WARN);
            return false;
        }
        meshMessage.setAccessKey(encryptionKey);


        return postMeshMessage(meshMessage, false);
    }


    /**
     * This method is used to post a mesh message. It takes a MeshMessage object and a boolean value indicating whether to retry sending the message.
     * <p>
     * The method retrieves the destination address, source address, SZMIC value, opcode, AKF value, and AID value from the MeshMessage object. If the access type is application, it calculates the AID value using the Encipher.k4() method. Otherwise, it sets the AID value to 0x00.
     * <p>
     * The method also retrieves the sequence number and parameters from the MeshMessage object. If the parameters exist and the TID position is valid, it updates the TID value based on the retry flag.
     * <p>
     * Next, an AccessLayerPDU object is created using the opcode and parameters. The AccessLayerPDU is then converted to a byte array.
     * <p>
     * The method determines whether the message needs to be segmented by comparing the length of the access PDU data with the segment length for the given destination address and opcode. If the message is segmented, it checks if there is already a segmented message being sent.
     * <p>
     * Afterwards, the method retrieves the transmit IV index and creates an UpperTransportAccessPDU object using the access PDU data, access key, SZMIC value, access type, IV index, sequence number, source address, and destination address.
     * <p>
     * If the UpperTransportAccessPDU is null, it returns false. Otherwise, it logs the upper transport PDU.
     * <p>
     * The method then checks if the message is reliable. If it is not segmented, it sends an unsegmented access message. If it is segmented, it creates segmented access messages and sends them.
     * <p>
     * If the message is reliable and unsegmented, it starts a reliable timeout check. If the message is reliable and segmented, it starts a reliable timeout check when a block ack is received.
     * <p>
     * Finally, the method sends the network PDUs and returns true.
     *
     * @param meshMessage target message
     * @param retry       is retry
     * @return true if the message was sent successfully, false otherwise
     */
    private boolean postMeshMessage(MeshMessage meshMessage, boolean retry) {
        int dst = meshMessage.getDestinationAddress();
        int src = localAddress;
        int aszmic = meshMessage.getSzmic();
        int opcode = meshMessage.getOpcode();
        byte akf = meshMessage.getAccessType().akf;
        byte aid;
        if (meshMessage.getAccessType() == AccessType.APPLICATION) {
            aid = Encipher.k4(meshMessage.getAccessKey());
        } else {
            aid = 0x00;
        }

        int sequenceNumber = mSequenceNumber.get();

        final byte[] params = meshMessage.getParams();
        final int tidPos = meshMessage.getTidPosition();
        if (params != null && tidPos >= 0 && params.length > tidPos) {
            params[tidPos] = retry ? (byte) this.tid.get() : (byte) this.tid.incrementAndGet();
        }

        AccessLayerPDU accessPDU = new AccessLayerPDU(meshMessage.getOpcode(), params);

        byte[] accessPduData = accessPDU.toByteArray();

//        boolean segmented = accessPduData.length > UNSEGMENTED_ACCESS_PAYLOAD_MAX_LENGTH;
        final int segmentLen = getSegmentAccessLength(dst, opcode);
        boolean segmented = accessPduData.length > segmentLen;
        meshMessage.setSegmented(segmented);
        if (segmented) {
            synchronized (RELIABLE_SEGMENTED_LOCK) {
                if (segmentedBusy) {
                    log("segment message send err: segmented busy");
                    return false;
                }
            }
        }

        log("post access pdu: " + Arrays.bytesToHexString(accessPDU.toByteArray(), ""));

        int ivIndex = getTransmitIvIndex();

        UpperTransportAccessPDU upperPDU = createUpperTransportAccessPDU(accessPduData,
                meshMessage.getAccessKey(),
                (byte) meshMessage.getSzmic(),
                meshMessage.getAccessType(),
                ivIndex,
                sequenceNumber, src, dst);

        if (upperPDU == null) {
            log("create upper transport pdu err: encrypt err", MeshLogger.LEVEL_WARN);
            return false;
        }
        log("upper transport pdu: " + Arrays.bytesToHexString(upperPDU.getEncryptedPayload(), ""));

        // check message reliable
        final boolean reliable = meshMessage.isReliable();

        // upperPDU.getEncryptedPayload().length <= UNSEGMENTED_TRANSPORT_PAYLOAD_MAX_LENGTH
        //
        /*
            for unsegmented & reliable message, start reliable timeout check immediately,
            for segmented & reliable message, start reliable timeout check when received block ack
         */
        if (!segmented) {
            log("send unsegmented access message");

            if (reliable) {
                if (reliableBusy) {
                    log("unsegmented reliable message send err: busy", MeshLogger.LEVEL_WARN);
                    return false;
                }
                reliableBusy = true;
                mSendingReliableMessage = meshMessage;
                restartReliableMessageTimeoutTask();
            }

            UnsegmentedAccessMessagePDU unsegmentedMessagePDU = createUnsegmentedAccessMessage(upperPDU.getEncryptedPayload(), akf, aid);
            NetworkLayerPDU networkPDU = createNetworkPDU(unsegmentedMessagePDU.toByteArray(),
                    meshMessage.getCtl(), meshMessage.getTtl(), src, dst, ivIndex, sequenceNumber);
            sendNetworkPdu(networkPDU);
        } else {
            synchronized (RELIABLE_SEGMENTED_LOCK) {
                if (reliable) {
                    if (reliableBusy) {
                        log("segmented reliable message send err: busy", MeshLogger.LEVEL_WARN);
                        return false;
                    }
                    reliableBusy = true;
                    mSendingReliableMessage = meshMessage;
//                    restartReliableMessageTimeoutTask(); //
                }
                SparseArray<SegmentedAccessMessagePDU> segmentedAccessMessages = createSegmentedAccessMessage(upperPDU.getEncryptedPayload(), akf, aid, aszmic, sequenceNumber, segmentLen);
                if (segmentedAccessMessages.size() == 0) return false;

                log("send segmented access message");
                List<NetworkLayerPDU> networkLayerPduList = new ArrayList<>();
                for (int i = 0; i < segmentedAccessMessages.size(); i++) {
                    byte[] lowerTransportPdu = segmentedAccessMessages.get(i).toByteArray();
                    NetworkLayerPDU networkPDU = createNetworkPDU(lowerTransportPdu,
                            meshMessage.getCtl(), meshMessage.getTtl(), src, dst, ivIndex, sequenceNumber + i);
                    networkLayerPduList.add(networkPDU);
                }
                if (MeshUtils.validUnicastAddress(dst)) {
                    this.sentSegmentedMessageBuffer = segmentedAccessMessages.clone();
                    startSegmentedMessageTimeoutCheck();
                    startSegmentedBlockAckWaiting(meshMessage.getCtl(), meshMessage.getTtl(), src, dst);
                } else if (reliable) {
                    restartReliableMessageTimeoutTask();
                }
                sendNetworkPduList(networkLayerPduList);
            }
        }
        return true;
    }

    /**
     * proxy filter init steps:
     * 1. set white list
     * 2. add localAddress and 0xFFFF into address array
     */
    public void proxyFilterInit() {
        proxyFilterInitStep = 0;
        mDelayHandler.removeCallbacks(proxyFilterInitTimeoutTask);
        mDelayHandler.postDelayed(proxyFilterInitTimeoutTask, PROXY_FILTER_INIT_TIMEOUT);
        setFilterType(ProxyFilterType.WhiteList);
    }

    /**
     * This method is used to set the filter type for the proxy.
     * It takes a parameter of type ProxyFilterType, which represents the desired filter type.
     * It creates a ProxySetFilterTypeMessage object with the value of the filter type and then calls the sendProxyConfigurationMessage method to send the message to the proxy.
     *
     * @param filterType target type
     */
    private void setFilterType(ProxyFilterType filterType) {
        ProxySetFilterTypeMessage message = new ProxySetFilterTypeMessage(filterType.value);
        sendProxyConfigurationMessage(message);
    }

    /**
     * add a filter address to a proxy configuration.
     *
     * @param addressArray addresses
     */
    private void addFilterAddress(int[] addressArray) {
        ProxyAddAddressMessage addAddressMessage = new ProxyAddAddressMessage(addressArray);
        sendProxyConfigurationMessage(addAddressMessage);
    }

    /**
     * Validates the destination address.
     *
     * @param address the destination address to be validated
     * @return true if the address is not equal to zero, false otherwise
     */
    private boolean validateDestinationAddress(int address) {
        return address != 0;
    }

    /**
     * get app key in map
     *
     * @return app key at index
     */
    private byte[] getAppKey(int appKeyIndex) {
        if (this.appKeyMap == null) return null;
        return this.appKeyMap.get(appKeyIndex);
    }


    /**
     * get device key for config model message when akf==0
     * {@link AccessType#DEVICE}
     *
     * @param unicastAddress node address
     * @return device key
     */
    private byte[] getDeviceKey(int unicastAddress) {
        if (this.deviceKeyMap == null) return null;
        return this.deviceKeyMap.get(unicastAddress);
    }

    /**
     * This method starts a timeout check for segmented messages.
     * It sets a flag to indicate that the system is currently busy with segmented messages.
     * It first removes any previously scheduled timeout tasks using the mDelayHandler's removeCallbacks() method.
     * Then, it schedules a new timeout task called segmentedMessageTimeoutTask using the mDelayHandler's postDelayed() method.
     * The timeout duration is specified by the constant BLOCK_ACK_WAITING_TIMEOUT.
     */
    private void startSegmentedMessageTimeoutCheck() {
        segmentedBusy = true;
        mDelayHandler.removeCallbacks(segmentedMessageTimeoutTask);
        mDelayHandler.postDelayed(segmentedMessageTimeoutTask, BLOCK_ACK_WAITING_TIMEOUT);
    }

    /**
     * used to start the waiting period for receiving a segmented block acknowledgment.
     *
     * @param src source address
     * @param dst dst address
     */
    private void startSegmentedBlockAckWaiting(int ctl, int ttl, int src, int dst) {
        mDelayHandler.removeCallbacks(mSegmentBlockWaitingTask);
        mSegmentBlockWaitingTask.resetParams(ctl, ttl, src, dst);
        mDelayHandler.postDelayed(mSegmentBlockWaitingTask, getSegmentedTimeout(ttl, true));
    }

    /**
     * stop segmented message block acknowledgment waiting
     *
     * @param complete true: when timeout {@link #BLOCK_ACK_WAITING_TIMEOUT}
     *                 or block ack shows all segmented message received
     *                 false: when checking block ack
     */
    private void stopSegmentedBlockAckWaiting(boolean complete, boolean success) {
        log(String.format("stop segmented block waiting, complete - %B success - %B", complete, success));
        mDelayHandler.removeCallbacks(mSegmentBlockWaitingTask);
        if (complete) {
            onSegmentedMessageComplete(success);
        }
    }

    /**
     * segment message sent complete
     *
     * @param success true: received completed block
     *                false: timeout
     */
    private void onSegmentedMessageComplete(boolean success) {
        log("segmented message complete, success? : " + success);
        // clear segment state
        clearSegmentSendingState(success);

        // check reliable state
        if (reliableBusy) {
            /*
            if segmented message sent success, check response after #@link{RELIABLE_MESSAGE_TIMEOUT}
            else if segmented message timeout, retry immediately
             */
            if (success) {
                restartReliableMessageTimeoutTask();
            } else {
                // if segment timeout , no need to resend reliable message
                onReliableMessageComplete(false);
            }
        }
    }

    /**
     * This method is used to clear the state of segment sending.
     *
     * @param success is segment message send success
     */
    private void clearSegmentSendingState(boolean success) {
        segmentedBusy = false;
        mDelayHandler.removeCallbacks(segmentedMessageTimeoutTask);
        sentSegmentedMessageBuffer.clear();
        if (mNetworkingBridge != null) {
            mNetworkingBridge.onSegmentMessageComplete(success);
        }
        /*final  MeshMessage meshMessage = mSendingReliableMessage;
        if (meshMessage != null){
            int opcode = meshMessage.getOpcode();

        }*/

    }

    /**
     * This method calculates the timeout for segment acknowledgments in a segmented communication protocol. The timeout is determined based on the time-to-live (TTL) value and whether it is for sending or receiving.
     * <p>
     * If the timeout is for sending (outer), it calculates the timeout by adding the relay timeout (300ms), the segment acknowledgment timeout (200ms + 50ms * TTL), and the number of packets in the networking queue multiplied by the network packet send interval.
     * <p>
     * If the timeout is for receiving, it calculates the timeout by adding the relay timeout and the segment acknowledgment timeout.
     * <p>
     * The method then logs the calculated timeout and returns it.
     * <p>
     * Please let me know if you have any further questions!
     *
     * @param ttl   ttl
     * @param outer is sending
     * @return timeout milli
     */
    private long getSegmentedTimeout(int ttl, boolean outer) {

        final int relayTimeout = 300;
        final int segmentAckTimeout = 200 + 50 * ttl;
        long timeout;
        if (outer) {
            // send
            int queueSize;
            synchronized (mNetworkingQueue) {
                queueSize = mNetworkingQueue.size();
            }
            timeout = relayTimeout + segmentAckTimeout + queueSize * netPktSendInterval;
        } else {
            // receive
            timeout = relayTimeout + segmentAckTimeout;
        }
        log("get segment ack timeout: " + timeout);
        return timeout;
    }

    /**
     * calculates the timeout duration for a reliable message in a networking queue.
     *
     * @return timeout
     */
    private long getReliableMessageTimeout() {
        int queueSize;
        synchronized (mNetworkingQueue) {
            queueSize = mNetworkingQueue.size();
        }

        // 960
//        long timeout = 1280 + queueSize * NETWORKING_INTERVAL;

        // for test
//        long timeout = (dleEnabled ? 5120 : 2560) + queueSize * NETWORKING_INTERVAL;
        long timeout = queueSize * netPktSendInterval;
        final MeshMessage meshMessage = mSendingReliableMessage;
        if (meshMessage != null) {
            timeout += meshMessage.getRetryInterval();
        } else {
//            timeout = (dleEnabled ? 2560 : 1280) + ;
            timeout += MeshMessage.DEFAULT_RETRY_INTERVAL;
        }
        log("reliable message timeout:" + timeout);
        return timeout;
    }

    /**
     * used to increase the sequence number by one.
     */
    private void increaseSequenceNumber() {
        int latestValue = mSequenceNumber.incrementAndGet();
        onSequenceNumberUpdate(latestValue);
    }

    /**
     * Sends a list of network layer PDUs over the network.
     *
     * @param networkPduList The list of network layer PDUs to be sent.
     */
    private void sendNetworkPduList(List<NetworkLayerPDU> networkPduList) {
        if (mNetworkingBridge != null) {
            for (NetworkLayerPDU networkLayerPDU : networkPduList) {
                byte[] networkPduPayload = networkLayerPDU.generateEncryptedPayload();
                log("multi network pdu: " + Arrays.bytesToHexString(networkPduPayload, ":"));
                onNetworkingPduPrepared(networkPduPayload, networkLayerPDU.getDst());
            }
        }
    }

    /**
     * Sends a network layer PDU over the network.
     *
     * @param networkPdu network layer PDU to be sent.
     */
    private void sendNetworkPdu(NetworkLayerPDU networkPdu) {
        if (mNetworkingBridge != null) {
            byte[] networkPduPayload = networkPdu.generateEncryptedPayload();
            log("single network pdu: " + Arrays.bytesToHexString(networkPduPayload, ":"));
            onNetworkingPduPrepared(networkPduPayload, networkPdu.getDst());
        }
    }

    /**
     * Sends a proxy network layer PDU over the network.
     *
     * @param networkPdu network layer PDU to be sent.
     */
    private void sendProxyNetworkPdu(ProxyConfigurationPDU networkPdu) {
        if (mNetworkingBridge != null) {
            byte[] networkPduPayload = networkPdu.generateEncryptedPayload();
            log("proxy network pdu: " + Arrays.bytesToHexString(networkPduPayload, ":"));
            mNetworkingBridge.onCommandPrepared(ProxyPDU.TYPE_PROXY_CONFIGURATION, networkPduPayload);
        }
    }

    /**
     * This method is called when a networking PDU (Protocol Data Unit) is prepared for sending.
     * It logs the payload in hexadecimal format and whether the networking is busy or not.
     * <p>
     * If the networking is not busy, it checks if the destination address matches the direct address.
     * If it does, it logs that the networking PDU is being sent directly and calls the onCommandPrepared
     * method of the mNetworkingBridge (if available) with the networking PDU payload.
     * <p>
     * If the networking is busy or the destination address does not match the direct address,
     * it adds the networking PDU payload to the networking queue.
     * <p>
     * After that, it checks if the networking is not busy. If it is not, it sets the networkingBusy
     * flag to true, indicating that the networking is busy, and calls the pollNetworkingQueue method
     * to start sending the networking PDUs from the queue.
     */
    private void onNetworkingPduPrepared(byte[] payload, int dstAddress) {
        log("networking pdu prepared: " + Arrays.bytesToHexString(payload, ":") + " busy?-" + networkingBusy);

        synchronized (mNetworkBusyLock) {
            if (!networkingBusy) {
                boolean directPdu = dstAddress == this.directAddress;
                if (directPdu) {
                    log("networking pdu sending direct ");
                    if (mNetworkingBridge != null) {
                        mNetworkingBridge.onCommandPrepared(ProxyPDU.TYPE_NETWORK_PDU, payload);
                    }
                    return;
                }
            }
        }

        synchronized (mNetworkingQueue) {
            mNetworkingQueue.add(payload);
        }
        synchronized (mNetworkBusyLock) {
            if (!networkingBusy) {
                networkingBusy = true;
                pollNetworkingQueue();
            }
        }
    }


    /**
     * This method is responsible for polling the networking queue and retrieving the next payload to be sent.
     * If there is no payload in the queue, it sets the networkingBusy flag to false.
     * If there is a payload, it logs the payload and sends it to the networking bridge for processing.
     * It also removes any previously scheduled networking sending tasks and schedules a new one with a delay of netPktSendInterval.
     */
    private void pollNetworkingQueue() {
        byte[] payload;
        synchronized (mNetworkingQueue) {
            payload = mNetworkingQueue.poll();
        }
        if (payload == null) {
            log("networking pdu poll: null");
            synchronized (mNetworkBusyLock) {
                networkingBusy = false;
            }
        } else {
            log("networking pdu poll: " + Arrays.bytesToHexString(payload, ":"));
            if (mNetworkingBridge != null) {
                mNetworkingBridge.onCommandPrepared(ProxyPDU.TYPE_NETWORK_PDU, payload);
            }
            mDelayHandler.removeCallbacks(networkingSendingTask);
            mDelayHandler.postDelayed(networkingSendingTask, netPktSendInterval);
        }
    }

    private Runnable networkingSendingTask = new Runnable() {
        @Override
        public void run() {
            pollNetworkingQueue();
        }
    };

    /**
     * seqNo update by step
     * {@link #mSnoUpdateStep}
     *
     * @param latestSequenceNumber latest sequenceNumber
     */
    private void onSequenceNumberUpdate(int latestSequenceNumber) {
        if (mNetworkingBridge != null) {
            if (mSnoUpdateStep == 0 || latestSequenceNumber % mSnoUpdateStep == 0) {
                mNetworkingBridge.onNetworkInfoUpdate(latestSequenceNumber, (int) this.ivIndex);
            }
        }
    }


    /**
     * This method is used to parse a secure network beacon.
     * It takes in a payload, which is the data of the beacon, and a network beacon key, which is used to decrypt the payload.
     *
     * @param payload          payload
     * @param networkBeaconKey key
     */
    public void parseSecureBeacon(byte[] payload, byte[] networkBeaconKey) {
        SecureNetworkBeacon networkBeacon = SecureNetworkBeacon.from(payload, networkBeaconKey);
        // validate beacon data
        if (networkBeacon != null) {
            log("SecureNetworkBeacon received: " + networkBeacon.toString());
            int ivIndex = networkBeacon.getIvIndex();
            boolean isIvUpdating = networkBeacon.isIvUpdating();
            onIvIndexReceived(ivIndex & MeshUtils.UNSIGNED_INTEGER_MAX, isIvUpdating);
        } else {
            log("network beacon parse err");
        }

    }

    /**
     * This method is used to parse a private beacon from a given payload and private beacon key.
     *
     * @param payload          payload
     * @param privateBeaconKey key
     */
    public void parsePrivateBeacon(byte[] payload, byte[] privateBeaconKey) {
        MeshPrivateBeacon privateBeacon = MeshPrivateBeacon.from(payload, privateBeaconKey);
        if (privateBeacon != null) {
            this.privateBeaconReceived = true;
            log("MeshPrivateBeacon received: " + privateBeacon.toString());
            int ivIndex = privateBeacon.getIvIndex();
            boolean isIvUpdating = privateBeacon.isIvUpdating();
            onIvIndexReceived(ivIndex & MeshUtils.UNSIGNED_INTEGER_MAX, isIvUpdating);
        } else {
            log("private beacon parse error");
        }
    }

    /**
     * accepted when received networking pdu
     *
     * @param ivi 1-bit
     * @return ivIndex
     */
    private int getAcceptedIvIndex(int ivi) {
        log(String.format("getAcceptedIvIndex : %08X", ivIndex) + " ivi: " + ivi);
        boolean ivChecked = (ivIndex & 0b01) == ivi;
        return ivChecked ? (int) ivIndex : (int) (ivIndex - 1);
    }

    /**
     * This method returns the transmit IV index.
     * If the IV is not currently updating, the transmit IV index is returned as is.
     * Otherwise, the transmit IV index is decremented by 1. A log message is printed to display the transmit IV index in hexadecimal format. The transmit IV index is then returned.
     *
     * @return ivIndex
     */
    private int getTransmitIvIndex() {
        int re = (int) (!isIvUpdating ? ivIndex : ivIndex - 1);
        log(String.format("getTransmitIvIndex : %08X", re));
        return re;
    }

    /**
     * Sends a MeshBeaconPDU over the network.
     *
     * @param meshBeaconPDU The MeshBeaconPDU to send.
     */
    private void sendMeshBeaconPdu(MeshBeaconPDU meshBeaconPDU) {
        if (mNetworkingBridge != null) {
            mNetworkingBridge.onCommandPrepared(ProxyPDU.TYPE_MESH_BEACON, meshBeaconPDU.toBytes());
        }
    }

    /**
     * parse a network PDU (Protocol Data Unit) payload.
     *
     * @param payload data payload
     */
    public void parseNetworkPdu(byte[] payload) {

        int ivi = (payload[0] & 0xFF) >> 7;
        int ivIndex = getAcceptedIvIndex(ivi);
        NetworkLayerPDU networkLayerPDU = new NetworkLayerPDU(
                new NetworkLayerPDU.NetworkEncryptionSuite(ivIndex, this.encryptionKey, this.privacyKey, this.nid)
        );
        if (networkLayerPDU.parse(payload)) {
            log("network pdu: " + networkLayerPDU.toString());
            if (!validateSequenceNumber(networkLayerPDU, ivIndex)) {
                log("network pdu sequence number check err", MeshLogger.LEVEL_WARN);
                return;
            }
            if (networkLayerPDU.getCtl() == MeshMessage.CTL_ACCESS) {
                parseAccessMessage(networkLayerPDU);
            } else {
                parseControlMessage(networkLayerPDU);
            }

        } else {
            log("network layer parse err", MeshLogger.LEVEL_WARN);
        }
    }

    /**
     * Parses the Proxy Configuration PDU from the given payload.
     *
     * @param payload The payload containing the Proxy Configuration PDU.
     */
    public void parseProxyConfigurationPdu(byte[] payload) {
        // Extract the IVI from the first byte of the payload
        int ivi = (payload[0] & 0xFF) >> 7;

        // Get the accepted IVI from the extracted IVI
        int ivIndex = getAcceptedIvIndex(ivi);
        ProxyConfigurationPDU proxyNetworkPdu = new ProxyConfigurationPDU(
                new NetworkLayerPDU.NetworkEncryptionSuite(ivIndex, this.encryptionKey, this.privacyKey, this.nid)
        );
        if (proxyNetworkPdu.parse(payload)) {
            log("proxy pdu: " + proxyNetworkPdu.toString());
            if (!validateSequenceNumber(proxyNetworkPdu, ivIndex)) {
                log("proxy config pdu sequence number check err", MeshLogger.LEVEL_WARN);
                return;
            }
            log(String.format("proxy network pdu src: %04X dst: %04X", proxyNetworkPdu.getSrc(), proxyNetworkPdu.getDst()));
            onProxyConfigurationNotify(proxyNetworkPdu.getTransportPDU(), proxyNetworkPdu.getSrc());
        }

    }

    /**
     * This method is called when a proxy configuration notification is received.
     * It takes in a byte array representing the proxy configuration message and the source address of the message.
     *
     * @param proxyConfigMessage message data
     * @param src                source address
     */
    private void onProxyConfigurationNotify(byte[] proxyConfigMessage, int src) {
        log("onProxyConfigurationNotify: "
                + Arrays.bytesToHexString(proxyConfigMessage, ":"));
        ProxyFilterStatusMessage proxyFilterStatusMessage = ProxyFilterStatusMessage.fromBytes(proxyConfigMessage);
        if (proxyFilterStatusMessage != null) {

            // target Filter type is whitelist
            if (proxyFilterStatusMessage.getFilterType() == ProxyFilterType.WhiteList.value) {
                if (proxyFilterInitStep < 0) {
                    log("filter init action not started!", MeshLogger.LEVEL_WARN);
                    return;
                }
                log(String.format("reset direct address: %04X", src));
                this.directAddress = src;
                proxyFilterInitStep++;
                if (proxyFilterInitStep == PROXY_FILTER_INIT_STEP_SET_TYPE) {
                    if (this.whiteList == null || this.whiteList.length == 0) {
                        addFilterAddress(new int[]{localAddress, MeshUtils.ADDRESS_BROADCAST});
                    } else {
                        addFilterAddress(this.whiteList);
                    }
                } else if (proxyFilterInitStep == PROXY_FILTER_SET_STEP_ADD_ADR) {
                    onProxyInitComplete(true);
                }
            }

        }
    }

    private Runnable proxyFilterInitTimeoutTask = new Runnable() {
        @Override
        public void run() {
            log("filter init timeout");
            onProxyInitComplete(false);
        }
    };

    /**
     * called when the initialization of a proxy is completed.
     *
     * @param success indicates whether the initialization was successful or not.
     */
    private void onProxyInitComplete(boolean success) {
        proxyFilterInitStep = -1;
        if (success) {
            mDelayHandler.removeCallbacks(proxyFilterInitTimeoutTask);
        }
        if (mNetworkingBridge != null) {
            mNetworkingBridge.onProxyInitComplete(success, this.directAddress);
        }
    }

    /**
     * Validates the sequence number of a received NetworkLayerPDU.
     *
     * @param networkLayerPDU The NetworkLayerPDU to validate.
     * @param pduIvIndex      The IV index of the received PDU.
     * @return True if the sequence number is valid, false otherwise.
     */
    private boolean validateSequenceNumber(NetworkLayerPDU networkLayerPDU, int pduIvIndex) {
        int src = networkLayerPDU.getSrc();
        int pduSequenceNumber = networkLayerPDU.getSeq();
        long valueInCache = this.deviceSequenceNumberMap.get(src, -1);
        long deviceSequenceNumber = getSequenceNumberInCache(valueInCache);
        boolean pass = true;
//        log(String.format("sequence in cache: src--%04X | value--%016X", src, valueInCache));
        if (valueInCache == -1) {
            log("put init sequence number");
            saveSequenceCache(src, pduSequenceNumber, pduIvIndex);
        } else {
            int deviceIvIndex = getIvIndexInCache(valueInCache);
            if (pduIvIndex > deviceIvIndex) {
                log("network pdu - larger ivIndex received, save the new ivIndex");
                saveSequenceCache(src, pduSequenceNumber, pduIvIndex);
            } else if (pduIvIndex == deviceIvIndex) {
                if (pduSequenceNumber > deviceSequenceNumber) {
                    saveSequenceCache(src, pduSequenceNumber, pduIvIndex);
                } else {
                    log(String.format("validate sequence number error  src: %04X -- pdu-sno: %06X -- dev-sno: %06X", src, pduSequenceNumber, deviceSequenceNumber));
                    pass = false;
                }
            } else {
                log("network pdu error: less ivIndex pdu received");
                pass = false;
            }

        }
        return pass;
    }

    /**
     * save sequence number and ivIndex misc value
     */
    private void saveSequenceCache(int src, int sequenceNumber, int ivIndex) {
        long value = ((ivIndex & 0xFFFFFFFFL) << 32) | (sequenceNumber & 0x00FFFFFFL);
//        log(String.format("save sequence in cache src: %04X -- value: %016X", src, value));
//        log(String.format("save sequence detail src: %04X -- ivIndex: %08X -- ivIndex: %08X", src, ivIndex, sequenceNumber));
        this.deviceSequenceNumberMap.put(src, value);
    }

    /**
     * This method extracts the sequence number from a value stored in the cache.
     * The sequence number is represented by the lower 24 bits of the value.
     *
     * @param valInCache the value stored in the cache
     * @return the extracted sequence number
     */
    private long getSequenceNumberInCache(long valInCache) {
        return valInCache & 0xFFFFFF;
    }

    /**
     * This method extracts the IV index from a value stored in the cache.
     * The IV index is represented by bits 32 to 63 of the value.
     *
     * @param valInCache the value stored in the cache
     * @return the extracted IV index
     */
    private int getIvIndexInCache(long valInCache) {
        return (int) ((valInCache >> 32) & 0xFFFFFFFFL);
    }

    /**
     * This method is used to parse a control message received from the network layer.
     * It extracts the lower transport PDU data from the network layer PDU and identifies
     * the segment and opcode of the control message. Based on the segment and opcode,
     * it performs the appropriate action.
     *
     * @param networkLayerPDU The network layer PDU containing the control message.
     */
    private void parseControlMessage(NetworkLayerPDU networkLayerPDU) {
        byte[] lowerTransportPduData = networkLayerPDU.getTransportPDU();
        int segOpcode = lowerTransportPduData[0] & 0xFF;
        int seg = segOpcode >> 7;
        int opcode = segOpcode & 0x7F;
        log("parse control message  seg:" + seg + " -- opcode:" + opcode);
        if (seg == LowerTransportPDU.SEG_TYPE_UNSEGMENTED) {
            if (opcode == TransportControlMessagePDU.CONTROL_MESSAGE_OPCODE_SEG_ACK) {
                SegmentAcknowledgmentMessage segmentAckMessage = new SegmentAcknowledgmentMessage();
                if (segmentAckMessage.parse(lowerTransportPduData)) {
                    onSegmentAckMessageReceived(segmentAckMessage);
                }
            } else if (opcode == TransportControlMessagePDU.CONTROL_MESSAGE_OPCODE_HEARTBEAT) {
                onHeartbeatNotify(networkLayerPDU.getSrc(), networkLayerPDU.getDst(), lowerTransportPduData);
            }
        }
    }

    /**
     * This method is called when a heartbeat control message is received.
     * It logs the received heartbeat message and forwards it to the networking bridge
     * if available.
     *
     * @param src          The source address of the heartbeat message.
     * @param dst          The destination address of the heartbeat message.
     * @param transportPdu The transport PDU containing the heartbeat message.
     */
    private void onHeartbeatNotify(int src, int dst, byte[] transportPdu) {
        log("on heart beat notify: " + Arrays.bytesToHexString(transportPdu, ":"));
        if (mNetworkingBridge != null) {
            mNetworkingBridge.onHeartbeatMessageReceived(src, dst, transportPdu);
        }
    }

    /**
     * when receive Segment Acknowledgment Message
     * check if is segmented message sending,
     * and check blockAck value , if segmented message missing, resend
     */
    private void onSegmentAckMessageReceived(SegmentAcknowledgmentMessage segmentAckMessage) {
        log("onSegmentAckMessageReceived: " + segmentAckMessage.toString());
        if (segmentedBusy) {
            resendSegmentedMessages(segmentAckMessage.getSeqZero(), segmentAckMessage.getBlockAck());
        } else {
            log("Segment Acknowledgment Message err: segmented messages not sending", MeshLogger.LEVEL_WARN);
        }
    }

    /**
     * @param seqZero  valued by block ack message or -1 when not received any block ack message;
     *                 so if seqZero is -1, resend all segmented messages
     * @param blockAck valued by block ack message showing missing segmented messages or 0 when not received any block ack message
     */
    private void resendSegmentedMessages(int seqZero, int blockAck) {
        final SparseArray<SegmentedAccessMessagePDU> messageBuffer = sentSegmentedMessageBuffer.clone();
        log("resendSegmentedMessages: seqZero: " + seqZero
                + " block ack: " + blockAck
                + " buffer size: " + messageBuffer.size());
        if (messageBuffer.size() != 0) {
            SegmentedAccessMessagePDU message0 = messageBuffer.get(messageBuffer.keyAt(0));
            int messageSeqZero = message0.getSeqZero();

            if (seqZero != -1) {
                if (seqZero == messageSeqZero) {
                    stopSegmentedBlockAckWaiting(false, false);
                } else {
                    return;
                }
            }


            int ctl = mSegmentBlockWaitingTask.ctl;
            int ttl = mSegmentBlockWaitingTask.ttl;
            int src = mSegmentBlockWaitingTask.src;
            int dst = mSegmentBlockWaitingTask.dst;

//            int blockAck = segmentAckMessage.getBlockAck();
            int messageSegN = message0.getSegN();
            boolean messageReceived;
            SegmentedAccessMessagePDU messagePDU;
            int ivIndex = getTransmitIvIndex();
            int sequenceNumber = mSequenceNumber.get();
            int addedValue = 0;
            List<NetworkLayerPDU> networkLayerPduList = new ArrayList<>();
            for (int i = 0; i <= messageSegN; i++) {
                messageReceived = (blockAck & MeshUtils.bit(i)) != 0;
                if (!messageReceived) {
                    // message miss
                    messagePDU = messageBuffer.get(i);
                    byte[] lowerTransportPdu = messagePDU.toByteArray();
                    log("resend segmented message: seqZero:" + messagePDU.getSeqZero() + " -- segO:" + messagePDU.getSegO());
                    NetworkLayerPDU networkPDU = createNetworkPDU(lowerTransportPdu,
                            ctl, ttl, src, dst, ivIndex, sequenceNumber + addedValue);
                    addedValue++;
                    networkLayerPduList.add(networkPDU);
                }
            }

            if (networkLayerPduList.size() == 0) {
                // all received
                stopSegmentedBlockAckWaiting(true, true);
            } else {
                startSegmentedBlockAckWaiting(ctl, ttl, src, dst);
                sendNetworkPduList(networkLayerPduList);
            }

        }


    }


    /**
     * parse lower transport pdu
     */
    private void parseAccessMessage(NetworkLayerPDU networkLayerPDU) {
        log("parse access message");
        int src = networkLayerPDU.getSrc();
        int dst = networkLayerPDU.getDst();

        if (MeshUtils.validUnicastAddress(dst)) {
            if (dst != localAddress) {
                return;
            }
        }

        byte[] lowerTransportData = networkLayerPDU.getTransportPDU();

        byte lowerTransportHeader = lowerTransportData[0];
        int seg = (lowerTransportHeader >> 7) & 0b01;
        AccessLayerPDU accessPDU;
        if (seg == 1) {
            log("parse segmented access message");

            /*
             * tick refresh if received segment busy
             */
            /*if (reliableBusy) {
                log("refresh reliable tick because of segment network pdu received");
                restartReliableMessageTimeoutTask();
            }*/
            accessPDU = parseSegmentedAccessMessage(networkLayerPDU);

        } else {
            log("parse unsegmented access message");
            accessPDU = parseUnsegmentedAccessMessage(networkLayerPDU);
        }

        if (accessPDU != null) {
            onAccessPduReceived(src, dst, accessPDU);
        }
    }


    /**
     * refresh reliable message status, then invoke message callback
     */
    private void onAccessPduReceived(int src, int dst, AccessLayerPDU accessPDU) {

        log(String.format("access pdu received at 0x%04X: opcode -- 0x%04X", src, accessPDU.opcode)
                + " params -- " + Arrays.bytesToHexString(accessPDU.params, ""));
        // check reliable message state
        updateReliableMessage(src, accessPDU);
        if (mNetworkingBridge != null) {
            mNetworkingBridge.onMeshMessageReceived(src, dst, accessPDU.opcode, accessPDU.params);
        }
    }

    /**
     * This method is used to update the reliable message status when a response is received from a device.
     *
     * @param src            source address
     * @param accessLayerPDU access pdu
     */
    private void updateReliableMessage(int src, AccessLayerPDU accessLayerPDU) {
        if (!reliableBusy) return;
        MeshMessage sendingMessage = mSendingReliableMessage;
        if (sendingMessage != null && sendingMessage.getResponseOpcode() == accessLayerPDU.opcode) {
            int sendingDst = sendingMessage.getDestinationAddress();
            if (MeshUtils.validUnicastAddress(sendingDst) && sendingDst != src) {
                log(String.format("not expected response : %04X - %04X", sendingDst, src));
                return;
            }
            mResponseMessageBuffer.add(src);
            if (mResponseMessageBuffer.size() >= sendingMessage.getResponseMax()) {
                onReliableMessageComplete(true);
            }
        }
    }

    /**
     * reliable command complete
     *
     * @param success if command response received
     */
    private void onReliableMessageComplete(boolean success) {

        // clear networking packet sending queue
        log("clear network buffer");
        synchronized (mNetworkingQueue) {
            mDelayHandler.removeCallbacks(networkingSendingTask);
            networkingBusy = false;
            mNetworkingQueue.clear();
        }

        mDelayHandler.removeCallbacks(reliableMessageTimeoutTask);
        int opcode = mSendingReliableMessage.getOpcode();
        int rspMax = mSendingReliableMessage.getResponseMax();
        int rspCount = mResponseMessageBuffer.size();
        log(String.format("Reliable Message Complete: %06X success?: %b", opcode, success));
        mResponseMessageBuffer.clear();
        synchronized (RELIABLE_SEGMENTED_LOCK) {
            reliableBusy = false;
            if (success) {
                if (segmentedBusy && mSendingReliableMessage.isSegmented()) {
                    segmentedBusy = false;
                    stopSegmentedBlockAckWaiting(true, true);
//                    mDelayHandler.removeCallbacks(mSegmentBlockWaitingTask);
                }
            }
        }


        if (mNetworkingBridge != null) {
            mNetworkingBridge.onReliableMessageComplete(success, opcode, rspMax, rspCount);
        }
    }

    /**
     * start or refresh tick
     */
    private void restartReliableMessageTimeoutTask() {
        log("restart reliable message timeout task, immediate");
        mDelayHandler.removeCallbacks(reliableMessageTimeoutTask);
        mDelayHandler.postDelayed(reliableMessageTimeoutTask, getReliableMessageTimeout());
    }

    private Runnable reliableMessageTimeoutTask = new Runnable() {
        @Override
        public void run() {
            final MeshMessage meshMessage = mSendingReliableMessage;
            if (meshMessage != null) {
                log(String.format(Locale.getDefault(), "reliable message retry segmentRxComplete? %B retryCnt: %d %s opcode: %06X", lastSegComplete, meshMessage.getRetryCnt(), meshMessage.getClass().getSimpleName(), meshMessage.getOpcode()));
                if (lastSegComplete) {
                    if (meshMessage.getRetryCnt() <= 0) {
                        onReliableMessageComplete(false);
                    } else {
                        // resend mesh message
                        meshMessage.setRetryCnt(meshMessage.getRetryCnt() - 1);
                        synchronized (RELIABLE_SEGMENTED_LOCK) {
                            reliableBusy = false;
                            if (segmentedBusy && meshMessage.isSegmented()) {
                                stopSegmentedBlockAckWaiting(true, false);
                            }
                        }
                        postMeshMessage(meshMessage, true);
                    }
                } else {
                    // receiving rx segment packet
                    restartReliableMessageTimeoutTask();
                }
            }
        }
    };


    // parse unsegmented access message lower transport PDU

    /**
     * This method is used to parse an unsegmented access message from a network layer PDU.
     * It first retrieves the lower transport data from the network layer PDU and extracts the header.
     * The Access Key Flag (AKF) and the IV Index are then extracted from the header.
     * An instance of the UnsegmentedAccessMessagePDU is created and the network layer PDU is parsed using it.
     * If the parsing is successful, an UpperTransportAccessPDU.UpperTransportEncryptionSuite is created based on the AKF value.
     * If AKF is equal to the AKF value of the Access Type DEVICE, the device key is used along with the IV Index.
     * Otherwise, a list of application keys is retrieved and used along with the IV Index.
     * An instance of UpperTransportAccessPDU is created with the encryption suite.
     * The unsegmented access message is then parsed and decrypted using the UpperTransportAccessPDU.
     * If the decryption is successful, the decrypted payload is parsed as an AccessLayerPDU and returned.
     * If any errors occur during the parsing or decryption process, null is returned.
     *
     * @param networkLayerPDU network pdu
     * @return access pdu
     */
    private AccessLayerPDU parseUnsegmentedAccessMessage(NetworkLayerPDU networkLayerPDU) {
        byte[] lowerTransportData = networkLayerPDU.getTransportPDU();
        byte header = lowerTransportData[0]; //Lower transport pdu starts here
        int akf = (header >> 6) & 0x01;

        int ivIndex = networkLayerPDU.encryptionSuite.ivIndex;

        UnsegmentedAccessMessagePDU unsegmentedAccessMessagePDU = new UnsegmentedAccessMessagePDU();
        if (unsegmentedAccessMessagePDU.parse(networkLayerPDU)) {

            UpperTransportAccessPDU.UpperTransportEncryptionSuite upperTransportEncryptionSuite;
            if (AccessType.DEVICE.akf == akf) {
                upperTransportEncryptionSuite = new UpperTransportAccessPDU.UpperTransportEncryptionSuite(getDeviceKey(networkLayerPDU.getSrc()), ivIndex);
            } else {
                List<byte[]> appKeyList = getAppKeyList();
                upperTransportEncryptionSuite = new UpperTransportAccessPDU.UpperTransportEncryptionSuite(appKeyList, ivIndex);
            }

            UpperTransportAccessPDU upperTransportAccessPDU = new UpperTransportAccessPDU(upperTransportEncryptionSuite);
            boolean decRe = upperTransportAccessPDU.parseAndDecryptUnsegmentedMessage(unsegmentedAccessMessagePDU, networkLayerPDU.getSeq(), networkLayerPDU.getSrc(), networkLayerPDU.getDst());
            if (decRe) {
                return AccessLayerPDU.parse(upperTransportAccessPDU.getDecryptedPayload());
            } else {
                log("unsegmented access message parse err", MeshLogger.LEVEL_WARN);
            }
        }
        return null;
    }

    /**
     * retrieves a list of app keys stored in the appKeyMap.
     *
     * @return app key list
     */
    private List<byte[]> getAppKeyList() {
        if (this.appKeyMap != null && this.appKeyMap.size() != 0) {
            List<byte[]> appKeyList = new ArrayList<>();
            for (int i = 0; i < appKeyMap.size(); i++) {
                appKeyList.add(appKeyMap.get(appKeyMap.keyAt(i)));
            }
            return appKeyList;
        }
        return null;
    }

    /**
     * This is a method that checks whether a segment block needs to be stopped or restarted based on the parameters passed.
     * If the parameter "immediate" is true, it stops the segment timeout task, otherwise, it restarts it.
     * The method also removes any pending callbacks for the "mAccessSegCheckTask" task and sets a new timeout based on the "ttl" parameter.
     * Finally, it posts a delayed task to the "mDelayHandler" with the "mAccessSegCheckTask" and the calculated timeout.
     *
     * @param immediate immediate
     * @param ttl       ttl
     * @param src       src
     */
    private void checkSegmentBlock(boolean immediate, int ttl, int src) {
        if (immediate) {
            stopSegmentTimeoutTask();
        } else {
            restartSegmentTimeoutTask();
        }
        mDelayHandler.removeCallbacks(mAccessSegCheckTask);
        long timeout = immediate ? 0 : getSegmentedTimeout(ttl, false);
        mAccessSegCheckTask.src = src;
        mAccessSegCheckTask.ttl = ttl;
        log("check segment block: immediate-" + immediate + " ttl-" + ttl + " src-" + src + " timeout-" + timeout);
        mDelayHandler.postDelayed(mAccessSegCheckTask, timeout);
    }

    /**
     * Stop a task that checks for segment block acknowledgments.
     * It removes any pending callbacks from the delay handler associated with the task.
     */
    private void stopSegmentBlockAckTask() {
        mDelayHandler.removeCallbacks(mAccessSegCheckTask);
    }

    /**
     * This method is used to send a segment block acknowledgment message to a specific source with a given time-to-live (TTL) value.
     * It first clones the received segmented message buffer and checks if there are any messages in it.
     * If there are, it calculates the sequence number (seqZero) and block acknowledgment value (blockAck) for each message and creates a SegmentAcknowledgmentMessage object.
     * It then sends this message to the source using the sendSegmentAckMessage() method.
     * If all the segments have not been received yet, it sets a delay for checking the status of the segmented message transmission using the mAccessSegCheckTask.
     * This method is used in a communication protocol to ensure that all segments of a message are received correctly.
     *
     * @param src source
     * @param ttl ttl
     */
    private void sendSegmentBlockAck(int src, int ttl) {
        log("send segment block ack:" + src);
        final SparseArray<SegmentedAccessMessagePDU> messages = receivedSegmentedMessageBuffer.clone();
        if (messages.size() > 0) {
//            int segN = -1;
            int seqZero = -1;
            int blockAck = 0;
            int segO;
            int segN = -1;
            SegmentedAccessMessagePDU message;
            for (int i = 0; i < messages.size(); i++) {
                segO = messages.keyAt(i);
                message = messages.get(segO);
                if (segN == -1) {
                    segN = message.getSegN();
                }
                if (seqZero == -1) {
                    seqZero = message.getSeqZero();
                }
                blockAck |= (1 << segO);
            }

            SegmentAcknowledgmentMessage segmentAckMessage = new SegmentAcknowledgmentMessage(seqZero, blockAck);
            sendSegmentAckMessage(segmentAckMessage, src);

            boolean complete = messages.size() == (segN + 1);
            if (!complete) {
                mDelayHandler.removeCallbacks(mAccessSegCheckTask);
                long timeout = getSegmentedTimeout(ttl, false);
                mDelayHandler.postDelayed(mAccessSegCheckTask, timeout);
            }
        }
    }

    /**
     * send segment busy
     * send a busy acknowledgment message for a segment block.
     *
     * @param src     the source of the acknowledgment
     * @param seqZero the sequence number of the segment block
     * @param seqAuth the sequence authentication value
     */
    private void sendSegmentBlockBusyAck(int src, int seqZero, long seqAuth) {
        log("send segment block busy ack:" + src);
        saveBusySeqAuth(src, seqAuth);
        SegmentAcknowledgmentMessage segmentAckMessage = new SegmentAcknowledgmentMessage(seqZero, 0);
        sendSegmentAckMessage(segmentAckMessage, src);
    }

    /**
     * send a segment acknowledgment message to a specific destination.
     *
     * @param segmentAcknowledgmentMessage message
     * @param dst                          dst
     */
    private void sendSegmentAckMessage(SegmentAcknowledgmentMessage segmentAcknowledgmentMessage, int dst) {
        log("send segment ack: " + segmentAcknowledgmentMessage.toString());
        sendUnsegmentedControlMessage(segmentAcknowledgmentMessage, dst);
    }

    /**
     * send an unsegmented control message.
     * It takes a control message PDU as input and converts it to a byte array.
     *
     * @param controlMessagePDU control message pdu
     * @param dst               dst
     */
    private void sendUnsegmentedControlMessage(UnsegmentedControlMessagePDU controlMessagePDU, int dst) {
        byte[] data = controlMessagePDU.toByteArray();
        log("send control message: " + Arrays.bytesToHexString(data, ""));
        int ctl = MeshMessage.CTL_CONTROL;
        int ttl = 5;
        int src = localAddress;
        int ivIndex = getTransmitIvIndex();
        NetworkLayerPDU networkPDU = createNetworkPDU(data, ctl, ttl, src, dst, ivIndex, mSequenceNumber.get());
        sendNetworkPdu(networkPDU);
    }

    /**
     * not receive any segment with current segAuth
     */
    private static final long SEG_TIMEOUT = 10 * 1000;
    private Runnable segmentTimeoutTask = new Runnable() {
        @Override
        public void run() {
            stopSegmentBlockAckTask();
            log(String.format(Locale.getDefault(), "segment timeout : lastSeqAuth: 0x%014X -- src: %02d",
                    lastSeqAuth,
                    lastSegSrc));
            lastSegComplete = true;
            lastSegSrc = 0;
            lastSeqAuth = 0;
        }
    };

    /**
     * restart segment timer
     */
    private void restartSegmentTimeoutTask() {
        mDelayHandler.removeCallbacks(segmentTimeoutTask);
        mDelayHandler.postDelayed(segmentTimeoutTask, SEG_TIMEOUT);
    }

    /**
     * stop segment timer
     */
    private void stopSegmentTimeoutTask() {
        mDelayHandler.removeCallbacks(segmentTimeoutTask);
    }

    /**
     * send a segment acknowledgment message to a specified source with information
     *
     * @param src     src
     * @param segN    segN
     * @param seqZero seqZero
     */
    private void sendSegmentCompleteBlockAck(int src, int segN, int seqZero) {
        int blockAck = 0;
        for (int i = 0; i < segN + 1; i++) {
            blockAck |= (1 << i);
        }
        SegmentAcknowledgmentMessage segmentAckMessage = new SegmentAcknowledgmentMessage(seqZero, blockAck);
        sendSegmentAckMessage(segmentAckMessage, src);
    }

    /**
     * parse segmented access message
     * check auth
     */
    /**
     * This method is used to parse a segmented access message received from the network layer.
     * It extracts the necessary information from the network layer PDU and constructs a segmented access message PDU.
     * It then performs various checks and operations to ensure the integrity and completeness of the segmented message.
     * Finally, it decrypts the segmented message and returns the access layer PDU.
     *
     * @param networkLayerPDU The network layer PDU containing the segmented access message.
     * @return The access layer PDU decrypted from the segmented access message, or null if there was an error.
     */
    private AccessLayerPDU parseSegmentedAccessMessage(NetworkLayerPDU networkLayerPDU) {
        // Create a new segmented access message PDU and parse the network layer PDU into it
        SegmentedAccessMessagePDU message = new SegmentedAccessMessagePDU();
        message.parse(networkLayerPDU);
        // Extract necessary information from the network layer PDU
        final int src = networkLayerPDU.getSrc();
        int ttl = networkLayerPDU.getTtl() & 0xFF;
        int sequenceNumber = networkLayerPDU.getSeq();

        int seqLowerBitValue = sequenceNumber & SEQ_ZERO_LIMIT;

        int seqZero = message.getSeqZero();

        int seqHigherBitValue;
        // Calculate the higher bit value of the sequence number based on the lower bit value and the seqZero value
        if (seqLowerBitValue < seqZero) {
            seqHigherBitValue = (sequenceNumber - (SEQ_ZERO_LIMIT + 1)) & 0xFFE000;
        } else {
            seqHigherBitValue = sequenceNumber & 0xFFE000;
        }

        // Calculate the transport sequence number by combining the higher bit value and the seqZero value
        // sequence number of first segmented message
        int transportSeqNo = seqHigherBitValue | seqZero;
        int ivIndex = networkLayerPDU.encryptionSuite.ivIndex;
        // seq auth:   ivIndex(32bits) | seqNo(11bits) | seqZero(13bits)
        // 0x7FFFFFFFL remove highest bit
        // Calculate the seqAuth value for authentication
        long seqAuth = (transportSeqNo & 0xFFFFFFL) | ((ivIndex & 0x7FFFFFFFL) << 24);

        // Extract the segO and segN values from the segmented access message PDU
        int segO = message.getSegO();
        int segN = message.getSegN();

        log(String.format(Locale.getDefault(), "lastComplete? :%B -- seqAuth: 0x%014X -- lastSeqAuth: 0x%014X -- src: 0x%04X -- lastSrc: 0x%04X -- seg0: %02d -- segN: %02d",
                lastSegComplete,
                seqAuth,
                lastSeqAuth,
                src,
                lastSegSrc,
                segO,
                segN));

        if (isBusyAuthExists(src, seqAuth)) {
            log("busy auth exists");
            sendSegmentBlockBusyAck(src, seqZero, seqAuth);
            return null;
        }

        if (isCompleteAuthExists(src, seqAuth)) {
            log("complete auth exists");
            sendSegmentCompleteBlockAck(src, segN, seqZero);
            return null;
        }

        AccessLayerPDU accessPDU = null;

        // Check if the current seqAuth and source values are different from the last ones
        if (seqAuth != lastSeqAuth || lastSegSrc != src) {
            if (lastSegComplete) {
                log("last segment complete");
                // save last seqAuth
                saveCompletedSeqAuth(lastSegSrc, lastSeqAuth);
                lastSegComplete = false;
                // new segment message
                lastSeqAuth = seqAuth;
                lastSegSrc = src;
                receivedSegmentedMessageBuffer.clear();
            } else {
                sendSegmentBlockBusyAck(src, seqZero, seqAuth);
                return null;
            }
        }

       /* if (seqAuth != lastSeqAuth || lastSegSrc != src) {
            if (lastSegComplete) {
                log("last segment complete");
                // save last seqAuth
                saveCompletedSeqAuth(lastSegSrc, lastSeqAuth);
                lastSegComplete = false;
                // new segment message
                lastSeqAuth = seqAuth;
                lastSegSrc = src;
                receivedSegmentedMessageBuffer.clear();
                receivedSegmentedMessageBuffer.put(segO, message);
                checkSegmentBlock(false, ttl, src);
            } else {
                sendSegmentBlockBusyAck(src, seqZero, seqAuth);
            }
        } else*/
        {
            receivedSegmentedMessageBuffer.put(segO, message);

            int messageCnt = receivedSegmentedMessageBuffer.size();
            log("received segment message count: " + messageCnt);

            if (messageCnt != segN + 1) {
                lastSeqAuth = seqAuth;
                checkSegmentBlock(false, ttl, src);
            } else {
                lastSegComplete = true;
                checkSegmentBlock(true, ttl, src);
                if (isCompleteAuthExists(src, seqAuth)) {
                    log(" seqAuth already received: " + seqAuth);
                    lastSeqAuth = 0;
                    return null;
                }
                UpperTransportAccessPDU.UpperTransportEncryptionSuite encryptionSuite;
                int akf = message.getAkf();
                if (akf == AccessType.APPLICATION.akf) {
                    encryptionSuite = new UpperTransportAccessPDU.UpperTransportEncryptionSuite(getAppKeyList(), ivIndex);
                } else {
                    byte[] deviceKey = getDeviceKey(src);
                    if (deviceKey == null) {
                        log("Device key not found when decrypt segmented access message", MeshLogger.LEVEL_WARN);
                        return null;
                    }
                    encryptionSuite = new UpperTransportAccessPDU.UpperTransportEncryptionSuite(deviceKey, ivIndex);
                }

                UpperTransportAccessPDU upperTransportAccessPDU = new UpperTransportAccessPDU(encryptionSuite);
                upperTransportAccessPDU.parseAndDecryptSegmentedMessage(receivedSegmentedMessageBuffer.clone(), transportSeqNo, src, networkLayerPDU.getDst());

                byte[] completeTransportPdu = upperTransportAccessPDU.getDecryptedPayload();

                log("decrypted upper: " + Arrays.bytesToHexString(completeTransportPdu, ""));
                if (completeTransportPdu != null) {
                    accessPDU = AccessLayerPDU.parse(completeTransportPdu);
                } else {
                    log("upper pdu decryption error: ", MeshLogger.LEVEL_WARN);
                }
            }
        }
        return accessPDU;
    }

    /**
     * Creates an UpperTransportAccessPDU object with the given parameters.
     *
     * @param accessPDU  The Access PDU to be encrypted.
     * @param key        The encryption key.
     * @param szmic      The value indicating the size of the Message Integrity Check (MIC).
     * @param accessType The type of access.
     * @param ivIndex    The IV index.
     * @param seqNo      The sequence number.
     * @param src        The source address.
     * @param dst        The destination address.
     * @return The created UpperTransportAccessPDU object, or null if encryption fails.
     */
    private UpperTransportAccessPDU createUpperTransportAccessPDU(byte[] accessPDU, byte[] key, byte szmic, AccessType accessType, int ivIndex, int seqNo, int src, int dst) {

        UpperTransportAccessPDU.UpperTransportEncryptionSuite encryptionSuite;


        if (accessType == AccessType.APPLICATION) {
            List<byte[]> appKeyList = new ArrayList<>();
            appKeyList.add(key);
            encryptionSuite = new UpperTransportAccessPDU.UpperTransportEncryptionSuite(appKeyList, ivIndex);
        } else {
            encryptionSuite = new UpperTransportAccessPDU.UpperTransportEncryptionSuite(key, ivIndex);
        }
        UpperTransportAccessPDU upperTransportAccessPDU =
                new UpperTransportAccessPDU(encryptionSuite);
        if (upperTransportAccessPDU.encrypt(accessPDU, szmic, accessType, seqNo, src, dst)) {
            return upperTransportAccessPDU;
        } else {
            return null;
        }


    }


    /*private SparseArray<LowerTransportPDU> createLowerTransportPDU(byte[] upperTransportPDU, byte akf, byte aid, int aszmic, int seqNo) {
        SparseArray<LowerTransportPDU> lowerTransportPduMap;
        if (upperTransportPDU.length <= UNSEGMENTED_TRANSPORT_PAYLOAD_MAX_LENGTH) {
            LowerTransportPDU lowerTransportPDU = createUnsegmentedAccessMessage(upperTransportPDU, akf, aid);
            lowerTransportPduMap = new SparseArray<>();
            lowerTransportPduMap.put(0, lowerTransportPDU);
        } else {
            lowerTransportPduMap = createSegmentedAccessMessage(upperTransportPDU, akf, aid, aszmic, seqNo);
        }
        return lowerTransportPduMap;
    }*/

    /**
     * Creates segmented access message PDUs for a given encrypted upper transport PDU.
     *
     * @param encryptedUpperTransportPDU The encrypted upper transport PDU.
     * @param akf                        The Application Key Flag.
     * @param aid                        The Application Identifier.
     * @param aszmic                     The value indicating whether the message integrity check is required.
     * @param sequenceNumber             The sequence number of the message.
     * @param segmentLen                 The length of each segment.
     * @return A SparseArray containing the segmented access message PDUs.
     */
    private SparseArray<SegmentedAccessMessagePDU> createSegmentedAccessMessage(byte[] encryptedUpperTransportPDU, byte akf, byte aid, int aszmic, int sequenceNumber, int segmentLen) {

        final int segmentedAccessLen = segmentLen + 1;
        byte[] seqNoBuffer = MeshUtils.integer2Bytes(sequenceNumber, 3, ByteOrder.BIG_ENDIAN);
        // 13 lowest bits
        int seqZero = ((seqNoBuffer[1] & 0x1F) << 8) | (seqNoBuffer[2] & 0xFF);

        // segment pdu number
        int segNum = (int) Math.ceil(((double) encryptedUpperTransportPDU.length) / segmentedAccessLen); // SEGMENTED_ACCESS_PAYLOAD_MAX_LENGTH
        int segN = segNum - 1; // index from 0
        log("create segmented access message: seqZero - " + seqZero + " segN - " + segN);

        SparseArray<SegmentedAccessMessagePDU> lowerTransportPDUArray = new SparseArray<>();
        int offset = 0;
        int segmentedLength;
        SegmentedAccessMessagePDU lowerTransportPDU;
        for (int segOffset = 0; segOffset < segNum; segOffset++) {
            segmentedLength = Math.min(encryptedUpperTransportPDU.length - offset, segmentedAccessLen);
            lowerTransportPDU = new SegmentedAccessMessagePDU();
            lowerTransportPDU.setAkf(akf);
            lowerTransportPDU.setAid(aid);
            lowerTransportPDU.setSzmic(aszmic);
            lowerTransportPDU.setSeqZero(seqZero);
            lowerTransportPDU.setSegO(segOffset);
            lowerTransportPDU.setSegN(segN);
            lowerTransportPDU.setSegmentM(ByteBuffer.allocate(segmentedLength).put(encryptedUpperTransportPDU, offset, segmentedLength).array());
            offset += segmentedLength;
            lowerTransportPDUArray.put(segOffset, lowerTransportPDU);

        }
        return lowerTransportPDUArray;
    }


    /**
     * Creates an unsegmented access message PDU for a given upper transport PDU.
     *
     * @param upperTransportPDU The upper transport PDU.
     * @param akf               The Application Key Flag.
     * @param aid               The Application Identifier.
     * @return The unsegmented access message PDU.
     */
    private UnsegmentedAccessMessagePDU createUnsegmentedAccessMessage(byte[] upperTransportPDU, byte akf, byte aid) {
        return new UnsegmentedAccessMessagePDU(akf, aid, upperTransportPDU);
    }

    /**
     * Creates a network layer PDU for a given transport PDU and other parameters.
     *
     * @param transportPdu   The transport PDU.
     * @param ctl            The Control field value.
     * @param ttl            The Time To Live value.
     * @param src            The source address.
     * @param dst            The destination address.
     * @param ivIndex        The IV Index value.
     * @param sequenceNumber The sequence number of the message.
     * @return The network layer PDU.
     */
    private NetworkLayerPDU createNetworkPDU(byte[] transportPdu,
                                             int ctl, int ttl, int src, int dst, int ivIndex, int sequenceNumber) {
        NetworkLayerPDU networkLayerPDU = new NetworkLayerPDU(
                new NetworkLayerPDU.NetworkEncryptionSuite(ivIndex, this.encryptionKey, this.privacyKey, this.nid)
        );
        networkLayerPDU.setIvi((byte) (ivIndex & 0x01));
        networkLayerPDU.setNid(this.nid);
        networkLayerPDU.setCtl((byte) ctl);
        networkLayerPDU.setTtl((byte) ttl);
        networkLayerPDU.setSeq(sequenceNumber);
        networkLayerPDU.setSrc(src);
        networkLayerPDU.setDst(dst);
        networkLayerPDU.setTransportPDU(transportPdu);

        // for every network pdu , sequence number should increase
        increaseSequenceNumber();
        return networkLayerPDU;
    }

    /**
     * Creates a proxy configuration PDU for a given transport PDU and other parameters.
     *
     * @param transportPdu   The transport PDU.
     * @param src            The source address.
     * @param ivIndex        The IV Index value.
     * @param sequenceNumber The sequence number of the message.
     * @return The proxy configuration PDU.
     */
    private ProxyConfigurationPDU createProxyConfigurationPdu(byte[] transportPdu, int src, int ivIndex, int sequenceNumber) {
        ProxyConfigurationPDU networkLayerPDU = new ProxyConfigurationPDU(
                new NetworkLayerPDU.NetworkEncryptionSuite(ivIndex, this.encryptionKey, this.privacyKey, this.nid)
        );
        networkLayerPDU.setIvi((byte) (ivIndex & 0x01));
        networkLayerPDU.setNid(this.nid);
        networkLayerPDU.setCtl(ProxyConfigurationPDU.ctl);
        networkLayerPDU.setTtl(ProxyConfigurationPDU.ttl);
        networkLayerPDU.setSeq(sequenceNumber);
        networkLayerPDU.setSrc(src);
        networkLayerPDU.setDst(ProxyConfigurationPDU.dst);
        networkLayerPDU.setTransportPDU(transportPdu);

        // for every network pdu , sequence number should increase
        increaseSequenceNumber();
        return networkLayerPDU;
    }

    /**
     * Segment ack message task
     */
    private class SegmentAckMessageSentTask implements Runnable {
        private int src;
        private int ttl;

        @Override
        public void run() {
            sendSegmentBlockAck(src, ttl);
        }
    }

    /**
     * Segment ack message timeout task
     */
    private class SegmentedMessageTimeoutTask implements Runnable {
        @Override
        public void run() {
            log("segmented message timeout");
            stopSegmentedBlockAckWaiting(true, false);
        }
    }

    /**
     * This class represents a task that is executed in a separate thread.
     */
    private class SegmentBlockWaitingTask implements Runnable {
        private int ctl;
        private int ttl;
        private int src;
        private int dst;

        /**
         * set the values of these parameters
         *
         * @param ctl ctl
         * @param ttl ttl
         * @param src src
         * @param dst dst
         */
        public void resetParams(int ctl, int ttl, int src, int dst) {
            this.ctl = ctl;
            this.ttl = ttl;
            this.src = src;
            this.dst = dst;
        }

        /**
         * entry point for the task and it calls the resendSegmentedMessages method with specific arguments.
         */
        @Override
        public void run() {
            resendSegmentedMessages(-1, 0);
        }
    }


    /**
     * log using DEBUG level
     *
     * @param logMessage log message
     */
    private void log(String logMessage) {
        log(logMessage, MeshLogger.LEVEL_DEBUG);
    }

    /**
     * log using custom level
     *
     * @param logMessage log message
     * @param level      level
     */
    private void log(String logMessage, int level) {
        MeshLogger.log(logMessage, LOG_TAG, level);
    }
}
