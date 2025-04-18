/********************************************************************************************************
 * @file SceneGetMessage.java
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
package com.telink.ble.mesh.core.message.scene;

import com.telink.ble.mesh.core.message.Opcode;
import com.telink.ble.mesh.core.message.lighting.LightingMessage;

/**
 * Created by kee on 2019/9/19.
 */

/**
 * This class represents a SceneGetMessage.
 * It is used to request the status of a scene from a destination address using a specific application key index.
 * This message can be used to get information about a scene.
 */
public class SceneGetMessage extends LightingMessage {

    /**
     * Creates a new SceneGetMessage with the given destination address and application key index.
     *
     * @param destinationAddress The destination address to send the message to.
     * @param appKeyIndex The index of the application key to use for the message.
     */
    public SceneGetMessage(int destinationAddress, int appKeyIndex) {
        super(destinationAddress, appKeyIndex);
    }

    /**
     * Returns the opcode value for the SceneGetMessage, which is Opcode.SCENE_GET.
     *
     * @return The opcode value for the SceneGetMessage.
     */
    @Override
    public int getOpcode() {
        return Opcode.SCENE_GET.value;
    }

    /**
     * Returns the response opcode value for the SceneGetMessage, which is Opcode.SCENE_STATUS.
     *
     * @return The response opcode value for the SceneGetMessage.
     */
    @Override
    public int getResponseOpcode() {
        return Opcode.SCENE_STATUS.value;
    }

    /**
     * Creates and returns a simple SceneGetMessage with the given destination address, application key index, and response maximum value.
     *
     * @param destinationAddress The destination address to send the message to.
     * @param appKeyIndex The index of the application key to use for the message.
     * @param rspMax The maximum number of responses to request.
     * @return A simple SceneGetMessage with the given parameters.
     */
    public static SceneGetMessage getSimple(int destinationAddress, int appKeyIndex, int rspMax) {
        SceneGetMessage message = new SceneGetMessage(destinationAddress, appKeyIndex);
        message.setResponseMax(rspMax);
        return message;
    }
}
