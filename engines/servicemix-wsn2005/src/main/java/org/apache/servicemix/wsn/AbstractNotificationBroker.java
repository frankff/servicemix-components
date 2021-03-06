/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.servicemix.wsn;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.jws.Oneway;
import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;
import javax.xml.ws.wsaddressing.W3CEndpointReference;
import javax.xml.namespace.QName;

import org.apache.servicemix.id.IdGenerator;
import org.apache.servicemix.wsn.client.AbstractWSAClient;
import org.oasis_open.docs.wsn.b_2.GetCurrentMessage;
import org.oasis_open.docs.wsn.b_2.GetCurrentMessageResponse;
import org.oasis_open.docs.wsn.b_2.NoCurrentMessageOnTopicFaultType;
import org.oasis_open.docs.wsn.b_2.NotificationMessageHolderType;
import org.oasis_open.docs.wsn.b_2.Notify;
import org.oasis_open.docs.wsn.b_2.Subscribe;
import org.oasis_open.docs.wsn.b_2.SubscribeCreationFailedFaultType;
import org.oasis_open.docs.wsn.b_2.SubscribeResponse;
import org.oasis_open.docs.wsn.b_2.InvalidTopicExpressionFaultType;
import org.oasis_open.docs.wsn.br_2.PublisherRegistrationFailedFaultType;
import org.oasis_open.docs.wsn.br_2.RegisterPublisher;
import org.oasis_open.docs.wsn.br_2.RegisterPublisherResponse;
import org.oasis_open.docs.wsn.brw_2.NotificationBroker;
import org.oasis_open.docs.wsn.brw_2.PublisherRegistrationFailedFault;
import org.oasis_open.docs.wsn.brw_2.PublisherRegistrationRejectedFault;
import org.oasis_open.docs.wsn.brw_2.ResourceNotDestroyedFault;
import org.oasis_open.docs.wsn.bw_2.InvalidFilterFault;
import org.oasis_open.docs.wsn.bw_2.InvalidMessageContentExpressionFault;
import org.oasis_open.docs.wsn.bw_2.InvalidProducerPropertiesExpressionFault;
import org.oasis_open.docs.wsn.bw_2.InvalidTopicExpressionFault;
import org.oasis_open.docs.wsn.bw_2.MultipleTopicsSpecifiedFault;
import org.oasis_open.docs.wsn.bw_2.NoCurrentMessageOnTopicFault;
import org.oasis_open.docs.wsn.bw_2.SubscribeCreationFailedFault;
import org.oasis_open.docs.wsn.bw_2.TopicExpressionDialectUnknownFault;
import org.oasis_open.docs.wsn.bw_2.TopicNotSupportedFault;
import org.oasis_open.docs.wsn.bw_2.UnableToDestroySubscriptionFault;
import org.oasis_open.docs.wsn.bw_2.UnacceptableInitialTerminationTimeFault;
import org.oasis_open.docs.wsn.bw_2.UnsupportedPolicyRequestFault;
import org.oasis_open.docs.wsn.bw_2.UnrecognizedPolicyRequestFault;
import org.oasis_open.docs.wsrf.rw_2.ResourceUnknownFault;
import org.oasis_open.docs.wsrf.rw_2.ResourceUnavailableFault;
import org.oasis_open.docs.wsrf.rpw_2.GetResourceProperty;
import org.oasis_open.docs.wsrf.rpw_2.InvalidResourcePropertyQNameFault;
import org.oasis_open.docs.wsrf.rp_2.GetResourcePropertyResponse;
import org.oasis_open.docs.wsrf.rp_2.InvalidResourcePropertyQNameFaultType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebService(endpointInterface = "org.oasis_open.docs.wsn.brw_2.NotificationBroker")
public abstract class AbstractNotificationBroker extends AbstractEndpoint implements NotificationBroker, GetResourceProperty {

