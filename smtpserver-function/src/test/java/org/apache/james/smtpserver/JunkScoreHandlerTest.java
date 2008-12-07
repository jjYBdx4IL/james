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


package org.apache.james.smtpserver;

import java.util.HashMap;
import java.util.Map;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;

import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.container.ContainerUtil;
import org.apache.james.smtpserver.core.filter.fastfail.JunkScoreHandler;
import org.apache.james.smtpserver.junkscore.JunkScore;
import org.apache.james.test.mock.avalon.MockLogger;
import org.apache.mailet.base.test.MockMimeMessage;
import org.apache.mailet.base.test.MockMail;
import org.apache.mailet.Mail;

import junit.framework.TestCase;

public class JunkScoreHandlerTest extends TestCase {
    private String response = null;
    private boolean stopped = false;
    private boolean messageAborted = false;
    private final static String KEY1 = "KEY1";
    private final static String KEY2 = "KEY2";
    private final static double SCORE1 = 10.0;
    private final static double SCORE2 = 7.1;

    public void setUp() {
        response = null;
        stopped = false;
        messageAborted = false;
    }
    
    private SMTPSession setupMockedSMTPSession() {
        SMTPSession session = new AbstractSMTPSession() {
            HashMap state = new HashMap();
            HashMap cState = new HashMap();
            Mail m = null;
        
            public Map getState() {
            state.put(SMTPSession.SENDER, "sender@localhost");
                return state;
            }

            public Map getConnectionState() {
                return cState;
            }

            public void writeResponse(String resp) {
                response = resp;
            }

            public void setStopHandlerProcessing(boolean b) {
                stopped = b;
            }

            public void abortMessage() {
                messageAborted = true;
            }
            
            public Mail getMail(){
                if (m == null) m = getMockMail();
                return m;
            }
            
            public String getRemoteHost() {
                return "anyHost";
            }
            
            public String getRemoteIPAddress() {
                return "000.000.000.001";
            }
        };
        return session;
    }
    
    private Mail getMockMail(){
        Mail m = new MockMail();
        try {
            m.setMessage(new MockMimeMessage());
        } catch (MessagingException e) {
            e.printStackTrace();
        }
        return m;
    }
    
    public void testIllegalActionThrowException() {
        boolean exception = false;
        JunkScoreHandler handler = new JunkScoreHandler();
        ContainerUtil.enableLogging(handler,new MockLogger());
    
        try {
            handler.setAction("invalid");
        } catch (ConfigurationException e) {
            exception = true;
        }
    
        assertTrue("Exception thrown",exception);
    }
    
    public void testRejectAction() throws ConfigurationException {

        SMTPSession session = setupMockedSMTPSession();
        JunkScoreHandler handler = new JunkScoreHandler();
        ContainerUtil.enableLogging(handler,new MockLogger());
    
        handler.setAction("reject");
        handler.setMaxScore(15.0);
        handler.onConnect(session);
        handler.onMessage(session);

        assertNull("Not rejected",response);
        ((JunkScore) session.getState().get(JunkScore.JUNK_SCORE)).setStoredScore(KEY1, SCORE1);
        ((JunkScore) session.getConnectionState().get(JunkScore.JUNK_SCORE_SESSION)).setStoredScore(KEY2, SCORE2);
        handler.onMessage(session);
    
        assertNotNull("Rejected",response);
        assertTrue("Rejected",stopped);
        assertTrue("Rejected",messageAborted);
    }
    
    public void testHeaderAction() throws ConfigurationException, MessagingException {
        SMTPSession session = setupMockedSMTPSession();
        JunkScoreHandler handler = new JunkScoreHandler();
        ContainerUtil.enableLogging(handler,new MockLogger());
    
        handler.setAction("header");

        handler.onConnect(session);
        ((JunkScore) session.getState().get(JunkScore.JUNK_SCORE)).setStoredScore(KEY1, SCORE1);
        ((JunkScore) session.getConnectionState().get(JunkScore.JUNK_SCORE_SESSION)).setStoredScore(KEY2, SCORE2);
        handler.onMessage(session);
    
        MimeMessage message = session.getMail().getMessage();
        assertNotNull("Header added",message.getHeader("X-JUNKSCORE")[0]);
        assertNotNull("Header added",message.getHeader("X-JUNKSCORE-COMPOSED")[0]);
    }
}