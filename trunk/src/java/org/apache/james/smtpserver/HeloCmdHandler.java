/***********************************************************************
 * Copyright (c) 1999-2006 The Apache Software Foundation.             *
 * All rights reserved.                                                *
 * ------------------------------------------------------------------- *
 * Licensed under the Apache License, Version 2.0 (the "License"); you *
 * may not use this file except in compliance with the License. You    *
 * may obtain a copy of the License at:                                *
 *                                                                     *
 *     http://www.apache.org/licenses/LICENSE-2.0                      *
 *                                                                     *
 * Unless required by applicable law or agreed to in writing, software *
 * distributed under the License is distributed on an "AS IS" BASIS,   *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or     *
 * implied.  See the License for the specific language governing       *
 * permissions and limitations under the License.                      *
 ***********************************************************************/

package org.apache.james.smtpserver;


import java.net.UnknownHostException;
import org.apache.avalon.framework.configuration.Configurable;
import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.logger.AbstractLogEnabled;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.avalon.framework.service.ServiceManager;
import org.apache.avalon.framework.service.Serviceable;
import org.apache.james.services.DNSServer;
import org.apache.james.util.mail.dsn.DSNStatus;


/**
  * Handles HELO command
  */
public class HeloCmdHandler extends AbstractLogEnabled implements CommandHandler,Configurable, Serviceable {

    /**
     * The name of the command handled by the command handler
     */
    private final static String COMMAND_NAME = "HELO";

    /**
     * set checkValidHelo to false as default value
     */
    private boolean checkResolvableHelo = false;
    
    private boolean checkReverseEqualsHelo = false;
    
    private boolean checkAuthNetworks = false;
    
    private DNSServer dnsServer = null;
    
    /**
     * @see org.apache.avalon.framework.configuration.Configurable#configure(Configuration)
     */
    public void configure(Configuration handlerConfiguration) throws ConfigurationException {
        Configuration configuration = handlerConfiguration.getChild("checkResolvableHelo",false);
        if(configuration != null) {
           setCheckResolvableHelo(configuration.getValueAsBoolean(false));
        }
          
        Configuration config = handlerConfiguration.getChild(
                "checkReverseEqualsHelo", false);
        if (config != null) {
            setCheckReverseEqualsHelo(config.getValueAsBoolean(false));
        }
        
        Configuration configRelay = handlerConfiguration.getChild("checkAuthNetworks",false);
        if(configRelay != null) {
            setCheckAuthNetworks(configRelay.getValueAsBoolean(false));
        }
        
    }
    
    /**
     * @see org.apache.avalon.framework.service.Serviceable#service(ServiceManager)
     */
    public void service(ServiceManager serviceMan) throws ServiceException {
        DNSServer dnsServer = (DNSServer) serviceMan.lookup(DNSServer.ROLE);
        setDNSServer(dnsServer);
    }
    
    /**
     * Set to true to enable check for resolvable EHLO
     * 
     * @param checkResolvableHelo Set to true for enable check
     */
    public void setCheckResolvableHelo(boolean checkResolvableHelo) {
        this.checkResolvableHelo = checkResolvableHelo;
    }
    
    /**
     * Set to true to enable check for reverse equal HELO
     * 
     * @param checkReverseEqualsHelo
     *            Set to true for enable check
     */
    public void setCheckReverseEqualsHelo(boolean checkReverseEqualsHelo) {
        this.checkReverseEqualsHelo = checkReverseEqualsHelo;
    }

    /**
     * Set to true if AuthNetworks should be included in the EHLO check
     * 
     * @param checkAuthNetworks Set to true to enable
     */
    public void setCheckAuthNetworks(boolean checkAuthNetworks) {
        this.checkAuthNetworks = checkAuthNetworks;
    }
    
    /**
     * Set the DNSServer
     * 
     * @param dnsServer The DNSServer
     */
    public void setDNSServer(DNSServer dnsServer) {
        this.dnsServer = dnsServer;
    }
      
    /*
     * process HELO command
     *
     * @see org.apache.james.smtpserver.CommandHandler#onCommand(SMTPSession)
    **/
    public void onCommand(SMTPSession session) {
        doHELO(session, session.getCommandArgument());
    }

    /**
     * Handler method called upon receipt of a HELO command.
     * Responds with a greeting and informs the client whether
     * client authentication is required.
     *
     * @param session SMTP session object
     * @param argument the argument passed in with the command by the SMTP client
     */
    private void doHELO(SMTPSession session, String argument) {
        String responseString = null;
        boolean badHelo = false;
                
        /**
         * don't check if the ip address is allowed to relay. Only check if it is set in the config. ed.
         */
        if (!session.isRelayingAllowed() || checkAuthNetworks) {

            // check for resolvable HELO if its set in config
            if (checkResolvableHelo) {
            

                // try to resolv the provided helo. If it can not resolved do not accept it.
                try {
                    dnsServer.getByName(argument);
                } catch (UnknownHostException e) {
                    badHelo = true;
                    responseString = "501 Provided HELO " + argument + " can not resolved";
                    session.writeResponse(responseString);
                    getLogger().info(responseString);
                } 

            } else if (checkReverseEqualsHelo) {
                try {
                    // get reverse entry
                    String reverse = dnsServer.getByName(
                            session.getRemoteIPAddress()).getHostName();

                    if (!argument.equals(reverse)) {
                        badHelo = true;
                        responseString = "501 "
                                + DSNStatus.getStatus(DSNStatus.PERMANENT,
                                        DSNStatus.DELIVERY_INVALID_ARG)
                                + " Provided HELO " + argument
                                + " not equal reverse of "
                                + session.getRemoteIPAddress();

                        session.writeResponse(responseString);
                        getLogger().info(responseString);
                    }
                } catch (UnknownHostException e) {
                    badHelo = true;
                    responseString = "501 "
                            + DSNStatus.getStatus(DSNStatus.PERMANENT,
                                    DSNStatus.DELIVERY_INVALID_ARG)
                            + " Ipaddress " + session.getRemoteIPAddress()
                            + " can not resolved";

                    session.writeResponse(responseString);
                    getLogger().info(responseString);
                }
            }
        }
        
        if (argument == null) {
            responseString = "501 Domain address required: " + COMMAND_NAME;
            session.writeResponse(responseString);
            getLogger().info(responseString);
        } else if (!badHelo) {
            session.resetState();
            session.getState().put(SMTPSession.CURRENT_HELO_MODE, COMMAND_NAME);
            session.getResponseBuffer().append("250 ")
                          .append(session.getConfigurationData().getHelloName())
                          .append(" Hello ")
                          .append(argument)
                          .append(" (")
                          .append(session.getRemoteHost())
                          .append(" [")
                          .append(session.getRemoteIPAddress())
                          .append("])");
            responseString = session.clearResponseBuffer();
            session.writeResponse(responseString);
        }
    }
}
