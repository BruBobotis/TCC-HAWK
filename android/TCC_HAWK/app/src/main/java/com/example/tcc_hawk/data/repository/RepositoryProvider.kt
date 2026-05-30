package com.example.tcc_hawk.data.repository

import android.content.Context
import com.example.tcc_hawk.data.repository.fake.FakeHawkRepository
import com.example.tcc_hawk.data.repository.ble.BleHawkRepository

object RepositoryProvider {

    // Troque para false quando quiser testar sem relógio
    var useBle: Boolean = true

    fun provide(context: Context): HawkRepository {
        return if (useBle) BleHawkRepository(context) else FakeHawkRepository()
    }
}