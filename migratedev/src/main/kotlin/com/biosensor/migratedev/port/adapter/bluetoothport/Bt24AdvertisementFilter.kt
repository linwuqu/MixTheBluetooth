package com.biosensor.migratedev.port.adapter.bluetoothport

fun interface BluetoothAdvertisementFilter {
    fun matches(advertisement: BluetoothAdvertisement): Boolean
}

object Bt24AdvertisementFilter : BluetoothAdvertisementFilter {
    private const val SERVICE_UUID = "FFE0"
    private const val MANUFACTURER_ID = 0x4458

    override fun matches(
        advertisement: BluetoothAdvertisement
    ): Boolean {
        if (!advertisement.isBle) {
            return false
        }
        val serviceUuids = advertisement.serviceUuids ?: return false
        val manufacturerIds = advertisement.manufacturerIds ?: return false
        return serviceUuids.any {
            it.normalizedUuid() == SERVICE_UUID
        } && MANUFACTURER_ID in manufacturerIds
    }

    private fun String.normalizedUuid(): String {
        val compact = removePrefix("0x").substringBefore("-").uppercase()
        return when {
            compact.length == 8 && compact.startsWith("0000") -> compact.takeLast(4)

            else -> compact.padStart(4, '0')
        }
    }
}
