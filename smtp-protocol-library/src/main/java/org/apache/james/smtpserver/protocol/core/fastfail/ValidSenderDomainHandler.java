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
package org.apache.james.smtpserver.protocol.core.fastfail;

import java.util.Collection;

import javax.annotation.Resource;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.james.api.protocol.Configurable;
import org.apache.james.dsn.DSNStatus;
import org.apache.james.smtpserver.protocol.DNSService;
import org.apache.james.smtpserver.protocol.SMTPRetCode;
import org.apache.james.smtpserver.protocol.SMTPSession;
import org.apache.james.smtpserver.protocol.TemporaryResolutionException;
import org.apache.james.smtpserver.protocol.hook.HookResult;
import org.apache.james.smtpserver.protocol.hook.HookReturnCode;
import org.apache.james.smtpserver.protocol.hook.MailHook;
import org.apache.mailet.MailAddress;

/**
 * Add MFDNSCheck feature to SMTPServer. This handler reject mail from domains which have not an an valid MX record.  
 * 
 */
public class ValidSenderDomainHandler implements MailHook, Configurable {
    
    private boolean checkAuthNetworks = false;
    private DNSService dnsService = null;

    /**
     * Gets the DNS service.
     * @return the dnsService
     */
    public final DNSService getDNSService() {
        return dnsService;
    }

    /**
     * Sets the DNS service.
     * @param dnsService the dnsService to set
     */
    @Resource(name="org.apache.james.smtpserver.protocol.DNSService")
    public final void setDNSService(DNSService dnsService) {
        this.dnsService = dnsService;
    }
    
    
    /**
     * @see org.apache.james.api.protocol.Configurable#configure(org.apache.commons.configuration.Configuration)
     */
    public void configure(Configuration handlerConfiguration) throws ConfigurationException {
    	setCheckAuthNetworks(handlerConfiguration.getBoolean("checkAuthNetworks",false));
    }
        
    /**
     * Enable checking of authorized networks
     * 
     * @param checkAuthNetworks Set to true to enable
     */
    public void setCheckAuthNetworks(boolean checkAuthNetworks) {
        this.checkAuthNetworks = checkAuthNetworks;
    }

    
    protected boolean check(SMTPSession session, MailAddress senderAddress) {
        // null sender so return
        if (senderAddress == null) return false;

        // Not scan the message if relaying allowed
        if (session.isRelayingAllowed() && !checkAuthNetworks) {
            session.getLogger().info("YES");

        	return false;
        }

        Collection<String> records = null;
            
        // try to resolv the provided domain in the senderaddress. If it can not resolved do not accept it.
        try {
            records = dnsService.findMXRecords(senderAddress.getDomain());
        } catch (TemporaryResolutionException e) {
            // TODO: Should we reject temporary ?
        }
    
        if (records == null || records.size() == 0) {
            return true;
        }

        return false;
    }
    
    /**
     * @see org.apache.james.smtpserver.protocol.hook.MailHook#doMail(org.apache.james.smtpserver.protocol.SMTPSession, org.apache.mailet.MailAddress)
     */
    public HookResult doMail(SMTPSession session, MailAddress sender) {
        if (check(session,sender)) {
            return new HookResult(HookReturnCode.DENY,SMTPRetCode.SYNTAX_ERROR_ARGUMENTS,DSNStatus.getStatus(DSNStatus.PERMANENT,DSNStatus.ADDRESS_SYNTAX_SENDER)+ " sender " + sender + " contains a domain with no valid MX records");
        } else {
            return new HookResult(HookReturnCode.DECLINED);
        }
    }
}