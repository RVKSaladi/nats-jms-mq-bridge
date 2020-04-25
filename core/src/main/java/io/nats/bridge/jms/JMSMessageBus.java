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
package io.nats.bridge.jms;

import io.nats.bridge.MessageBus;
import io.nats.bridge.TimeSource;
import io.nats.bridge.jms.support.JMSReply;
import io.nats.bridge.jms.support.JMSRequestResponse;
import io.nats.bridge.messages.Message;
import io.nats.bridge.metrics.Counter;
import io.nats.bridge.metrics.Metrics;
import io.nats.bridge.metrics.MetricsProcessor;
import io.nats.bridge.metrics.TimeTracker;
import io.nats.bridge.util.ExceptionHandler;
import io.nats.bridge.util.FunctionWithException;
import org.slf4j.Logger;

import javax.jms.*;
import java.util.Queue;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;


public class JMSMessageBus implements MessageBus {

    private final Destination destination;
    private final Session session;
    private final Connection connection;

    private final Destination responseDestination;
    private final MessageConsumer responseConsumer;
    private final TimeSource timeSource;
    private final Map<String, JMSRequestResponse> requestResponseMap = new HashMap<>();
    private final Metrics metrics;
    private final Counter countRequestResponseErrors;
    private final Counter countReceivedReply;
    private final Counter countReceivedReplyErrors;
    private final Counter countPublish;
    private final Counter countReceived;
    private final Counter countPublishErrors;
    private final Counter messageConvertErrors;
    private final Counter countRequest;
    private final Counter countRequestErrors;
    private final Counter countRequestResponses;
    private final Counter countRequestResponsesMissing;
    private final TimeTracker timerRequestResponse;
    private final TimeTracker timerReceiveReply;
    private final Supplier<MessageProducer> producerSupplier;
    private final Supplier<MessageConsumer> consumerSupplier;
    private final MetricsProcessor metricsProcessor;
    private final ExceptionHandler tryHandler;
    private final Logger logger;
    private MessageProducer producer;
    private MessageConsumer consumer;
    private final java.util.Queue<JMSReply> jmsReplyQueue;
    private final FunctionWithException<javax.jms.Message, Message> jmsMessageConverter;
    private FunctionWithException<Message, javax.jms.Message> bridgeMessageConverter;

    public JMSMessageBus(final Destination destination, final Session session,
                         final Connection connection, final Destination responseDestination,
                         final MessageConsumer responseConsumer, final TimeSource timeSource, final Metrics metrics,
                         final Supplier<MessageProducer> producerSupplier,
                         final Supplier<MessageConsumer> consumerSupplier,
                         final MetricsProcessor metricsProcessor,
                         final ExceptionHandler tryHandler,
                         final Logger logger,
                         final Queue<JMSReply> jmsReplyQueue,
                         final FunctionWithException<javax.jms.Message, Message> jmsMessageConverter,
                         final FunctionWithException<Message, javax.jms.Message> bridgeMessageConverter) {
        this.destination = destination;
        this.session = session;
        this.connection = connection;
        this.responseDestination = responseDestination;
        this.responseConsumer = responseConsumer;
        //TODO setup exception listener for JMS Connection
        this.timeSource = timeSource;
        this.tryHandler = tryHandler;


        this.metrics = metrics;
        countPublish = metrics.createCounter("publish-count");
        countPublishErrors = metrics.createCounter("publish-count-errors");
        countRequest = metrics.createCounter("request-count");
        countRequestErrors = metrics.createCounter("request-count-errors");
        countRequestResponses = metrics.createCounter("request-response-count");
        countRequestResponseErrors = metrics.createCounter("request-response-count-errors");
        countRequestResponsesMissing = metrics.createCounter("request-response-missing-count");
        timerRequestResponse = metrics.createTimeTracker("request-response-timing");
        countReceived = metrics.createCounter("received-count");
        countReceivedReply = metrics.createCounter("received-reply-count");
        timerReceiveReply = metrics.createTimeTracker("receive-reply-timing");
        countReceivedReplyErrors = metrics.createCounter("received-reply-count-errors");
        messageConvertErrors = metrics.createCounter("message-convert-count-errors");


        this.producerSupplier = producerSupplier;

        this.consumerSupplier = consumerSupplier;
        this.metricsProcessor = metricsProcessor;
        this.logger = logger;
        this.jmsReplyQueue = jmsReplyQueue;
        this.jmsMessageConverter = jmsMessageConverter;
        this.bridgeMessageConverter = bridgeMessageConverter;
    }

    private MessageProducer producer() {
        if (producer == null) {
            producer = producerSupplier.get();
        }
        return producer;
    }

    private MessageConsumer consumer() {
        if (consumer == null) {
            consumer = consumerSupplier.get();
        }
        return consumer;
    }

    @Override
    public void publish(final Message message) {

        if (logger.isInfoEnabled()) logger.info("publish called " + message);
        tryHandler.tryWithErrorCount(() -> {
            producer().send(convertToJMSMessage(message));
            countPublish.increment();
        }, countPublishErrors, "Unable to send the message to the producer");

    }


