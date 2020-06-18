package io.nats.bridge.admin.runner.support

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component

@Component
class HealthChecker(private val messageBridgeRunner: MessageBridgeRunner,
                    private val startTime: Long = System.currentTimeMillis() / 1000) : HealthIndicator {


    override fun health(): Health {
        return if (!messageBridgeRunner.isHealthy()) {
            Health.down().withDetail("NATS_MessageBridge", "Not Available - Was Started? ${messageBridgeRunner.wasStarted()} Is Running? ${messageBridgeRunner.isRunning()} ${messageBridgeRunner.getLastError()?.message} ${messageBridgeRunner.getLastError()?.javaClass?.simpleName}").build()
        } else if (!messageBridgeRunner.wasStarted() ) {
            Health.down().withDetail("NATS_MessageBridge", "Could not start the bridge! Was started? See logs for startup errors. ${messageBridgeRunner.wasStarted()} Is Running? ${messageBridgeRunner.isRunning()} ${messageBridgeRunner.getLastError()?.message} ${messageBridgeRunner.getLastError()?.javaClass?.simpleName}").build()
        }
        else Health.up().withDetail("NATS_MessageBridge", "Available")
                .withDetail("upTimeSeconds", (System.currentTimeMillis() / 1000) - startTime)
                .build()
    }
}