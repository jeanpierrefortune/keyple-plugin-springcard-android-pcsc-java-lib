/*
 * Copyright (c)2022 SpringCard - www.springcard.com.com
 * All right reserved
 * This software is covered by the SpringCard SDK License Agreement - see LICENSE.txt
 */
package com.springcard.keyple.plugin.android.pcsc.example.activity

import com.springcard.keyple.plugin.android.pcsc.example.util.CalypsoClassicInfo
import org.calypsonet.terminal.calypso.WriteAccessLevel
import org.calypsonet.terminal.calypso.card.CalypsoCard
import org.calypsonet.terminal.calypso.sam.CalypsoSam
import org.calypsonet.terminal.calypso.transaction.CardSecuritySetting
import org.calypsonet.terminal.reader.CardReader
import org.calypsonet.terminal.reader.ObservableCardReader
import org.calypsonet.terminal.reader.selection.CardSelectionManager
import org.eclipse.keyple.card.calypso.CalypsoExtensionService
import org.eclipse.keyple.core.service.KeyplePluginException
import org.eclipse.keyple.core.service.ObservableReader
import org.eclipse.keyple.core.service.Plugin
import org.eclipse.keyple.core.service.Reader
import org.eclipse.keyple.core.service.SmartCardServiceProvider
import org.eclipse.keyple.core.service.resource.CardResourceProfileConfigurator
import org.eclipse.keyple.core.service.resource.CardResourceService
import org.eclipse.keyple.core.service.resource.CardResourceServiceProvider
import org.eclipse.keyple.core.service.resource.PluginsConfigurator
import org.eclipse.keyple.core.util.ByteArrayUtil
import timber.log.Timber

