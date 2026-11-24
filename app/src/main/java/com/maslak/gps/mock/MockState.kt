package com.maslak.gps.mock

import com.maslak.gps.data.LatLng
import com.maslak.gps.data.MockRequest
import com.maslak.gps.data.MockSettings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

enum class RunPhase { IDLE, WAITING, RUNNING }

/** حالة مشتركة بين الواجهة والخدمة */
object MockState {
    val phase = MutableStateFlow(RunPhase.IDLE)
    val current = MutableStateFlow<LatLng?>(null)
    val errors = MutableSharedFlow<String>(extraBufferCapacity = 8)

    @Volatile var request: MockRequest? = null
    @Volatile var settings: MockSettings = MockSettings()
}
