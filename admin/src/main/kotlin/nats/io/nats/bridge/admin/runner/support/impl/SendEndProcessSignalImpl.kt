package nats.io.nats.bridge.admin.runner.support.impl

import nats.io.nats.bridge.admin.runner.support.SendEndProcessSignal
import java.util.concurrent.atomic.AtomicBoolean

class SendEndProcessSignalImpl(private val atomicBoolean: AtomicBoolean = AtomicBoolean()) : SendEndProcessSignal {
    override fun sendStopRunning() = atomicBoolean.set(true)
}