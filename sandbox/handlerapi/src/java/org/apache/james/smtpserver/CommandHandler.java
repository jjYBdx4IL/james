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

import java.util.List;

/**
 * Custom command handlers must implement this interface
 * The command handlers will be Server wide common to all the SMTPHandlers,
 * therefore the command handlers must store all the state information
 * in the SMTPSession object
 */
 public interface CommandHandler {
     
    /**
     * Handle the command
    **/
    void onCommand(SMTPSession session);

    /**
     * Return a List of implemented commands
     * 
     * @return List which contains implemented commands
     */
    List getImplCommands();
    
}
