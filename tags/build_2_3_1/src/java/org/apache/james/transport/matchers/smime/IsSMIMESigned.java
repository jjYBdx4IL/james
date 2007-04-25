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
package org.apache.james.transport.matchers.smime;

import java.util.Collection;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.mailet.GenericMatcher;
import org.apache.mailet.Mail;

/**
 * checks if a mail is smime signed. 

 */
public class IsSMIMESigned extends GenericMatcher {

    /**
     * @see org.apache.mailet.Matcher#match(org.apache.mailet.Mail)
     */
    public Collection match(Mail mail) throws MessagingException {
        if (mail == null) return null;
        
        MimeMessage message = mail.getMessage();
        if (message == null) return null;
        
        
        if (message.isMimeType("multipart/signed") 
                || message.isMimeType("application/pkcs7-signature")
                || message.isMimeType("application/x-pkcs7-signature")
                || ((message.isMimeType("application/pkcs7-mime") || message.isMimeType("application/x-pkcs7-mime")) 
                        && message.getContentType().indexOf("signed-data") != -1)) {
            return mail.getRecipients();
        } else return null;
    }
}
