class CgmSyncMachine(
    private val protocol: CgmDeviceProtocol,
    private val session: CgmCacheSession,
    private val validator: CgmCacheValidator
) {
    var state: CgmSyncState = CgmSyncState.Idle
        private set

    fun dispatch(event: CgmSyncEvent): CgmTransition {
        return when (event) {
            CgmSyncEvent.ReadRequested -> onReadRequested()
            is CgmSyncEvent.DeviceTextReceived -> onDeviceText(event.text)
            is CgmSyncEvent.CacheFileSaved -> onCacheFileSaved(event.file)
            is CgmSyncEvent.JobFinished -> onJobFinished(event.result)
            CgmSyncEvent.DeleteRequested -> onDeleteRequested()
            CgmSyncEvent.DeleteAckReceived -> onDeleteAck()
            CgmSyncEvent.ResetRequested -> onReset()
        }
    }

    private fun onReadRequested(): CgmTransition {
        session.beginRead()
        state = CgmSyncState.ReadingCache
        return transition(CgmDomainEffect.SendDeviceCommand(protocol.readCacheCommand()))
    }

    private fun onDeviceText(text: String): CgmTransition {
        if (protocol.isDeleteAck(text)) return dispatch(CgmSyncEvent.DeleteAckReceived)

        val accepted = session.acceptText(text)
        state = CgmSyncState.ReceivingCache(accepted.lineCount)
        if (!accepted.sawEnd) return transition()

        state = CgmSyncState.ValidatingCache
        val validation = validator.validate(session.snapshot())
        if (!validation.valid) return retryOrFail(validation)

        return transition(CgmDomainEffect.SaveCacheFile(session.snapshot()))
    }

    private fun onCacheFileSaved(file: CgmCacheFile): CgmTransition {
        state = CgmSyncState.Uploading(file)
        return transition(CgmDomainEffect.UploadAndPoll(file))
    }

    private fun onJobFinished(result: CgmJobResult): CgmTransition {
        state = CgmSyncState.WaitingDeleteConfirm(result)
        return transition(
            CgmDomainEffect.PublishResult(result),
            CgmDomainEffect.SendDeviceCommand(protocol.deleteCacheCommand())
        )
    }

    private fun onDeleteRequested(): CgmTransition {
        state = CgmSyncState.WaitingDeleteConfirm(result = null)
        return transition(CgmDomainEffect.SendDeviceCommand(protocol.deleteCacheCommand()))
    }

    private fun retryOrFail(validation: CgmCacheValidation): CgmTransition {
        val reason = validation.message ?: "cache invalid"
        if (session.canRetry()) {
            session.beginRetry()
            state = CgmSyncState.RetryingRead(session.attempt, reason)
            return transition(CgmDomainEffect.SendDeviceCommand(protocol.readCacheRetryCommand()))
        }

        state = CgmSyncState.Failed(reason)
        return transition(CgmDomainEffect.ShowMessage(reason))
    }

    private fun onDeleteAck(): CgmTransition {
        if (state !is CgmSyncState.WaitingDeleteConfirm) return transition()
        state = CgmSyncState.Done((state as CgmSyncState.WaitingDeleteConfirm).result)
        return transition(CgmDomainEffect.ShowMessage("cache delete confirmed"))
    }

    private fun onReset(): CgmTransition {
        session.reset()
        state = CgmSyncState.Idle
        return transition()
    }

    private fun transition(vararg effects: CgmDomainEffect): CgmTransition {
        return CgmTransition(state = state, effects = effects.toList())
    }
}