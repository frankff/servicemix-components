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
package org.apache.servicemix.cxfbc;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.MessagingException;
import javax.jbi.messaging.NormalizedMessage;
import javax.wsdl.WSDLException;
import javax.wsdl.factory.WSDLFactory;
import javax.wsdl.xml.WSDLReader;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.ibm.wsdl.Constants;

import org.apache.cxf.Bus;
import org.apache.cxf.binding.AbstractBindingFactory;

import org.apache.cxf.binding.jbi.JBIConstants;
import org.apache.cxf.binding.jbi.JBIFault;

import org.apache.cxf.binding.soap.interceptor.SoapActionOutInterceptor;
import org.apache.cxf.binding.soap.interceptor.SoapOutInterceptor;
import org.apache.cxf.binding.soap.interceptor.SoapPreProtocolOutInterceptor;
import org.apache.cxf.bus.spring.SpringBusFactory;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.endpoint.EndpointImpl;
import org.apache.cxf.interceptor.AttachmentOutInterceptor;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.interceptor.Interceptor;
import org.apache.cxf.interceptor.StaxOutInterceptor;

import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseChainCache;
import org.apache.cxf.phase.PhaseInterceptorChain;
import org.apache.cxf.phase.PhaseManager;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.model.BindingOperationInfo;
import org.apache.cxf.service.model.EndpointInfo;
import org.apache.cxf.service.model.SchemaInfo;
import org.apache.cxf.service.model.ServiceInfo;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.ConduitInitiator;
import org.apache.cxf.transport.ConduitInitiatorManager;
import org.apache.cxf.transport.jbi.JBIMessageHelper;
import org.apache.cxf.wsdl.WSDLManager;
import org.apache.cxf.wsdl11.SchemaUtil;
import org.apache.cxf.wsdl11.ServiceWSDLBuilder;
import org.apache.cxf.wsdl11.WSDLServiceBuilder;
import org.apache.cxf.wsdl11.WSDLServiceFactory;
import org.apache.servicemix.JbiConstants;
import org.apache.servicemix.common.endpoints.ProviderEndpoint;
import org.apache.servicemix.cxfbc.interceptors.JbiOutInterceptor;
import org.apache.servicemix.cxfbc.interceptors.JbiOutWsdl1Interceptor;
import org.apache.servicemix.cxfbc.interceptors.MtomCheckInterceptor;
import org.apache.servicemix.soap.util.DomUtil;
import org.springframework.core.io.Resource;

/**
 * 
 * @author gnodet
 * @org.apache.xbean.XBean element="provider"
 */
