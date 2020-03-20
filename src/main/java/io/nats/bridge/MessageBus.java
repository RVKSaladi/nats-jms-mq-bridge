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

package io.nats.bridge;

import java.io.Closeable;
import java.util.Optional;
import java.util.function.Consumer;


/**
 * Message Bus wraps a Nats.io and a JMS message context to send/recieve messages to the messaging systems.
 * It also encapsulates how request/reply is DONE.
 * TODO write Nats.io version
 */
public interface MessageBus extends Closeable {

    /**
     * Publish a message.
     *
     * @param message message bus message.
     */
    void publish(Message message);

    /**
     * Publish a string message
     *
     * @param message string message
     */
    default void publish(String message) {
        publish(new StringMessage(message));
    }

    /**
     * Perform a request/reply over nats or JMS.
     *
     * @param message       message to send
     * @param replyCallback callback.
     */
    void request(final Message message, Consumer<Message> replyCallback);

    /**
     * Perform a request/reply with strings
     *
     * @param message message string
     * @param reply   callback for reply string
     */
    default void request(final String message, final Consumer<String> reply) {
        request(new StringMessage(message), replyMessage -> reply.accept(((StringMessage) replyMessage).getBody()));
    }

    /**
     * Receives a message. The optional is none if the message is not received.
     *
     * @return a possible message.
     */
    Optional<Message> receive();

    void close();


}
