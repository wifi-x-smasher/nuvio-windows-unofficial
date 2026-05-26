package com.nuvio.app.core.sync

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

internal actual object AppForegroundMonitor {
    actual fun events(): Flow<Unit> = flowOf(Unit)
}
