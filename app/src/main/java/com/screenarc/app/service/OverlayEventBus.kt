package com.screenarc.app.service

import com.screenarc.app.model.OverlayCommand
import com.screenarc.app.model.OverlayEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object OverlayEventBus {
    private val _events = MutableSharedFlow<OverlayEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<OverlayEvent> = _events.asSharedFlow()

    private val _commands = MutableSharedFlow<OverlayCommand>(extraBufferCapacity = 64)
    val commands: SharedFlow<OverlayCommand> = _commands.asSharedFlow()

    fun postEvent(event: OverlayEvent) {
        _events.tryEmit(event)
    }

    fun postCommand(command: OverlayCommand) {
        _commands.tryEmit(command)
    }
}
