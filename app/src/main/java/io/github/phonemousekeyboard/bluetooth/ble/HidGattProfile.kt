package io.github.phonemousekeyboard.bluetooth.ble

import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED
import android.bluetooth.BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_NOTIFY
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_READ
import android.bluetooth.BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import io.github.phonemousekeyboard.bluetooth.HidDescriptor
import java.util.UUID

/**
 * The GATT layout of a Bluetooth Low Energy keyboard + mouse, as defined by the Bluetooth
 * "HID over GATT" profile (HOGP). Computers read this to learn what the device is.
 *
 *  HID service (0x1812)
 *    HID Information, Report Map (= [HidDescriptor.REPORT_MAP]), Protocol Mode,
 *    HID Control Point, and one Report characteristic per input report ID.
 *  Battery service (0x180F)        required by HOGP; always reports 100 %.
 *  Device Information (0x180A)     manufacturer name.
 *
 * Everything requires an encrypted link. Reading it before pairing makes the computer pair
 * first, which is how keyboards and mice are expected to behave.
 */
internal object HidGattProfile {

    // ---- UUIDs (Bluetooth SIG assigned numbers) ----

    val SERVICE_HID: UUID = uuid16(0x1812)
    val SERVICE_BATTERY: UUID = uuid16(0x180F)
    val SERVICE_DEVICE_INFORMATION: UUID = uuid16(0x180A)

    val CHARACTERISTIC_HID_INFORMATION: UUID = uuid16(0x2A4A)
    val CHARACTERISTIC_REPORT_MAP: UUID = uuid16(0x2A4B)
    val CHARACTERISTIC_HID_CONTROL_POINT: UUID = uuid16(0x2A4C)
    val CHARACTERISTIC_REPORT: UUID = uuid16(0x2A4D)
    val CHARACTERISTIC_PROTOCOL_MODE: UUID = uuid16(0x2A4E)
    val CHARACTERISTIC_BATTERY_LEVEL: UUID = uuid16(0x2A19)
    val CHARACTERISTIC_MANUFACTURER_NAME: UUID = uuid16(0x2A29)

    val DESCRIPTOR_CLIENT_CONFIGURATION: UUID = uuid16(0x2902)
    val DESCRIPTOR_REPORT_REFERENCE: UUID = uuid16(0x2908)

    // ---- Fixed values ----

    /** HID spec version 1.11, no country code, flags: remote wake + normally connectable. */
    val HID_INFORMATION = byteArrayOf(0x11, 0x01, 0x00, 0x03)

    /** Protocol Mode "Report" (as opposed to the simplified "Boot" protocol). */
    val PROTOCOL_MODE_REPORT = byteArrayOf(0x01)

    val BATTERY_FULL = byteArrayOf(100)

    val MANUFACTURER_NAME = "Phone Mouse & Keyboard".toByteArray(Charsets.UTF_8)

    /** Report Reference descriptor "type" value for input reports. */
    private const val REPORT_TYPE_INPUT: Byte = 1

    /** The input report IDs exposed as Report characteristics, in [HidDescriptor] order. */
    val INPUT_REPORT_IDS = listOf(HidDescriptor.KEYBOARD_REPORT_ID, HidDescriptor.MOUSE_REPORT_ID)

    // ---- Building the services ----

    /**
     * Builds the HID service. Returns the service and its Report characteristics by report ID,
     * which are needed later to send input.
     */
    fun buildHidService(): Pair<BluetoothGattService, Map<Int, BluetoothGattCharacteristic>> {
        val service = BluetoothGattService(SERVICE_HID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        service.addCharacteristic(readOnly(CHARACTERISTIC_HID_INFORMATION))
        service.addCharacteristic(readOnly(CHARACTERISTIC_REPORT_MAP))
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                CHARACTERISTIC_PROTOCOL_MODE,
                PROPERTY_READ or PROPERTY_WRITE_NO_RESPONSE,
                PERMISSION_READ_ENCRYPTED or PERMISSION_WRITE_ENCRYPTED,
            ),
        )
        service.addCharacteristic(
            BluetoothGattCharacteristic(
                CHARACTERISTIC_HID_CONTROL_POINT,
                PROPERTY_WRITE_NO_RESPONSE,
                PERMISSION_WRITE_ENCRYPTED,
            ),
        )

        val reports = INPUT_REPORT_IDS.associateWith { buildInputReport() }
        reports.values.forEach(service::addCharacteristic)

        return service to reports
    }

    fun buildBatteryService(): BluetoothGattService {
        val service = BluetoothGattService(SERVICE_BATTERY, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val level = BluetoothGattCharacteristic(
            CHARACTERISTIC_BATTERY_LEVEL,
            PROPERTY_READ or PROPERTY_NOTIFY,
            PERMISSION_READ_ENCRYPTED,
        )
        level.addDescriptor(clientConfiguration())
        service.addCharacteristic(level)
        return service
    }

    fun buildDeviceInformationService(): BluetoothGattService {
        val service = BluetoothGattService(
            SERVICE_DEVICE_INFORMATION,
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        )
        service.addCharacteristic(readOnly(CHARACTERISTIC_MANUFACTURER_NAME))
        return service
    }

    /** The Report Reference value for an input report: its ID, then "input". */
    fun reportReference(reportId: Int): ByteArray = byteArrayOf(reportId.toByte(), REPORT_TYPE_INPUT)

    private fun buildInputReport(): BluetoothGattCharacteristic {
        val report = BluetoothGattCharacteristic(
            CHARACTERISTIC_REPORT,
            PROPERTY_READ or PROPERTY_NOTIFY,
            PERMISSION_READ_ENCRYPTED,
        )
        report.addDescriptor(clientConfiguration())
        report.addDescriptor(
            BluetoothGattDescriptor(DESCRIPTOR_REPORT_REFERENCE, BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED),
        )
        return report
    }

    /** Lets the computer switch notifications on and off. */
    private fun clientConfiguration() = BluetoothGattDescriptor(
        DESCRIPTOR_CLIENT_CONFIGURATION,
        BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or
            BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED,
    )

    private fun readOnly(uuid: UUID) =
        BluetoothGattCharacteristic(uuid, PROPERTY_READ, PERMISSION_READ_ENCRYPTED)

    /** Expands a 16-bit Bluetooth SIG number into a full UUID. */
    private fun uuid16(shortUuid: Int): UUID =
        UUID.fromString("0000%04x-0000-1000-8000-00805f9b34fb".format(shortUuid))
}