    public static final String NAMESPACE_URI = "http://docs.oasis-open.org/wsn/b-2";
    public static final String PREFIX = "wsnt";
    public static final QName TOPIC_EXPRESSION_QNAME = new QName(NAMESPACE_URI, "TopicExpression", PREFIX);
    public static final QName FIXED_TOPIC_SET_QNAME = new QName(NAMESPACE_URI, "FixedTopicSet", PREFIX);
    public static final QName TOPIC_EXPRESSION_DIALECT_QNAME = new QName(NAMESPACE_URI, "TopicExpressionDialect", PREFIX);
    public static final QName TOPIC_SET_QNAME = new QName(NAMESPACE_URI, "TopicSet", PREFIX);

    private final Logger logger = LoggerFactory.getLogger(AbstractNotificationBroker.class);

    private IdGenerator idGenerator;

    private AbstractPublisher anonymousPublisher;

    private Map<String, AbstractPublisher> publishers;

    private Map<String, AbstractSubscription> subscriptions;

    public AbstractNotificationBroker(String name) {
        super(name);
        idGenerator = new IdGenerator();
        subscriptions = new ConcurrentHashMap<String, AbstractSubscription>();
        publishers = new ConcurrentHashMap<String, AbstractPublisher>();
    }

    public void init() throws Exception {
        register();
        anonymousPublisher = createPublisher("Anonymous");
        anonymousPublisher.register();
    }

    public void destroy() throws Exception {
        anonymousPublisher.destroy();
        unregister();
    }

    protected String createAddress() {
        return "http://servicemix.org/wsnotification/NotificationBroker/" + getName();
    }

    /**
     * 
     * @param notify
     */
    @WebMethod(operationName = "Notify")
    @Oneway
    public void notify(
            @WebParam(name = "Notify", 
                      targetNamespace = "http://docs.oasis-open.org/wsn/b-1", 
                      partName = "Notify")
            Notify notify) {

        logger.debug("Notify");
        handleNotify(notify);
    }

    protected void handleNotify(Notify notify) {
        for (NotificationMessageHolderType messageHolder : notify.getNotificationMessage()) {
            W3CEndpointReference producerReference = messageHolder.getProducerReference();
            AbstractPublisher publisher = getPublisher(producerReference);
            if (publisher != null) {
                publisher.notify(messageHolder);
            }
        }
    }

    protected AbstractPublisher getPublisher(W3CEndpointReference producerReference) {
        AbstractPublisher publisher = null;
        if (producerReference != null) {
            String address = AbstractWSAClient.getWSAAddress(producerReference);
            publisher = publishers.get(address);
        }
        if (publisher == null) {
            publisher = anonymousPublisher;
        }
        return publisher;
    }

    /**
     * 
     * @param subscribeRequest
     * @return returns org.oasis_open.docs.wsn.b_1.SubscribeResponse
     * @throws SubscribeCreationFailedFault
     * @throws InvalidTopicExpressionFault
     * @throws TopicNotSupportedFault
     * @throws InvalidFilterFault
     * @throws InvalidProducerPropertiesExpressionFault
     * @throws ResourceUnknownFault
     * @throws InvalidUseRawValueFault
     * @throws InvalidMessageContentExpressionFault
     * @throws TopicExpressionDialectUnknownFault
     * @throws UnacceptableInitialTerminationTimeFault
     */
    @WebMethod(operationName = "Subscribe")
    @WebResult(name = "SubscribeResponse", 
               targetNamespace = "http://docs.oasis-open.org/wsn/b-1", 
               partName = "SubscribeResponse")
    public SubscribeResponse subscribe(
            @WebParam(name = "Subscribe", 
                      targetNamespace = "http://docs.oasis-open.org/wsn/b-1", 
                      partName = "SubscribeRequest")
            Subscribe subscribeRequest) throws InvalidFilterFault, InvalidMessageContentExpressionFault,
            InvalidProducerPropertiesExpressionFault, InvalidTopicExpressionFault, ResourceUnknownFault,
            SubscribeCreationFailedFault, TopicExpressionDialectUnknownFault, TopicNotSupportedFault,
            UnacceptableInitialTerminationTimeFault, UnsupportedPolicyRequestFault, UnrecognizedPolicyRequestFault {

        logger.debug("Subscribe");
        return handleSubscribe(subscribeRequest, null);
    }

