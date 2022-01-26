/* **************************************************************************************
 * Copyright (c) 2021 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.calypsonet.keyple.plugin.bluebird.reader

import android.os.Handler
import android.os.Looper
import android.os.Message
import com.bluebird.payment.sam.SamInterface
import kotlinx.coroutines.channels.Channel
import org.eclipse.keyple.core.util.ByteArrayUtil

class BluebirdSamHandler : Handler(Looper.getMainLooper()) {

    var initDone: Boolean = false
    var initChannel: Channel<ByteArray?>? = null
    var responseChannel: Channel<ByteArray?>? = null

    override fun handleMessage(msg: Message) {
        if (msg.what == SamInterface.SAM_DATA_RECEIVED_MSG_INT) {

            val samData = msg.data.getByteArray("receive")

            initChannel?.let {
                val atrString: String = ByteArrayUtil.toHex(samData)
                initDone = atrString.startsWith("3B 3F") && atrString.endsWith("90 00")
                val atr = if (initDone) {
                    samData
                } else {
                    null
                }
                it.offer(atr)
            }
            responseChannel?.offer(samData)
        }
    }
}
