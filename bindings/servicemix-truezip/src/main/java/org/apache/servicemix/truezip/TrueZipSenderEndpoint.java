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
package org.apache.servicemix.truezip;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import javax.jbi.management.DeploymentException;
import javax.jbi.messaging.MessageExchange;
import javax.jbi.messaging.NormalizedMessage;
import javax.jbi.servicedesc.ServiceEndpoint;

import de.schlichtherle.io.File;
import de.schlichtherle.io.FileOutputStream;

import org.apache.servicemix.common.endpoints.ProviderEndpoint;
import org.apache.servicemix.components.util.DefaultFileMarshaler;
import org.apache.servicemix.components.util.FileMarshaler;
import org.apache.servicemix.id.IdGenerator;

/**
 * An endpoint which receives a message and writes the content to a file.
 * 
 * @org.apache.xbean.XBean element="sender"
 * 
 * @version $Revision$
 */
public class TrueZipSenderEndpoint extends ProviderEndpoint implements TrueZipEndpointType {

    private File directory;

    private FileMarshaler marshaler = new DefaultFileMarshaler();

    private String tempFilePrefix = "servicemix-";

    private String tempFileSuffix = ".xml";

    private boolean autoCreateDirectory = true;

    private IdGenerator idGenerator;

    public TrueZipSenderEndpoint() {
    }

    public TrueZipSenderEndpoint(TrueZipComponent component, ServiceEndpoint endpoint) {
        super(component, endpoint);
    }

    public void validate() throws DeploymentException {
        super.validate();
        if (directory == null) {
            throw new DeploymentException("You must specify the directory property");
        }
        if (isAutoCreateDirectory()) {
            directory.mkdirs();
        }
        if (!directory.isDirectory()) {
            throw new DeploymentException("The directory property must be a directory but was: " + directory);
        }
    }

    protected void processInOnly(MessageExchange exchange, NormalizedMessage in) throws Exception {
        OutputStream out = null;
        try {
            String name = marshaler.getOutputName(exchange, in);
            File newFile = null;
            if (name == null) {
                newFile = new File(directory, getNewTempName());
            } else {
                newFile = new File(directory, name);
            }
            logger.debug("Writing to file: {}", newFile.getCanonicalPath());
            out = new BufferedOutputStream(new FileOutputStream(newFile));
            marshaler.writeMessage(exchange, in, out, name);
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    logger.error("Caught exception while closing stream on error: {}" + e.getMessage(), e);
                }
            }
        }
    }

    protected String getNewTempName() {
        if (idGenerator == null) {
            idGenerator = new IdGenerator(tempFilePrefix);
        }
        return idGenerator.generateSanitizedId() + tempFileSuffix;
    }

    protected void processInOut(MessageExchange exchange, NormalizedMessage in, NormalizedMessage out) throws Exception {
        /** TODO list the files? */
        super.processInOut(exchange, in, out);
    }

    // Properties
    // -------------------------------------------------------------------------
    public java.io.File getDirectory() {
        return directory;
    }

    public void setDirectory(java.io.File directory) {
        this.directory = new File(directory);
    }

    public FileMarshaler getMarshaler() {
        return marshaler;
    }

    public void setMarshaler(FileMarshaler marshaler) {
        this.marshaler = marshaler;
    }

    public String getTempFilePrefix() {
        return tempFilePrefix;
    }

    public void setTempFilePrefix(String tempFilePrefix) {
        this.tempFilePrefix = tempFilePrefix;
    }

    public String getTempFileSuffix() {
        return tempFileSuffix;
    }

    public void setTempFileSuffix(String tempFileSuffix) {
        this.tempFileSuffix = tempFileSuffix;
    }

    public boolean isAutoCreateDirectory() {
        return autoCreateDirectory;
    }

    public void setAutoCreateDirectory(boolean autoCreateDirectory) {
        this.autoCreateDirectory = autoCreateDirectory;
    }

}