    public SubscribeResponse handleSubscribe(
                Subscribe subscribeRequest, 
                EndpointManager manager) throws InvalidFilterFault, InvalidMessageContentExpressionFault,
            InvalidProducerPropertiesExpressionFault, InvalidTopicExpressionFault,
            SubscribeCreationFailedFault, TopicExpressionDialectUnknownFault,
            TopicNotSupportedFault, UnacceptableInitialTerminationTimeFault,
            UnsupportedPolicyRequestFault, UnrecognizedPolicyRequestFault {
        AbstractSubscription subscription = null;
        boolean success = false;
        try {
            subscription = createSubcription(idGenerator.generateSanitizedId());
            subscription.setBroker(this);
            subscriptions.put(subscription.getAddress(), subscription);
            subscription.create(subscribeRequest);
            if (manager != null) {
                subscription.setManager(manager);
            }
            subscription.register();
            SubscribeResponse response = new SubscribeResponse();
            response.setSubscriptionReference(AbstractWSAClient.createWSA(subscription.getAddress()));
            success = true;
            return response;
        } catch (EndpointRegistrationException e) {
            logger.warn("Unable to register new endpoint", e);
            SubscribeCreationFailedFaultType fault = new SubscribeCreationFailedFaultType();
            throw new SubscribeCreationFailedFault("Unable to register new endpoint", fault, e);
        } finally {
            if (!success && subscription != null) {
                subscriptions.remove(subscription);
                try {
                    subscription.unsubscribe();
                } catch (UnableToDestroySubscriptionFault e) {
                    logger.info("Error destroying subscription", e);
                }
            }
        }
    }

    public void unsubscribe(String address) throws UnableToDestroySubscriptionFault {
        AbstractSubscription subscription = (AbstractSubscription) subscriptions.remove(address);
        if (subscription != null) {
            subscription.unsubscribe();
        }
    }

    /**
     * 
     * @param getCurrentMessageRequest
     * @return returns org.oasis_open.docs.wsn.b_1.GetCurrentMessageResponse
     * @throws MultipleTopicsSpecifiedFault
     * @throws TopicNotSupportedFault
     * @throws InvalidTopicExpressionFault
     * @throws ResourceUnknownFault
     * @throws TopicExpressionDialectUnknownFault
     * @throws NoCurrentMessageOnTopicFault
     */
    @WebMethod(operationName = "GetCurrentMessage")
    @WebResult(name = "GetCurrentMessageResponse", 
               targetNamespace = "http://docs.oasis-open.org/wsn/b-1", 
               partName = "GetCurrentMessageResponse")
    public GetCurrentMessageResponse getCurrentMessage(
            @WebParam(name = "GetCurrentMessage", 
                      targetNamespace = "http://docs.oasis-open.org/wsn/b-1", 
                      partName = "GetCurrentMessageRequest")
            GetCurrentMessage getCurrentMessageRequest) throws InvalidTopicExpressionFault,
            MultipleTopicsSpecifiedFault, NoCurrentMessageOnTopicFault, ResourceUnknownFault,
            TopicExpressionDialectUnknownFault, TopicNotSupportedFault {

        logger.debug("GetCurrentMessage");
        NoCurrentMessageOnTopicFaultType fault = new NoCurrentMessageOnTopicFaultType();
        throw new NoCurrentMessageOnTopicFault("There is no current message on this topic.", fault);
    }

