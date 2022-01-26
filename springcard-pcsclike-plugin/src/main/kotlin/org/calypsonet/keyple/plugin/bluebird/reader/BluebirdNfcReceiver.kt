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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.bluebird.extnfc.ExtNfcReader
import org.calypsonet.keyple.plugin.bluebird.BluebirdSupportContactlessProtocols
import timber.log.Timber

class BluebirdNfcReceiver(private val mContext: Context?) {
    var isRegistered: Boolean = false
        private set

    private var mListener: ((NfcResult) -> Unit)? = null

    fun registerScanReceiver() {
        Timber.d("BluebirdNfcReceiver - registerScanReceiver - isRegistered : $isRegistered")
        if (isRegistered) {
            unregisterScanReceiver()
        }
        if (mContext != null) {
            val filter = IntentFilter()
            filter.addAction(ExtNfcReader.Broadcast.EXTNFC_DETECTED_ACTION)
            mContext.registerReceiver(mEventReceiver, filter)
            isRegistered = true
        }
    }

    fun unregisterScanReceiver() {
        Timber.d("BluebirdNfcReceiver - unregisterScanReceiver - isRegistered : $isRegistered")
        if (!isRegistered) {
            return
        }
        if (mContext != null) {
            try {
                mContext.unregisterReceiver(mEventReceiver)
            } catch (ie: IllegalArgumentException) {
                Timber.e(ie)
            }
        }
        isRegistered = false
    }

    fun setListener(listener: ((NfcResult) -> Unit)) {
        mListener = listener
    }

    private val mEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (ExtNfcReader.Broadcast.EXTNFC_DETECTED_ACTION == action) {
                mListener?.let {
                    val cardType = intent.getIntExtra(ExtNfcReader.Broadcast.EXTNFC_CARD_TYPE_KEY, -1)
                    val tag = Tag(
                        currentProtocol = BluebirdSupportContactlessProtocols.findEnumByValue(cardType),
                        data = intent.getByteArrayExtra(ExtNfcReader.Broadcast.EXTNFC_CARD_DATA_KEY)
                    )
                    it(NfcResultSuccess(tag))
                }
            }
        }
    }
}
