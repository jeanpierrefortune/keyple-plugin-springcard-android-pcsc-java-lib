# Module AndroidPcscPlugin
# Package com.springcard.keyple.plugin.android.pcsc

You will find here some explanations on the use as well as the interfaces and public classes of the AndroidPcscPlugin.

## Initialization of the plugin
The first thing to do to use this plugin is to get a factory with [AndroidPcscPluginFactoryProvider] by providing it with 
- the type of device ([AndroidPcscPluginFactory.DeviceType]),
- the Android context.

## Register and observer
Submit the obtained factory to the smart card service of Keyple and get back the plugin.
<br>
Become an observer of the plugin to retrieve the readers from the events generated during their connection.

## Reader discovery

Start the device scanning with [AndroidPcscPlugin.scanDevices].
<br>
Handle the result received by the class implementing *DeviceScannerSpi*.
<br>
Become an observer of the needed reader.

## Code snippet 
<pre>
// initialization
pluginFactory = AndroidPcscPluginFactoryProvider.getFactory(AndroidPcscPluginFactory.DeviceType.USB, activity)
// registration
androidPcscPlugin = smartCardService.registerPlugin(pluginFactory) as ObservablePlugin
// set up observation
androidPcscPlugin.setPluginObservationExceptionHandler(this)
androidPcscPlugin.addObserver(this)
// discover readers
androidPcscPlugin
    .getExtension(AndroidPcscPlugin::class.java)
    .scanDevices(2, true, this)
[...]
override fun onDeviceDiscovered(deviceInfoList: MutableCollection&lt;DeviceInfo&gt;) {
    // connect to first discovered device
    if (deviceInfoList.isNotEmpty()) {
      val device = deviceInfoList.first()
      androidPcscPlugin
          .getExtension(AndroidPcscPlugin::class.java)
          .connectToDevice(device.identifier)
    } else {
        ...
    }
}
[...]
override fun onPluginEvent(pluginEvent: PluginEvent?) {
  if (pluginEvent != null) {
    if (pluginEvent.type == PluginEvent.Type.READER_CONNECTED) {
      // connect to the reader identified by its name
      for (readerName in pluginEvent.readerNames) {
        if (readerName.toUpperCase().contains("CONTACTLESS")) {
            cardReader = androidPcscPlugin.getReader(readerName) as ObservableReader
            if (cardReader != null) {
              cardReader!!.getExtension(AndroidPcscReader::class.java).setContactless(true)
              cardReader!!.setReaderObservationExceptionHandler { pluginName, readerName, e ->
                // handle plugin observation exception here
              }
              cardReader!!.addObserver(this)
              cardReader!!.startCardDetection(ObservableCardReader.DetectionMode.REPEATING)
              // prepare scheduled selection here        
            }
        }
    }
    if (pluginEvent.type == PluginEvent.Type.READER_DISCONNECTED) {
      for (readerName in pluginEvent.readerNames) {
        // notify reader disconnection
      }
    }
  }
}
</pre>
