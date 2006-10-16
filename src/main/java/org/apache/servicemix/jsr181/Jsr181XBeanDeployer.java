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
package org.apache.servicemix.jsr181;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.servicemix.common.BaseLifeCycle;
import org.apache.servicemix.common.xbean.BaseXBeanDeployer;
import org.apache.servicemix.common.xbean.ParentBeanFactoryPostProcessor;

public class Jsr181XBeanDeployer extends BaseXBeanDeployer {

    public Jsr181XBeanDeployer(Jsr181Component component) {
        super(component, Jsr181Endpoint.class);
    }

    protected List getBeanFactoryPostProcessors(String serviceUnitRootPath) {
        Map beans = new HashMap();
        beans.put("context", new EndpointComponentContext(((BaseLifeCycle) component.getLifeCycle()).getContext()));
        List processors = new ArrayList(super.getBeanFactoryPostProcessors(serviceUnitRootPath));
        processors.add(new ParentBeanFactoryPostProcessor(beans));
        return processors;
    }

}