public class CxfBcProvider extends ProviderEndpoint implements
        CxfBcEndpointWithInterceptor {

    List<Interceptor> in = new CopyOnWriteArrayList<Interceptor>();

    List<Interceptor> out = new CopyOnWriteArrayList<Interceptor>();

    List<Interceptor> outFault = new CopyOnWriteArrayList<Interceptor>();

    List<Interceptor> inFault = new CopyOnWriteArrayList<Interceptor>();

    private Resource wsdl;

    private String busCfg;

    private Bus bus;

    private ConduitInitiator conduitInit;

    private URI locationURI;

    private EndpointInfo ei;

    private Endpoint ep;

    private Service cxfService;

    private boolean mtomEnabled;

    private boolean useJBIWrapper = true;

    public void processExchange(MessageExchange exchange) {

    }

    public void process(MessageExchange exchange) throws Exception {

        if (exchange.getStatus() != ExchangeStatus.ACTIVE) {
            return;
        }
        NormalizedMessage nm = exchange.getMessage("in");

        Object newDestinationURI = nm.getProperty(JbiConstants.HTTP_DESTINATION_URI);
        if (newDestinationURI != null) {
            ei.setAddress((String) newDestinationURI);
        }
        
        Conduit conduit = conduitInit.getConduit(ei);
        CxfBcProviderMessageObserver obs = new CxfBcProviderMessageObserver(
                exchange, this);
        conduit.setMessageObserver(obs);
        Message message = ep.getBinding().createMessage();
        message.put(MessageExchange.class, exchange);
        Exchange cxfExchange = new ExchangeImpl();

        message.setExchange(cxfExchange);
        cxfExchange.setOutMessage(message);

        QName opeName = exchange.getOperation();
        BindingOperationInfo boi = null;
        if (opeName == null) {
            // if interface only have one operation, may not specify the opeName
            // in MessageExchange
            if (ei.getBinding().getOperations().size() == 1) {
                boi = ei.getBinding().getOperations().iterator().next();
            } else {
                throw new Fault(new Exception(
                        "Operation not bound on this MessageExchange"));

            }
        } else {
            boi = ei.getBinding().getOperation(exchange.getOperation());
        }
        cxfExchange.setOneWay(boi.getOperationInfo().isOneWay());
        cxfExchange.put(BindingOperationInfo.class, boi);
        cxfExchange.put(Endpoint.class, ep);
        cxfExchange.put(Service.class, cxfService);
        PhaseChainCache outboundChainCache = new PhaseChainCache();
        PhaseManager pm = getBus().getExtension(PhaseManager.class);
        List<Interceptor> outList = new ArrayList<Interceptor>();
        if (isMtomEnabled()) {
            outList.add(new JbiOutInterceptor());
            outList.add(new MtomCheckInterceptor(true));
            outList.add(new AttachmentOutInterceptor());

        }

        outList.add(new JbiOutInterceptor());
        outList.add(new JbiOutWsdl1Interceptor(isUseJBIWrapper()));
        outList.add(new SoapPreProtocolOutInterceptor());
        outList.add(new SoapOutInterceptor(getBus()));
        outList.add(new SoapActionOutInterceptor());
        PhaseInterceptorChain outChain = outboundChainCache.get(pm
                .getOutPhases(), outList);
        outChain.add(getOutInterceptors());
        outChain.add(getOutFaultInterceptors());
        message.setInterceptorChain(outChain);
        InputStream is = JBIMessageHelper.convertMessageToInputStream(nm
                .getContent());

        StreamSource source = new StreamSource(is);
        message.setContent(Source.class, source);

        message.setContent(InputStream.class, is);

        conduit.prepare(message);
        OutputStream os = message.getContent(OutputStream.class);
        XMLStreamWriter writer = message.getContent(XMLStreamWriter.class);

        String encoding = getEncoding(message);

        try {
            writer = StaxOutInterceptor.getXMLOutputFactory(message)
                    .createXMLStreamWriter(os, encoding);
        } catch (XMLStreamException e) {
            //
        }
        message.setContent(XMLStreamWriter.class, writer);
        message.put(org.apache.cxf.message.Message.REQUESTOR_ROLE, true);
        try {
            outChain.doIntercept(message);
            //Check to see if there is a Fault from the outgoing chain
            Exception ex = message.getContent(Exception.class);
            if (ex != null) {
                throw ex;
            }
            ex = message.getExchange().get(Exception.class);
            if (ex != null) {
                throw ex;
            }
            
            XMLStreamWriter xtw = message.getContent(XMLStreamWriter.class);
            if (xtw != null) {
                xtw.writeEndDocument();
                xtw.close();
            }

            os.flush();
            is.close();
            os.close();
        } catch (Exception e) {
            faultProcess(exchange, message, e);
        }

    }

    private void faultProcess(MessageExchange exchange, Message message, Exception e) throws MessagingException {
        javax.jbi.messaging.Fault fault = exchange.createFault();
        if (e.getCause() != null) {
            handleJBIFault(message, e.getCause().getMessage());
        } else {
            handleJBIFault(message, e.getMessage());
        }
        fault.setContent(message.getContent(Source.class));
        exchange.setFault(fault);
        boolean txSync = exchange.getStatus() == ExchangeStatus.ACTIVE
                && exchange.isTransacted()
                && Boolean.TRUE.equals(exchange
                        .getProperty(JbiConstants.SEND_SYNC));
        if (txSync) {
            getContext().getDeliveryChannel().sendSync(exchange);
        } else {
            getContext().getDeliveryChannel().send(exchange);
        }
    }

   
    private void handleJBIFault(Message message, String detail) {
        Document doc = DomUtil.createDocument();
        Element jbiFault = DomUtil.createElement(doc, new QName(
                JBIConstants.NS_JBI_BINDING, JBIFault.JBI_FAULT_ROOT));
        Node jbiFaultDetail = DomUtil.createElement(jbiFault, new QName("", JBIFault.JBI_FAULT_DETAIL));
        jbiFaultDetail.setTextContent(detail);
        jbiFault.appendChild(jbiFaultDetail);
        message.setContent(Source.class, new DOMSource(doc));
        message.put("jbiFault", true);
    }
    
    public List<Interceptor> getOutFaultInterceptors() {
        return outFault;
    }

    public List<Interceptor> getInFaultInterceptors() {
        return inFault;
    }

    public List<Interceptor> getInInterceptors() {
        return in;
    }

    public List<Interceptor> getOutInterceptors() {
        return out;
    }

    public void setInInterceptors(List<Interceptor> interceptors) {
        in = interceptors;
    }

    public void setInFaultInterceptors(List<Interceptor> interceptors) {
        inFault = interceptors;
    }

    public void setOutInterceptors(List<Interceptor> interceptors) {
        out = interceptors;
    }

    public void setOutFaultInterceptors(List<Interceptor> interceptors) {
        outFault = interceptors;
    }

    public void setWsdl(Resource wsdl) {
        this.wsdl = wsdl;
    }

    public Resource getWsdl() {
        return wsdl;
    }

    @Override
    public void validate() throws DeploymentException {
        try {
            if (definition == null) {
                if (wsdl == null) {
                    throw new DeploymentException("wsdl property must be set");
                }
                description = DomUtil.parse(wsdl.getInputStream());
                WSDLFactory wsdlFactory = WSDLFactory.newInstance();
                WSDLReader reader = wsdlFactory.newWSDLReader();
                reader.setFeature(Constants.FEATURE_VERBOSE, false);
                try {
                    // use wsdl manager to parse wsdl or get cached definition
                    definition = getBus().getExtension(WSDLManager.class)
                            .getDefinition(wsdl.getURL());

                } catch (WSDLException ex) {
                    // 
                }
                WSDLServiceFactory factory = new WSDLServiceFactory(getBus(),
                        definition, service);
                cxfService = factory.create();
                ei = cxfService.getServiceInfos().iterator().next()
                        .getEndpoints().iterator().next();

                for (ServiceInfo serviceInfo : cxfService.getServiceInfos()) {
                    if (serviceInfo.getName().equals(service)
                            && getEndpoint() != null
                            && serviceInfo
                                    .getEndpoint(new QName(serviceInfo
                                            .getName().getNamespaceURI(),
                                            getEndpoint())) != null) {
                        ei = serviceInfo.getEndpoint(new QName(serviceInfo
                                .getName().getNamespaceURI(), getEndpoint()));

                    }
                }
                // transform import xsd to inline xsd
                ServiceWSDLBuilder swBuilder = new ServiceWSDLBuilder(getBus(),
                        cxfService.getServiceInfos());
                ServiceInfo serInfo = new ServiceInfo();

                Map<String, Element> schemaList = new HashMap<String, Element>();
                SchemaUtil schemaUtil = new SchemaUtil(bus, schemaList);
                schemaUtil.getSchemas(definition, serInfo);

                serInfo = ei.getService();
                for (String key : schemaList.keySet()) {
                    Element ele = schemaList.get(key);
                    for (SchemaInfo sInfo : serInfo.getSchemas()) {
                        Node nl = sInfo.getElement().getElementsByTagNameNS(
                                "http://www.w3.org/2001/XMLSchema", "import")
                                .item(0);
                        if (sInfo.getNamespaceURI() == null // it's import
                                                            // schema
                                && nl != null
                                && ((Element) nl)
                                        .getAttribute("namespace")
                                        .equals(
                                                ele
                                                        .getAttribute("targetNamespace"))) {

                            sInfo.setElement(ele);
                        }
                    }
                }
                serInfo.setProperty(WSDLServiceBuilder.WSDL_DEFINITION, null);
                description = WSDLFactory.newInstance().newWSDLWriter()
                        .getDocument(swBuilder.build());

                if (endpoint == null) {
                    endpoint = ei.getName().getLocalPart();
                }
                ei.getBinding().setProperty(
                        AbstractBindingFactory.DATABINDING_DISABLED,
                        Boolean.TRUE);

                ep = new EndpointImpl(getBus(), cxfService, ei);

                // init transport
                if (locationURI != null) {
                    ei.setAddress(locationURI.toString());
                }

                ConduitInitiatorManager conduitMgr = getBus().getExtension(
                        ConduitInitiatorManager.class);
                conduitInit = conduitMgr.getConduitInitiator(ei
                        .getTransportId());
                super.validate();
            }
        } catch (DeploymentException e) {
            throw e;
        } catch (Exception e) {
            throw new DeploymentException(e);
        }
    }

    @Override
    public void start() throws Exception {
        super.start();

    }

    protected Bus getBus() {
        if (getBusCfg() != null) {
            if (bus == null) {
                SpringBusFactory bf = new SpringBusFactory();
                bus = bf.createBus(getBusCfg());
            }
            return bus;
        } else {
            return ((CxfBcComponent) getServiceUnit().getComponent()).getBus();
        }
    }

    public void setBusCfg(String busCfg) {
        this.busCfg = busCfg;
    }

    public String getBusCfg() {
        return busCfg;
    }

    public void setLocationURI(URI locationURI) {
        this.locationURI = locationURI;
    }

    public URI getLocationURI() {
        return locationURI;
    }

    private String getEncoding(Message message) {
        Exchange ex = message.getExchange();
        String encoding = (String) message.get(Message.ENCODING);
        if (encoding == null && ex.getInMessage() != null) {
            encoding = (String) ex.getInMessage().get(Message.ENCODING);
            message.put(Message.ENCODING, encoding);
        }

        if (encoding == null) {
            encoding = "UTF-8";
            message.put(Message.ENCODING, encoding);
        }
        return encoding;
    }

    Endpoint getCxfEndpoint() {
        return this.ep;
    }

    EndpointInfo getEndpointInfo() {
        return this.ei;
    }

    public void setMtomEnabled(boolean mtomEnabled) {
        this.mtomEnabled = mtomEnabled;
    }

    public boolean isMtomEnabled() {
        return mtomEnabled;
    }

    public void setUseJBIWrapper(boolean useJBIWrapper) {
        this.useJBIWrapper = useJBIWrapper;
    }

    public boolean isUseJBIWrapper() {
        return useJBIWrapper;
    }

}