    @Override
    public void request(final Message message, final Consumer<Message> replyCallback) {

        if (logger.isInfoEnabled()) logger.info("request called " + message);

        //TODO to get this to be more generic as part of builder pass a createDestination Function<Session, Destination> that calls session.createTemporaryQueue() or session.createTemporaryTopic()
        final javax.jms.Message jmsMessage = convertToJMSMessage(message);


        tryHandler.tryWithRethrow(() -> {
            final String correlationID = UUID.randomUUID().toString();
            jmsMessage.setJMSReplyTo(responseDestination);
            jmsMessage.setJMSCorrelationID(correlationID);
            producer().send(jmsMessage);
            if (logger.isDebugEnabled()) logger.debug("REQUEST BODY " + message.toString());

            if (logger.isDebugEnabled())
                logger.debug(String.format("CORRELATION ID: %s %s\n", correlationID, responseDestination.toString()));
            requestResponseMap.put(correlationID, new JMSRequestResponse(replyCallback, timeSource.getTime()));
            countRequest.increment();
        }, countRequestErrors, e -> new JMSMessageBusException("unable to send JMS request", e));

    }


    private javax.jms.Message convertToJMSMessage(final Message message) {
        return tryHandler.tryFunctionOrRethrow(message,
                m -> bridgeMessageConverter.apply(message),
                e -> new JMSMessageBusException("Unable to create JMS message", e));
    }


    private Message convertToBusMessage(final javax.jms.Message jmsMessage) {
        return tryHandler.tryFunctionOrRethrow(jmsMessage, m -> jmsMessageConverter.apply(jmsMessage), e -> {
            messageConvertErrors.increment();
            return new JMSMessageBusException("Unable to convert JMS message to Bridge Message", e);
        });

    }

    @Override
    public Optional<Message> receive() {

        return tryHandler.tryReturnOrRethrow(() -> {
            final javax.jms.Message message = consumer().receiveNoWait();
            if (message != null) {
                countReceived.increment();
                return Optional.of(convertToBusMessage(message));
            } else {
                return Optional.empty();
            }
        }, e -> {
            throw new JMSMessageBusException("Error receiving message", e);
        });


    }

    @Override
    public void close() {
        tryHandler.tryWithRethrow(connection::close, e -> new JMSMessageBusException("Error closing connection", e));
    }


    /**
     * This method gets called by bridge to process outstanding responses.
     * If the client is Nats and the Server is JMS then there will be messages from the `responseConsumer`.
     */
    private void processResponses() {

        tryHandler.tryWithErrorCount(() -> {
            javax.jms.Message message;
            do {
                message = responseConsumer.receiveNoWait();
                if (message != null) {
                    final String correlationID = message.getJMSCorrelationID();
                    if (logger.isDebugEnabled())
                        logger.debug(String.format("Process JMS Message Consumer %s \n", correlationID));
                    final Optional<JMSRequestResponse> jmsRequestResponse = Optional.ofNullable(requestResponseMap.get(correlationID));
                    final javax.jms.Message msg = message;
                    jmsRequestResponse.ifPresent(requestResponse -> {
                        requestResponse.getReplyCallback().accept(convertToBusMessage(msg));
                        /* Record metrics for duration and count. */
                        timerRequestResponse.recordTiming(this.timeSource.getTime() - requestResponse.getSentTime());
                        countRequestResponses.increment();
                    });

                    if (!jmsRequestResponse.isPresent()) {
                        countRequestResponsesMissing.increment();
                    }
                }
            }
            while (message != null);
        }, countRequestResponseErrors, "Error Processing Responses");

    }

    @Override
    public void process() {
        processResponses();
        processReplies();

        metricsProcessor.process();
    }

    /**
     * This method gets called to process replies.
     * If the client is JMS and the Server is Nats then there will be replies to process.
     */
    private void processReplies() {
        tryHandler.tryWithErrorCount(() -> {
            JMSReply reply = null;
            do {
                reply = jmsReplyQueue.poll();
                if (reply != null) {
                    final byte[] messageBody = reply.getReply().getBodyBytes();
                    final String correlationId = reply.getCorrelationID();
                    final MessageProducer replyProducer = session.createProducer(reply.getJmsReplyTo());
                    final BytesMessage jmsReplyMessage = session.createBytesMessage();
                    jmsReplyMessage.writeBytes(messageBody);
                    timerReceiveReply.recordTiming(timeSource.getTime() - reply.getSentTime());
                    countReceivedReply.increment();
                    if (logger.isDebugEnabled())
                        logger.debug(String.format("Reply handler - %s %s %s\n", reply.getReply().bodyAsString(), correlationId, replyProducer.getDestination().toString()));
                    jmsReplyMessage.setJMSCorrelationID(correlationId);
                    replyProducer.send(jmsReplyMessage);
                    replyProducer.close();
                }
            }
            while (reply != null);
        }, countReceivedReplyErrors, "error processing JMS receive queue for replies");
    }
}