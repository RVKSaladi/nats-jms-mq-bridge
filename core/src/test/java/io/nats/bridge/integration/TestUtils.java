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

import io.nats.bridge.MessageBridge;
import io.nats.bridge.MessageBus;
import io.nats.bridge.jms.support.JMSMessageBusBuilder;
import io.nats.bridge.messages.Message;
import io.nats.bridge.messages.MessageBuilder;
import io.nats.bridge.nats.support.NatsMessageBusBuilder;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TestUtils {
    public static MessageBus getMessageBusJms(final String topicPostFix) {
        final String queueName = "dynamicQueues/message-only-" + topicPostFix;
        final JMSMessageBusBuilder jmsMessageBusBuilder = new JMSMessageBusBuilder().withDestinationName(queueName);

        return jmsMessageBusBuilder.withUserNameConnection("cloudurable").withPasswordConnection("cloudurable").build();
    }

    public static MessageBus getMessageBusIbmMQ() {
        try {
            final JMSMessageBusBuilder jmsMessageBusBuilder = new JMSMessageBusBuilder().useIBMMQ().withDestinationName("DEV.QUEUE.1")
                    .withReplyDestinationName("DEV.QUEUE.2");
            return jmsMessageBusBuilder.withUserNameConnection("app").withPasswordConnection("passw0rd").build();
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }


    public static MessageBus getMessageBusJmsWithHeaders(final String topicPostFix) {
        final String queueName = "dynamicQueues/headers-message-only-" + topicPostFix;
        final JMSMessageBusBuilder jmsMessageBusBuilder = new JMSMessageBusBuilder().turnOnCopyHeaders().withDestinationName(queueName);
        return jmsMessageBusBuilder.withUserNameConnection("cloudurable").withPasswordConnection("cloudurable").build();
    }

    public static MessageBus getMessageBusNats(final String topicPostFix) throws IOException, InterruptedException {

        final String subject = topicPostFix + "NatsMessageBus";

        final NatsMessageBusBuilder natsMessageBusBuilder = NatsMessageBusBuilder.builder().withSubject(subject);
        natsMessageBusBuilder.getOptionsBuilder().noReconnect();
        return natsMessageBusBuilder.build();
    }


    public static void runBridgeLoop(final MessageBridge messageBridge, final AtomicBoolean stop, final CountDownLatch countDownLatch) {

        final Thread thread = new Thread(() -> {
            try {
                while (!stop.get()) {
                    Thread.sleep(1000);

                    int process = messageBridge.process(Duration.ofSeconds(10));
                    if (process > 0) {
                        System.out.println("Bridge Loop: Messages sent or received " + process);
                    }
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
                    System.out.println("Server Loop: Handle message " + message.bodyAsString());
                    System.out.println("Server Loop: Handle message headers " + message.headers());


                    final String myHeader = (String) message.headers().get("MY_HEADER");
                    if (myHeader == null) {
                        final Message reply = MessageBuilder.builder().withBody("Hello " + message.bodyAsString()).build();
                        message.reply(reply);
                    } else {
                        final Message reply = MessageBuilder.builder().withBody("Hello " + message.bodyAsString() + " MY_HEADER " + myHeader).build();
                        message.reply(reply);
                    }

                });


                try {
                    Thread.sleep(50);
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
