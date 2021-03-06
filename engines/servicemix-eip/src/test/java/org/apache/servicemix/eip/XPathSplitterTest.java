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
package org.apache.servicemix.eip;

import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.ExchangeStatus;
import javax.jbi.messaging.InOnly;
import javax.jbi.messaging.InOut;
import javax.xml.namespace.QName;

import org.apache.servicemix.eip.patterns.XPathSplitter;
import org.apache.servicemix.eip.support.ExchangeTarget;
import org.apache.servicemix.tck.ReceiverComponent;

public class XPathSplitterTest extends AbstractEIPTest {

    private XPathSplitter splitter;

    protected void setUp() throws Exception {
        super.setUp();

        splitter = new XPathSplitter();
        splitter.setTarget(createServiceExchangeTarget(new QName("target")));
        splitter.setXPath("/hello/*");
        configurePattern(splitter);
        activateComponent(splitter, "splitter");
    }
    
    public void testInOnly() throws Exception {
        ReceiverComponent rec = activateReceiver("target");
        
        InOnly me = client.createInOnlyExchange();
        me.setService(new QName("splitter"));
        me.getInMessage().setContent(createSource("<hello><one/><two/><three/></hello>"));
        client.sendSync(me);
        assertEquals(ExchangeStatus.DONE, me.getStatus());
        
        rec.getMessageList().assertMessagesReceived(3); 
    }

    public void testInOut() throws Exception {
        InOut me = client.createInOutExchange();
        me.setService(new QName("splitter"));
        me.getInMessage().setContent(createSource("<hello><one/><two/><three/></hello>"));
        client.sendSync(me);
        assertEquals(ExchangeStatus.ERROR, me.getStatus());
    }

    public void testInOnlyWithoutErrors() throws Exception {
        ReceiverComponent rec = activateReceiver("target");

        splitter.setReportErrors(true);
        InOnly me = client.createInOnlyExchange();
        me.setService(new QName("splitter"));
        me.getInMessage().setContent(createSource("<hello><one/><two/><three/></hello>"));
        client.sendSync(me);
        assertEquals(ExchangeStatus.DONE, me.getStatus());

        rec.getMessageList().assertMessagesReceived(3);
    }

    public void testInOnlyWithErrors() throws Exception {
        activateComponent(new ReturnErrorComponent(), "target");

        splitter.setReportErrors(true);
        InOnly me = client.createInOnlyExchange();
        me.setService(new QName("splitter"));
        me.getInMessage().setContent(createSource("<hello><one/><two/><three/></hello>"));
        client.sendSync(me);
        assertEquals(ExchangeStatus.ERROR, me.getStatus());
    }

    // Test validate() with null target
    public void testValidateNullTarget() throws Exception {
    	ExchangeTarget target = null;
    	splitter.setTarget(target);
    	
    	try {
    		splitter.validate();
    		fail("ExchangeTarget is null, validate should throw an exception");
    	} catch (IllegalArgumentException iae) {
    		// test succeeds
    	}
    }
    
    // Test validate() with null XPath expression
    public void testValidateNullXPathExpression() throws Exception {
        XPathSplitter badSplitter = new XPathSplitter();
        badSplitter.setTarget(createServiceExchangeTarget(new QName("target")));
    	badSplitter.setXPath(null);
    	
    	try {
    		badSplitter.validate();
    		fail("XPath expression is null, validate should throw an exception");
    	} catch (DeploymentException de) {
    		// test succeeds
    	}
    }
}
