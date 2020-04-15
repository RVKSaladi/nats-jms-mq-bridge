// Copyright 2020 The NATS Authors
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at:
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.nats.bridge.integration;

import io.nats.bridge.messages.Message;
import io.nats.bridge.MessageBridge;
import io.nats.bridge.MessageBus;
import io.nats.bridge.jms.support.JMSMessageBusBuilder;
import io.nats.bridge.messages.MessageBuilder;
import io.nats.bridge.nats.support.NatsMessageBusBuilder;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestUtils {
    public static MessageBus getMessageBusJms(final String topicPostFix) {
        final String queueName = "dynamicQueues/message-only-" + topicPostFix;
        final JMSMessageBusBuilder jmsMessageBusBuilder = new JMSMessageBusBuilder().withDestinationName(queueName);
        return jmsMessageBusBuilder.build();
    }

    public static MessageBus getMessageBusNats(final String topicPostFix) throws IOException, InterruptedException {

        final String subject =  topicPostFix + "NatsMessageBus";

        final NatsMessageBusBuilder natsMessageBusBuilder = NatsMessageBusBuilder.builder().withSubject(subject);
        natsMessageBusBuilder.getOptionsBuilder().noReconnect();
        return natsMessageBusBuilder.build();
    }


    public static void runBridgeLoop(final MessageBridge messageBridge, final AtomicBoolean stop, final CountDownLatch countDownLatch) {

        final Thread thread = new Thread(() -> {
            try {
                while (!stop.get()) {
                    Thread.sleep(1000);
                    messageBridge.process();
                }
                messageBridge.close();
                countDownLatch.countDown();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });

        thread.start();
    }

    public static void stopServerAndBridgeLoops(final AtomicBoolean stop, final CountDownLatch countDownLatch1, final CountDownLatch countDownLatch2) throws Exception {
        stop.set(true);
        countDownLatch1.await(100, TimeUnit.MILLISECONDS);
        countDownLatch2.await(100, TimeUnit.MILLISECONDS);
    }


    public static void runServerLoop(final AtomicBoolean stop, final MessageBus serverMessageBus, final CountDownLatch serverStopped) {

        final Thread thread = new Thread(() -> {
            while (true) {
                if (stop.get()) {
                    serverMessageBus.close();
                    break;
                }
                final Optional<Message> receive = serverMessageBus.receive();
                receive.ifPresent(message -> {
                    System.out.println("Handle message " + message.bodyAsString());


                    final Message reply = MessageBuilder.builder().withBody("Hello " + message.bodyAsString()).build();
                    message.reply(reply);
                });


                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                serverMessageBus.process();
            }

            serverStopped.countDown();



        });

        thread.start();

    }
}
