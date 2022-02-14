/*
 * Copyright (c) 2018-2018-2018 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.pcsclike.example.model

open class EventModel(val type: Int, val text: String) {
  companion object {
    const val TYPE_HEADER = 0
    const val TYPE_ACTION = 1
    const val TYPE_RESULT = 2
    const val TYPE_MULTICHOICE = 3
  }
}
