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
package org.apache.servicemix.saxon;

import org.apache.servicemix.common.BaseComponent;
import org.apache.servicemix.common.BaseLifeCycle;
import org.apache.servicemix.common.BaseServiceUnitManager;
import org.apache.servicemix.common.Deployer;
import org.apache.servicemix.common.ServiceUnit;
import org.apache.servicemix.common.xbean.BaseXBeanDeployer;
import org.apache.servicemix.common.xbean.XBeanServiceUnit;

/**
 * 
 * @org.apache.xbean.XBean element="component"
 *                  description="XSLT component"
 * @author <a href="mailto:gnodet [at] gmail.com">Guillaume Nodet</a>
 */
public class SaxonComponent extends BaseComponent {

    private SaxonEndpoint[] endpoints;

    /**
     * @return Returns the endpoints.
     */
    public SaxonEndpoint[] getEndpoints() {
        return endpoints;
    }

    /**
     * @param endpoints The endpoints to set.
     */
    public void setEndpoints(SaxonEndpoint[] endpoints) {
        this.endpoints = endpoints;
    }
    
    /* (non-Javadoc)
     * @see org.servicemix.common.BaseComponent#createLifeCycle()
     */
    protected BaseLifeCycle createLifeCycle() {
        return new LifeCycle();
    }

    /* (non-Javadoc)
     * @see org.servicemix.common.BaseComponent#createServiceUnitManager()
     */
    public BaseServiceUnitManager createServiceUnitManager() {
        Class[] classes = new Class[] { SaxonEndpoint.class };
        Deployer[] deployers = new Deployer[] { new BaseXBeanDeployer(this, classes) };
        return new BaseServiceUnitManager(this, deployers);
    }

    /**
     * @author <a href="mailto:gnodet [at] gmail.com">Guillaume Nodet</a>
     */
    public class LifeCycle extends BaseLifeCycle {

        protected ServiceUnit su;
        
        public LifeCycle() {
            super(SaxonComponent.this);
        }
        
        /* (non-Javadoc)
         * @see org.servicemix.common.BaseLifeCycle#doInit()
         */
        protected void doInit() throws Exception {
            super.doInit();
            if (endpoints != null && endpoints.length > 0) {
                su = new XBeanServiceUnit();
                su.setComponent(SaxonComponent.this);
                for (int i = 0; i < endpoints.length; i++) {
                    endpoints[i].setServiceUnit(su);
                    endpoints[i].validate();
                    su.addEndpoint(endpoints[i]);
                }
                getRegistry().registerServiceUnit(su);
            }
        }

        /* (non-Javadoc)
         * @see org.servicemix.common.BaseLifeCycle#doStart()
         */
        protected void doStart() throws Exception {
            super.doStart();
            if (su != null) {
                su.start();
            }
        }
        
        /* (non-Javadoc)
         * @see org.servicemix.common.BaseLifeCycle#doStop()
         */
        protected void doStop() throws Exception {
            if (su != null) {
                su.stop();
            }
            super.doStop();
        }
        
        /* (non-Javadoc)
         * @see org.servicemix.common.BaseLifeCycle#doShutDown()
         */
        protected void doShutDown() throws Exception {
            if (su != null) {
                su.shutDown();
            }
            super.doShutDown();
        }
    }

}
