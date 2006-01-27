/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://jwsdp.dev.java.net/CDDLv1.0.html
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * https://jwsdp.dev.java.net/CDDLv1.0.html  If applicable,
 * add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your
 * own identifying information: Portions Copyright [yyyy]
 * [name of copyright owner]
 */
/*
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

import com.sun.xml.soap.*;

import javax.xml.soap.SOAPHeaderElement;
import javax.xml.soap.SOAPException;

public class SampleRecipient extends SOAPRecipient {

    public void acceptHeaderElement(
        SOAPHeaderElement elem,
        ProcessingContext context)
        throws SOAPException {

        System.out.println("Processed header block : " +
                            elem.getTagName());
    }

    public void handleIncomingFault(ProcessingContext context) {
    }

    public void handleOutgoingFault(ProcessingContext context) {
    }

}
