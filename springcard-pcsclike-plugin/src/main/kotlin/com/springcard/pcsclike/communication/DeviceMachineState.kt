/* **************************************************************************************
 * Copyright (c) 2018-2019 SpringCard - https://www.springcard.com/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package com.springcard.pcsclike.communication

import com.springcard.pcsclike.SCardReaderList
import timber.log.Timber

internal enum class State {
  Closed,
  Creating,
  Idle,
  Sleeping,
  WakingUp,
  WritingCmdAndWaitingResp,
  Closing
}

internal class DeviceMachineState(private val scardReaderList: SCardReaderList) {

  @Volatile
  internal var currentState = State.Closed
    get() {
      Timber.d("currentState = ${field.name}")
      return field
    }
    private set(value) {
      Timber.d("New currentState = ${value.name}")
      field = value
    }
  private var isCreated = false

  fun setNewState(newState: State) {

    val oldState = currentState
    currentState = newState
    var callback: (() -> Unit)? = null

    Timber.d("State transition: $oldState -> $newState")

    when (newState) {
      State.Closed -> {
        when (oldState) {
          State.Closing -> {
            val uniqueId = SCardReaderList.getDeviceUniqueId(scardReaderList.layerDevice)
            SCardReaderList.connectedScardReaderList.remove(uniqueId)

            scardReaderList.exitExclusive()
            /* If error arrive while creating */
            /* Post with scardReaderList = null */
            callback =
                if (isCreated) {
                  { scardReaderList.callbacks.onReaderListClosed(scardReaderList) }
                } else {
                  { scardReaderList.callbacks.onReaderListClosed(null) }
                }
            isCreated = false
          }
          else -> {
            Timber.w("Transition should not happen")
            currentState = oldState
          }
        }
      }
      State.Creating -> {
        when (oldState) {
          State.Closed -> {
            /* No callback */
          }
          else -> {
            Timber.w("Transition should not happen")
            currentState = oldState
          }
        }
      }
      State.Idle -> {
        when (oldState) {
          State.Idle -> {
            /* card changed */
            /* callback emitted from slot machine state */
          }
          State.Creating -> {
            isCreated = true

            val uniqueId = SCardReaderList.getDeviceUniqueId(scardReaderList.layerDevice)
            SCardReaderList.knownSCardReaderList[uniqueId] = scardReaderList.constants
            SCardReaderList.connectedScardReaderList.add(uniqueId)

            scardReaderList.exitExclusive()
            callback = { scardReaderList.callbacks.onReaderListCreated(scardReaderList) }
          }
          State.Sleeping -> {
            callback =
                {
                  scardReaderList.callbacks.onReaderListState(
                      scardReaderList, scardReaderList.isSleeping)
                }
          }
          State.WakingUp -> {
            scardReaderList.exitExclusive()
            callback =
                {
                  scardReaderList.callbacks.onReaderListState(
                      scardReaderList, scardReaderList.isSleeping)
                }
          }
          State.WritingCmdAndWaitingResp -> {
            scardReaderList.exitExclusive()
            /* No callback here */
            /* But callback emitted from slot machine state */
          }
          else -> {
            Timber.w("Transition should not happen")
            currentState = oldState
          }
        }
        scardReaderList.mayConnectCard()
      }
      State.Sleeping -> {
        when (oldState) {
          State.Idle -> {
            callback =
                {
                  scardReaderList.callbacks.onReaderListState(
                      scardReaderList, scardReaderList.isSleeping)
                }
          }
          else -> {
            Timber.w("Transition should not happen")
            currentState = oldState
          }
        }
      }
      State.WakingUp -> {
        when (oldState) {
          State.Sleeping -> {
            /* No callback */
          }
          else -> {
            Timber.w("Transition should not happen")
            currentState = oldState
          }
        }
      }
      State.WritingCmdAndWaitingResp -> {
        when (oldState) {
          State.Idle -> {

            /* No callback */
          }
          else -> {
            Timber.w("Transition should not happen")
            currentState = oldState
          }
        }
      }
      State.Closing -> {
        when (oldState) {
          State.Creating, State.WritingCmdAndWaitingResp, State.WakingUp -> {
            /* If error arrive while creating */
            /* Post with scardReaderList = null */
            callback =
                if (isCreated) {
                  {
                    scardReaderList.callbacks.onReaderListError(
                        scardReaderList, scardReaderList.lastError)
                  }
                } else {
                  { scardReaderList.callbacks.onReaderListError(null, scardReaderList.lastError) }
                }
          }
          State.Sleeping -> {
            callback =
                {
                  scardReaderList.callbacks.onReaderListError(
                      scardReaderList, scardReaderList.lastError)
                }
          }
          State.Idle -> {
            /* No callback */
          }
          else -> {
            Timber.w("Transition should not happen")
            currentState = oldState
          }
        }
      }
      else -> {
        Timber.w("Impossible new state: $newState")
        currentState = oldState
      }
    }

    if (callback != null) {
      scardReaderList.postCallback(callback)
    }
  }
}