class CardManager {
  companion object {
    /**
     * Schedules a card selection targeting three applications:
     * - Keyple demo kit card, AID = 315449432e49434131
     * - Navigo B prime card, AID = 315449432E494341
     * - Navigo B card, AID = A0000004040125090101 In all three cases, the environment file is read
     * as soon as the application is selected.
     * @param cardReader The card reader.
     * @return The created card selection manager.
     */
    fun initiateScheduledCardSelection(cardReader: CardReader): CardSelectionManager {
      val smartCardService = SmartCardServiceProvider.getService()
      val cardSelectionManager = smartCardService.createCardSelectionManager()
      val calypsoCardExtensionProvider: CalypsoExtensionService
      try {
        calypsoCardExtensionProvider = CalypsoExtensionService.getInstance()

        cardSelectionManager.prepareSelection(
            calypsoCardExtensionProvider
                .createCardSelection()
                .filterByDfName(CalypsoClassicInfo.AID)
                .prepareReadRecord(
                    CalypsoClassicInfo.SFI_EnvironmentAndHolder,
                    CalypsoClassicInfo.RECORD_NUMBER_1.toInt()))

        cardSelectionManager.prepareSelection(
            calypsoCardExtensionProvider
                .createCardSelection()
                .filterByDfName("315449432E494341")
                .prepareReadRecord(
                    CalypsoClassicInfo.SFI_EnvironmentAndHolder,
                    CalypsoClassicInfo.RECORD_NUMBER_1.toInt()))

        cardSelectionManager.prepareSelection(
            calypsoCardExtensionProvider
                .createCardSelection()
                .filterByDfName("A0000004040125090101")
                .prepareReadRecord(
                    CalypsoClassicInfo.SFI_EnvironmentAndHolder,
                    CalypsoClassicInfo.RECORD_NUMBER_1.toInt()))

        cardSelectionManager.scheduleCardSelectionScenario(
            cardReader as ObservableReader,
            ObservableCardReader.DetectionMode.REPEATING,
            ObservableCardReader.NotificationMode.ALWAYS)
      } catch (e: KeyplePluginException) {
        Timber.e(e)
      } catch (e: Exception) {
        Timber.e(e)
      }
      return cardSelectionManager
    }

    fun runCardTransaction(
        cardReader: CardReader,
        calypsoCard: CalypsoCard,
        cardSecuritySetting: CardSecuritySetting?
    ) {
      if (cardSecuritySetting == null) {
        runCardTransactionWithoutSam(cardReader, calypsoCard)
      } else {
        runCardTransactionWithSam(cardReader, calypsoCard, cardSecuritySetting)
      }
    }

    private fun runCardTransactionWithoutSam(cardReader: CardReader, calypsoCard: CalypsoCard) {
      val calypsoCardExtensionProvider = CalypsoExtensionService.getInstance()
      val cardTransaction =
          calypsoCardExtensionProvider.createCardTransactionWithoutSecurity(cardReader, calypsoCard)
      /*
       * Prepare the reading of two additional files.
       */
      cardTransaction
          .prepareReadRecord(
              CalypsoClassicInfo.SFI_EventLog, CalypsoClassicInfo.RECORD_NUMBER_1.toInt())
          .prepareReadRecord(
              CalypsoClassicInfo.SFI_ContractList, CalypsoClassicInfo.RECORD_NUMBER_1.toInt())
          .prepareReadRecord(
              CalypsoClassicInfo.SFI_Contracts, CalypsoClassicInfo.RECORD_NUMBER_1.toInt())
          .prepareReadRecord(
              CalypsoClassicInfo.SFI_Counter1, CalypsoClassicInfo.RECORD_NUMBER_1.toInt())

      cardTransaction.processCardCommands()
      Timber.d("Card transaction ended successfully")
    }

    private fun runCardTransactionWithSam(
        cardReader: CardReader,
        calypsoCard: CalypsoCard,
        cardSecuritySetting: CardSecuritySetting
    ) {
      val newEventRecord: ByteArray =
          ByteArrayUtil.fromHex("8013C8EC55667788112233445566778811223344556677881122334455")

      val cardTransactionManager =
          CalypsoExtensionService.getInstance()
              .createCardTransaction(cardReader, calypsoCard, cardSecuritySetting)
              .prepareReadRecord(
                  CalypsoClassicInfo.SFI_EventLog, CalypsoClassicInfo.RECORD_NUMBER_1.toInt())
              .prepareReadRecord(
                  CalypsoClassicInfo.SFI_ContractList, CalypsoClassicInfo.RECORD_NUMBER_1.toInt())
              .prepareReadRecord(
                  CalypsoClassicInfo.SFI_Counter1, CalypsoClassicInfo.RECORD_NUMBER_1.toInt())
              .processOpening(WriteAccessLevel.DEBIT)

      /*
      Place for the analysis of the context and the list of contracts
      */

      // read the elected contract
      cardTransactionManager
          .prepareReadRecord(
              CalypsoClassicInfo.SFI_Contracts, CalypsoClassicInfo.RECORD_NUMBER_1.toInt())
          .processCardCommands()

      /*
      Place for the analysis of the contracts
      */

      // add an event record and close the Secure Session
      cardTransactionManager
          .prepareAppendRecord(CalypsoClassicInfo.SFI_EventLog, newEventRecord)
          .processClosing()

      Timber.d("Card transaction ended successfully")
    }

    /**
     * Setup the [CardResourceService] to provide a Calypso SAM C1 resource when requested.
     *
     * @param plugin The plugin to which the SAM reader belongs.
     * @param readerNameRegex A regular expression matching the expected SAM reader name.
     * @param samProfileName A string defining the SAM profile.
     * @throws IllegalStateException If the expected card resource is not found.
     */
    fun setupCardResourceService(
        plugin: Plugin,
        readerNameRegex: String?,
        samProfileName: String?
    ) {
      // Create a card resource extension expecting a SAM "C1".
      val samSelection =
          CalypsoExtensionService.getInstance()
              .createSamSelection()
              .filterByProductType(CalypsoSam.ProductType.SAM_C1)

      val samCardResourceExtension =
          CalypsoExtensionService.getInstance().createSamResourceProfileExtension(samSelection)

      // Get the service
      val cardResourceService = CardResourceServiceProvider.getService()

      // Configure the card resource service:
      // - allocation mode is blocking with a 100 milliseconds cycle and a 10 seconds timeout.
      // - the readers are searched in the PC/SC plugin, the observation of the plugin (for the
      // connection/disconnection of readers) and of the readers (for the insertion/removal of
      // cards)
      // is activated.
      // - two card resource profiles A and B are defined, each expecting a specific card
      // characterized by its power-on data and placed in a specific reader.
      // - the timeout for using the card's resources is set at 5 seconds.
      cardResourceService
          .configurator
          .withBlockingAllocationMode(100, 10000)
          .withPlugins(
              PluginsConfigurator.builder()
                  .addPluginWithMonitoring(
                      plugin,
                      { reader: Reader ->
                        Timber.e("Nothing to configure for reader '${reader.name}'")
                      },
                      { pluginName: String, e: Throwable ->
                        Timber.e("An unexpected plugin error occurred: $pluginName: $e")
                      },
                      { pluginName: String, readerName: String, e: Throwable ->
                        Timber.e("An unexpected reader error occurred: $pluginName:$readerName $e")
                      })
                  .withUsageTimeout(5000)
                  .build())
          .withCardResourceProfiles(
              CardResourceProfileConfigurator.builder(samProfileName, samCardResourceExtension)
                  .withReaderNameRegex(readerNameRegex)
                  .build())
          .configure()

      cardResourceService.start()

      // verify the resource availability
      val cardResource =
          cardResourceService.getCardResource(samProfileName)
              ?: throw IllegalStateException(
                  "Unable to retrieve a SAM card resource for profile '$samProfileName' from reader '$readerNameRegex' in plugin '${plugin.name}'")

      // release the resource
      cardResourceService.releaseCardResource(cardResource)
    }

    fun getSecuritySettings(): CardSecuritySetting? {
      // Create security settings that reference the same SAM profile requested from the card
      // resource
      // service and enable the multiple session mode.
      val samResource =
          CardResourceServiceProvider.getService()
              .getCardResource(CalypsoClassicInfo.SAM_PROFILE_NAME)
      return CalypsoExtensionService.getInstance()
          .createCardSecuritySetting()
          .setSamResource(samResource.reader, samResource.smartCard as CalypsoSam)
          .enableMultipleSession()
    }
  }
}
