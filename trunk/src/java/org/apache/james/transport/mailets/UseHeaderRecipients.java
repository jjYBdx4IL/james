/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james.transport.mailets;

import org.apache.mailet.GenericMailet;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;

import javax.mail.MessagingException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Collection;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * <p>Mailet designed to process the recipients from the mail headers rather
 * than the recipients specified in the SMTP message header.  This can be
 * useful if your mail is redirected on-route by a mail server that
 * substitutes a fixed recipient address for the original.</p>
 *
 * <p>To use this, match against the redirection address using the
 * <code>RecipientIs</code> matcher and set the mailet 'class' to
 * <code>UseHeaderRecipients</code>.  This will cause the email to be
 * re-injected into the root process with the recipient substituted
 * by all the recipients in the Mail-For, To and Cc headers
 * of the message.</p>
 *
 * <p>e.g.</p>
 * <pre>
 *    <mailet match="RecipientIs=forwarded@myhost"
 *            class="UseHeaderRecipients">
 *    </mailet>
 * </pre>
 *
 * @version 1.0.0, 24/11/2000
 * @author Stuart Roebuck <stuart.roebuck@adolos.com>
 */
public class UseHeaderRecipients extends GenericMailet {

    /**
     * Process an incoming email, removing the currently identified
     * recipients and replacing them with the recipients indicated in
     * the Mail-For, To and Cc headers of the actual email.
     *
     * @param mail incoming email
     */
    public void service(Mail mail) throws MessagingException {
        MimeMessage message = mail.getMessage();

        // Utilise features of Set Collections such that they automatically
        // ensure that no two entries are equal using the equality method
        // of the element objects.  MailAddress objects test equality based
        // on equivalent but not necessarily visually identical addresses.
        Collection recipients = mail.getRecipients();
        // Wipe all the exist recipients
        recipients.clear();
        recipients.addAll(getHeaderMailAddresses(message, "Mail-For"));
        if (recipients.isEmpty()) {
            recipients.addAll(getHeaderMailAddresses(message, "To"));
            recipients.addAll(getHeaderMailAddresses(message, "Cc"));
        }
        log("All recipients = " + recipients.toString());

        log("Reprocessing mail using recipients in message headers");
        // Return email to the "root" process.
        getMailetContext().sendMail(mail.getSender(), mail.getRecipients(), mail.getMessage());
        mail.setState(Mail.GHOST);
    }


    public String getMailetInfo() {
        return "UseHeaderRecipients Mailet";
    }

    /**
     * Work through all the headers of the email with a matching name and
     * extract all the mail addresses as a collection of addresses.
     *
     * @param mail the mail message to read
     * @param name the header name as a String
     * @return the collection of MailAddress objects.
     */
    private Collection getHeaderMailAddresses(MimeMessage message, String name) throws MessagingException {
        StringBuffer logBuffer =
            new StringBuffer(64)
                    .append("Checking ")
                    .append(name)
                    .append(" headers");
        log(logBuffer.toString());
        Collection addresses = new Vector();
        String[] headers = message.getHeader(name);
        String addressString;
        InternetAddress iAddress;
        if (headers != null) {
            for(int i = 0; i < headers.length; i++) {
                StringTokenizer st = new StringTokenizer(headers[i], ",", false);
                while (st.hasMoreTokens()) {
                    addressString = st.nextToken();
                    iAddress = new InternetAddress(addressString);
                    log("Address = " + iAddress.toString());
                    addresses.add(new MailAddress(iAddress));
                }
            }
        }
        return addresses;
    }

}
