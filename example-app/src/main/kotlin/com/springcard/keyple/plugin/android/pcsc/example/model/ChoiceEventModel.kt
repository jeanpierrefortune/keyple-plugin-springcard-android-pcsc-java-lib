/*
 * Copyright (c) 2018-2018-2018 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc.example.model

data class ChoiceEventModel(
    val title: String,
    val choices: List<String> = arrayListOf(),
    val callback: (choice: String) -> Unit
) : EventModel(TYPE_MULTICHOICE, title)
