/*
 * Copyright (c) 2018-2018-2018 SpringCard - www.springcard.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.pcsclike.example.util

/**
 * Helper class to provide specific elements to handle Calypso cards.
 *
 * * AID application selection (default Calypso AID)
 * * SAM_C1_ATR_REGEX regular expression matching the expected C1 SAM ATR
 * * Files infos (SFI, rec number, etc) for
 *
 * * Environment and Holder
 * * Event Log
 * * Contract List
 * * Contracts
 */
object CalypsoClassicInfo {
  /** Calypso default AID */
  //    const val AID = "315449432e494341"
  const val AID = "315449432e49434131"
  //    const val AID = "A0000004040125090101"

  //    const val AID_HIS_STRUCTURE_5H = "315449432e49434131"

  // / ** 1TIC.ICA AID */
  // public final static String AID = "315449432E494341";
  /** SAM C1 regular expression: platform, version and serial number values are ignored */
  const val ATR_REV1_REGEX = "3B8F8001805A0A0103200311........829000.."
  const val RECORD_NUMBER_1: Byte = 1
  const val RECORD_NUMBER_2: Byte = 2
  const val RECORD_NUMBER_3: Byte = 3
  const val RECORD_NUMBER_4: Byte = 4
  const val SFI_EnvironmentAndHolder = 0x07.toByte()
  const val SFI_EventLog = 0x08.toByte()
  const val SFI_ContractList = 0x1E.toByte()
  const val SFI_Contracts = 0x09.toByte()
  const val SFI_Counter1 = 0x19.toByte()
  const val eventLog_dataFill = "00112233445566778899AABBCCDDEEFF00112233445566778899AABBCC"

  // Security settings
  const val SAM_PROFILE_NAME = "SAM C1"

  const val SAM_READER_NAME_REGEX = ".*ContactReader"
}
