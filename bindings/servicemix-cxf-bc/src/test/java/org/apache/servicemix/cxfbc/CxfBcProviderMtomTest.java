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

import java.io.File;
import java.net.URL;

import javax.jbi.messaging.InOut;
import javax.xml.namespace.QName;
import javax.xml.ws.soap.SOAPBinding;

import org.apache.cxf.calculator.CalculatorImpl;
import org.apache.cxf.calculator.CalculatorPortType;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.interceptor.LoggingOutInterceptor;
import org.apache.cxf.jaxws.EndpointImpl;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.apache.cxf.service.model.ServiceInfo;
import org.apache.servicemix.client.DefaultServiceMixClient;
import org.apache.servicemix.cxfbc.mtom.TestMtomImpl;
import org.apache.servicemix.cxfse.CxfSeComponent;
import org.apache.servicemix.jbi.jaxp.SourceTransformer;
import org.apache.servicemix.jbi.jaxp.StringSource;
import org.springframework.context.support.AbstractXmlApplicationContext;


public class CxfBcProviderMtomTest extends CxfBcSpringTestSupport {

    private DefaultServiceMixClient client;
    private InOut io;
    private CxfSeComponent component;
 
    protected void setUp() throws Exception {
        super.setUp();
        
        component = new CxfSeComponent();
        jbi.activateComponent(component, "CxfSeComponent");
        //Deploy proxy SU
        component.getServiceUnitManager().deploy("proxy", getServiceUnitPath("provider"));
        component.getServiceUnitManager().init("proxy", getServiceUnitPath("provider"));
        component.getServiceUnitManager().start("proxy");
    }
    
    protected void tearDown() throws Exception {
        component.getServiceUnitManager().stop("proxy");
        component.getServiceUnitManager().shutDown("proxy");
        component.getServiceUnitManager().undeploy("proxy", getServiceUnitPath("provider"));
    }
    
    
    public void testMtom() throws Exception {
        //start external service
        EndpointImpl endpointMtom =
            (EndpointImpl)javax.xml.ws.Endpoint.publish("http://localhost:9001/mtombridgetest", 
                new TestMtomImpl());
             
        SOAPBinding binding = (SOAPBinding)endpointMtom.getBinding();
        binding.setMTOMEnabled(true);
        endpointMtom.getInInterceptors().add(new LoggingInInterceptor());
        endpointMtom.getOutInterceptors().add(new LoggingOutInterceptor());
        client = new DefaultServiceMixClient(jbi);
        io = client.createInOutExchange();
        io.setService(new QName("http://apache.org/hello_world_soap_http", "SOAPServiceProvider"));
        io.setInterfaceName(new QName("http://apache.org/hello_world_soap_http", "Greeter"));
        io.setOperation(new QName("http://apache.org/hello_world_soap_http", "greetMe"));
        //send message to proxy
        io.getInMessage().setContent(new StringSource(
                "<message xmlns='http://java.sun.com/xml/ns/jbi/wsdl-11-wrapper'>"
              + "<part> "
              + "<greetMe xmlns='http://apache.org/hello_world_soap_http/types'><requestType>"
              + "ffang with mtom"
              + "</requestType></greetMe>"
              + "</part> "
              + "</message>"));
        client.sendSync(io);
        assertTrue(new SourceTransformer().contentToString(
                io.getOutMessage()).indexOf("testfoobar") >= 0);
        client.done(io);
    }
    
    public void testMtomWithException() throws Exception {
      //start external service
        JaxWsServerFactoryBean factory = new JaxWsServerFactoryBean();
        factory.setServiceClass(CalculatorPortType.class);
        factory.setServiceBean(new CalculatorImpl());
        String address = "http://localhost:9001/providertest";
        factory.setAddress(address);
        factory.setBindingId("http://schemas.xmlsoap.org/wsdl/soap12/");
        Server server = factory.create();
        Endpoint endpoint = server.getEndpoint();
        endpoint.getInInterceptors().add(new LoggingInInterceptor());
        endpoint.getOutInterceptors().add(new LoggingOutInterceptor());
        ServiceInfo service = endpoint.getEndpointInfo().getService();
        assertNotNull(service);
        client = new DefaultServiceMixClient(jbi);
        io = client.createInOutExchange();
        io.setService(new QName("http://apache.org/hello_world_soap_http", "SOAPServiceProvider"));
        io.setInterfaceName(new QName("http://apache.org/hello_world_soap_http", "Greeter"));
        //send message to proxy
        io.getInMessage().setContent(new StringSource(
                "<message xmlns='http://java.sun.com/xml/ns/jbi/wsdl-11-wrapper'>"
              + "<part> "
              + "<greetMe xmlns='http://apache.org/hello_world_soap_http/types'><requestType>"
              + "ffang with mtom exception"
              + "</requestType></greetMe>"
              + "</part> "
              + "</message>"));
        client.sendSync(io);
        assertTrue(new SourceTransformer().contentToString(
                io.getOutMessage()).indexOf("Hello ffang with mtom exception Negative number cant be added!") >= 0);
        client.done(io);
    }
    
       
    @Override
    protected AbstractXmlApplicationContext createBeanFactory() {
        return new ClassPathXmlApplicationContext("org/apache/servicemix/cxfbc/provider.xml");
    }

    protected String getServiceUnitPath(String name) {
        URL url = getClass().getClassLoader().getResource("org/apache/servicemix/cxfbc/" + name + "/xbean.xml");
        File path = new File(url.getFile());
        path = path.getParentFile();
        return path.getAbsolutePath();
    }
}
