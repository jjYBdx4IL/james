/*
 * Copyright (C) The Apache Software Foundation. All rights reserved.
 *
 * This software is published under the terms of the Apache Software License
 * version 1.1, a copy of which has been included with this distribution in
 * the LICENSE file.
 */
package org.apache.james.smtpserver;

import java.io.IOException;

/**
  * This exception is used to indicate when a new MimeMessage has exceeded
  * the maximum message size for the server, as configured in the conf file.
  *
  * @version 0.5.1
  */
public class MessageSizeException extends IOException {

    /**
     * Sole contructor for this class.  This constructor sets
     * the exception message to a fixed error message.
     */
    public MessageSizeException() {
        super("Message size exceeds fixed maximum message size.");
    }
}

