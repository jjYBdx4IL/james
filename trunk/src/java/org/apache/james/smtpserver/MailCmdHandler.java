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

import org.apache.avalon.framework.logger.AbstractLogEnabled;
import org.apache.james.util.mail.dsn.DSNStatus;
import org.apache.mailet.MailAddress;
import java.util.Locale;
import java.util.StringTokenizer;

/**
  * Handles MAIL command
  */
public class MailCmdHandler
    extends AbstractLogEnabled
    implements CommandHandler {

    private final static String MAIL_OPTION_SIZE = "SIZE";

    private final static String MESG_SIZE = "MESG_SIZE"; // The size of the message

    private final static String SENDER = "SENDER_ADDRESS";     // Sender's email address

    /*
     * handles MAIL command
     *
     * @see org.apache.james.smtpserver.CommandHandler#onCommand(SMTPSession)
    **/
    public void onCommand(SMTPSession session) {
        doMAIL(session, session.getCommandArgument());
    }


    /**
     * Handler method called upon receipt of a MAIL command.
     * Sets up handler to deliver mail as the stated sender.
     *
     * @param session SMTP session object
     * @param argument the argument passed in with the command by the SMTP client
     */
    private void doMAIL(SMTPSession session, String argument) {
        String responseString = null;
        StringBuffer responseBuffer = session.getResponseBuffer();

        String sender = null;
        if ((argument != null) && (argument.indexOf(":") > 0)) {
            int colonIndex = argument.indexOf(":");
            sender = argument.substring(colonIndex + 1);
            argument = argument.substring(0, colonIndex);
        }
        if (session.getState().containsKey(SENDER)) {
            responseString = "503 "+DSNStatus.getStatus(DSNStatus.PERMANENT,DSNStatus.DELIVERY_OTHER)+" Sender already specified";
            session.writeResponse(responseString);
        } else if (argument == null || !argument.toUpperCase(Locale.US).equals("FROM")
                   || sender == null) {
            responseString = "501 "+DSNStatus.getStatus(DSNStatus.PERMANENT,DSNStatus.DELIVERY_INVALID_ARG)+" Usage: MAIL FROM:<sender>";
            session.writeResponse(responseString);
        } else {
            sender = sender.trim();
            // the next gt after the first lt ... AUTH may add more <>
            int lastChar = sender.indexOf('>', sender.indexOf('<'));
            // Check to see if any options are present and, if so, whether they are correctly formatted
            // (separated from the closing angle bracket by a ' ').
            if ((lastChar > 0) && (sender.length() > lastChar + 2) && (sender.charAt(lastChar + 1) == ' ')) {
                String mailOptionString = sender.substring(lastChar + 2);

                // Remove the options from the sender
                sender = sender.substring(0, lastChar + 1);

                StringTokenizer optionTokenizer = new StringTokenizer(mailOptionString, " ");
                while (optionTokenizer.hasMoreElements()) {
                    String mailOption = optionTokenizer.nextToken();
                    int equalIndex = mailOption.indexOf('=');
                    String mailOptionName = mailOption;
                    String mailOptionValue = "";
                    if (equalIndex > 0) {
                        mailOptionName = mailOption.substring(0, equalIndex).toUpperCase(Locale.US);
                        mailOptionValue = mailOption.substring(equalIndex + 1);
                    }

                    // Handle the SIZE extension keyword

                    if (mailOptionName.startsWith(MAIL_OPTION_SIZE)) {
                        if (!(doMailSize(session, mailOptionValue))) {
                            return;
                        }
                    } else {
                        // Unexpected option attached to the Mail command
                        if (getLogger().isDebugEnabled()) {
                            StringBuffer debugBuffer =
                                new StringBuffer(128)
                                    .append("MAIL command had unrecognized/unexpected option ")
                                    .append(mailOptionName)
                                    .append(" with value ")
                                    .append(mailOptionValue);
                            getLogger().debug(debugBuffer.toString());
                        }
                    }
                }
            }
            if (!sender.startsWith("<") || !sender.endsWith(">")) {
                responseString = "501 "+DSNStatus.getStatus(DSNStatus.PERMANENT,DSNStatus.ADDRESS_SYNTAX_SENDER)+" Syntax error in MAIL command";
                session.writeResponse(responseString);
                if (getLogger().isErrorEnabled()) {
                    StringBuffer errorBuffer =
                        new StringBuffer(128)
                            .append("Error parsing sender address: ")
                            .append(sender)
                            .append(": did not start and end with < >");
                    getLogger().error(errorBuffer.toString());
                }
                return;
            }
            MailAddress senderAddress = null;
            //Remove < and >
            sender = sender.substring(1, sender.length() - 1);
            if (sender.length() == 0) {
                //This is the <> case.  Let senderAddress == null
            } else {
                if (sender.indexOf("@") < 0) {
                    sender = sender + "@localhost";
                }
                try {
                    senderAddress = new MailAddress(sender);
                } catch (Exception pe) {
                    responseString = "501 "+DSNStatus.getStatus(DSNStatus.PERMANENT,DSNStatus.ADDRESS_SYNTAX_SENDER)+" Syntax error in sender address";
                    session.writeResponse(responseString);
                    if (getLogger().isErrorEnabled()) {
                        StringBuffer errorBuffer =
                            new StringBuffer(256)
                                    .append("Error parsing sender address: ")
                                    .append(sender)
                                    .append(": ")
                                    .append(pe.getMessage());
                        getLogger().error(errorBuffer.toString());
                    }
                    return;
                }
            }
            session.getState().put(SENDER, senderAddress);
            responseBuffer.append("250 "+DSNStatus.getStatus(DSNStatus.SUCCESS,DSNStatus.ADDRESS_OTHER)+" Sender <")
                          .append(sender)
                          .append("> OK");
            responseString = session.clearResponseBuffer();
            session.writeResponse(responseString);
        }
    }

    /**
     * Handles the SIZE MAIL option.
     *
     * @param session SMTP session object
     * @param mailOptionValue the option string passed in with the SIZE option
     * @return true if further options should be processed, false otherwise
     */
    private boolean doMailSize(SMTPSession session, String mailOptionValue) {
        int size = 0;
        try {
            size = Integer.parseInt(mailOptionValue);
        } catch (NumberFormatException pe) {
            // This is a malformed option value.  We return an error
            String responseString = "501 "+DSNStatus.getStatus(DSNStatus.PERMANENT,DSNStatus.DELIVERY_INVALID_ARG)+" Syntactically incorrect value for SIZE parameter";
            session.writeResponse(responseString);
            getLogger().error("Rejected syntactically incorrect value for SIZE parameter.");
            return false;
        }
        if (getLogger().isDebugEnabled()) {
            StringBuffer debugBuffer =
                new StringBuffer(128)
                    .append("MAIL command option SIZE received with value ")
                    .append(size)
                    .append(".");
                    getLogger().debug(debugBuffer.toString());
        }
        long maxMessageSize = session.getConfigurationData().getMaxMessageSize();
        if ((maxMessageSize > 0) && (size > maxMessageSize)) {
            // Let the client know that the size limit has been hit.
            String responseString = "552 "+DSNStatus.getStatus(DSNStatus.PERMANENT,DSNStatus.SYSTEM_MSG_TOO_BIG)+" Message size exceeds fixed maximum message size";
            session.writeResponse(responseString);
            StringBuffer errorBuffer =
                new StringBuffer(256)
                    .append("Rejected message from ")
                    .append(session.getState().get(SENDER).toString())
                    .append(" from host ")
                    .append(session.getRemoteHost())
                    .append(" (")
                    .append(session.getRemoteIPAddress())
                    .append(") of size ")
                    .append(size)
                    .append(" exceeding system maximum message size of ")
                    .append(maxMessageSize)
                    .append("based on SIZE option.");
            getLogger().error(errorBuffer.toString());
            return false;
        } else {
            // put the message size in the message state so it can be used
            // later to restrict messages for user quotas, etc.
            session.getState().put(MESG_SIZE, new Integer(size));
        }
        return true;
    }

}
