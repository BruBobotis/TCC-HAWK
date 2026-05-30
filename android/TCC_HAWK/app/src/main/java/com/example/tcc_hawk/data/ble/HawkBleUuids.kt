package com.example.tcc_hawk.data.ble

import java.util.UUID

object HawkBleUuids {
    val SERVICE: UUID = UUID.fromString("7E400001-8F42-4A7A-BB6F-112233445566")
    val STATUS: UUID  = UUID.fromString("7E400002-8F42-4A7A-BB6F-112233445566")
    val SYNC: UUID    = UUID.fromString("7E400003-8F42-4A7A-BB6F-112233445566")
    val CMD: UUID     = UUID.fromString("7E400004-8F42-4A7A-BB6F-112233445566")

    const val DEVICE_NAME = "HAWK-WATCH"
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}