    /**
     * 
     * @param registerPublisherRequest
     * @return returns org.oasis_open.docs.wsn.br_1.RegisterPublisherResponse
     * @throws PublisherRegistrationRejectedFault
     * @throws InvalidTopicExpressionFault
     * @throws TopicNotSupportedFault
     * @throws ResourceUnknownFault
     * @throws PublisherRegistrationFailedFault
     */
    @WebMethod(operationName = "RegisterPublisher")
    @WebResult(name = "RegisterPublisherResponse", 
               targetNamespace = "http://docs.oasis-open.org/wsn/br-1", 
               partName = "RegisterPublisherResponse")
    public RegisterPublisherResponse registerPublisher(
            @WebParam(name = "RegisterPublisher", 
                      targetNamespace = "http://docs.oasis-open.org/wsn/br-1", 
                      partName = "RegisterPublisherRequest")
            RegisterPublisher registerPublisherRequest) throws InvalidTopicExpressionFault,
            PublisherRegistrationFailedFault, PublisherRegistrationRejectedFault, ResourceUnknownFault,
            TopicNotSupportedFault {

        logger.debug("RegisterPublisher");
        return handleRegisterPublisher(registerPublisherRequest, null);
    }

    public RegisterPublisherResponse handleRegisterPublisher(RegisterPublisher registerPublisherRequest,
            EndpointManager manager) throws InvalidTopicExpressionFault, PublisherRegistrationFailedFault,
            PublisherRegistrationRejectedFault, ResourceUnknownFault, TopicNotSupportedFault {
        AbstractPublisher publisher = null;
        boolean success = false;
        try {
            publisher = createPublisher(idGenerator.generateSanitizedId());
            publishers.put(publisher.getAddress(), publisher);
            if (manager != null) {
                publisher.setManager(manager);
            }
            publisher.register();
            publisher.create(registerPublisherRequest);
            RegisterPublisherResponse response = new RegisterPublisherResponse();
            response.setPublisherRegistrationReference(AbstractWSAClient.createWSA(publisher.getAddress()));
            success = true;
            return response;
        } catch (EndpointRegistrationException e) {
            logger.warn("Unable to register new endpoint", e);
            PublisherRegistrationFailedFaultType fault = new PublisherRegistrationFailedFaultType();
            throw new PublisherRegistrationFailedFault("Unable to register new endpoint", fault, e);
        } finally {
            if (!success && publisher != null) {
                publishers.remove(publisher.getAddress());
                try {
                    publisher.destroy();
                } catch (ResourceNotDestroyedFault e) {
                    logger.info("Error destroying publisher", e);
                }
            }
        }
    }

    protected abstract AbstractPublisher createPublisher(String name);

    protected abstract AbstractSubscription createSubcription(String name);

    @WebResult(name = "GetResourcePropertyResponse", targetNamespace = "http://docs.oasis-open.org/wsrf/rp-2", partName = "GetResourcePropertyResponse")
    @WebMethod(operationName = "GetResourceProperty")
    public GetResourcePropertyResponse getResourceProperty(
        @WebParam(partName = "GetResourcePropertyRequest", name = "GetResourceProperty", targetNamespace = "http://docs.oasis-open.org/wsrf/rp-2")
        javax.xml.namespace.QName getResourcePropertyRequest
    ) throws ResourceUnavailableFault, ResourceUnknownFault, InvalidResourcePropertyQNameFault {

        logger.debug("GetResourceProperty");
        return handleGetResourceProperty(getResourcePropertyRequest);
    }

    protected GetResourcePropertyResponse handleGetResourceProperty(QName property)
            throws ResourceUnavailableFault, ResourceUnknownFault, InvalidResourcePropertyQNameFault {
        InvalidResourcePropertyQNameFaultType fault = new InvalidResourcePropertyQNameFaultType();
        throw new InvalidResourcePropertyQNameFault("Invalid resource property QName: " + property, fault);
    }

}
