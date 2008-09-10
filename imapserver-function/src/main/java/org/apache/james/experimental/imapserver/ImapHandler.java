/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.experimental.imapserver;

import org.apache.avalon.cornerstone.services.connection.ConnectionHandler;
import org.apache.avalon.framework.container.ContainerUtil;
import org.apache.avalon.framework.logger.Logger;
import org.apache.james.Constants;
import org.apache.james.api.imap.ImapConstants;
import org.apache.james.api.imap.process.ImapProcessor;
import org.apache.james.api.imap.process.ImapSession;
import org.apache.james.experimental.imapserver.encode.writer.OutputStreamImapResponseWriter;
import org.apache.james.imapserver.codec.decode.ImapDecoder;
import org.apache.james.imapserver.codec.encode.ImapEncoder;
import org.apache.james.imapserver.codec.encode.ImapResponseComposer;
import org.apache.james.imapserver.codec.encode.base.ImapResponseComposerImpl;
import org.apache.james.socket.ProtocolHandler;
import org.apache.james.socket.ProtocolHandlerHelper;

import java.io.IOException;
import java.net.Socket;

/**
 * Handles IMAP connections.
 */
public class ImapHandler implements ProtocolHandler, ImapConstants
{
    
    private ProtocolHandlerHelper helper;

    private static final byte[] EMERGENCY_SIGNOFF = {'*',' ', 'B', 'Y', 'E', ' ', 
        'S', 'e', 'r', 'v', 'e', 'r', ' ', 'f', 'a', 'u', 'l', 't', '\r', '\n'};

    // TODO: inject dependency
    private String softwaretype = "JAMES "+VERSION+" Server " + Constants.SOFTWARE_VERSION;
    private ImapRequestHandler requestHandler;
    private ImapSession session;

    /**
     * The per-service configuration data that applies to all handlers
     */
    private ImapHandlerConfigurationData theConfigData;

    /**
     * Set the configuration data for the handler.
     *
     * @param theData the configuration data
     */
    public void setConfigurationData( Object theData )
    {
        if (theData instanceof ImapHandlerConfigurationData) {
            theConfigData = (ImapHandlerConfigurationData) theData;
            final ImapEncoder imapEncoder = theConfigData.getImapEncoder();
            final ImapProcessor imapProcessor = theConfigData.getImapProcessor();
            final ImapDecoder imapDecoder = theConfigData.getImapDecoder();
            requestHandler = new ImapRequestHandler(imapDecoder, imapProcessor, imapEncoder);
        } else {
            throw new IllegalArgumentException("Configuration object does not implement POP3HandlerConfigurationData");
        }
    }

    /**
     * Resets the handler data to a basic state.
     */
    public void resetHandler() {
        
        // Clear user data
        try {
            if (session != null) {
                session.logout();
            }
        } catch (Exception e) {
            getLogger().warn("Failed to close mailbox: " + e.getMessage());
            getLogger().debug(e.getMessage(), e);
        }
        session = null;
    }

    /**
     * @see ConnectionHandler#handleConnection(Socket)
     */
    public void handleProtocol() throws IOException {
            final OutputStreamImapResponseWriter writer = new OutputStreamImapResponseWriter( helper.getOutputStream() );
            ImapResponseComposer response = new ImapResponseComposerImpl( writer);

            // Write welcome message
                 
            response.okResponse(null, softwaretype + " Server "
                    + theConfigData.getHelloName() + " is ready.");

            session = new ImapSessionImpl();
            
            ContainerUtil.enableLogging(session, getLogger());

            helper.getWatchdog().start();
            while ( handleRequest() ) {
                helper.getWatchdog().reset();
            }
            helper.getWatchdog().stop();
            
            getLogger().info(
                    "Connection from " + helper.getRemoteHost() + " (" + helper.getRemoteIP()
                            + ") closed.");
    }

    private boolean handleRequest() {
        final boolean result = requestHandler.handleRequest( helper.getInputStream(), helper.getOutputStream(), session );
        return result;
    }
    
    /**
     * Method which will be called on error
     *  
     * @param e the RuntimeException
     */
    public void errorHandler(RuntimeException e) {
        try {
            helper.getOutputStream().write(EMERGENCY_SIGNOFF);
        } catch (Throwable t) {
            getLogger().debug("Write emergency signoff failed.", t);
        }
        helper.defaultErrorHandler(e);
    }
    
    public Logger getLogger() {
        return helper.getAvalonLogger();
    }

    public void setProtocolHandlerHelper(ProtocolHandlerHelper phh) {
        this.helper = phh;
    }

}
