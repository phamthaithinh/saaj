/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2009 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */



package bugfixes;

import com.sun.xml.messaging.saaj.soap.MessageImpl;
import java.io.*;
import java.net.URL;
import java.net.URLStreamHandler;
import java.util.Iterator;
import java.util.Properties;
import java.util.Locale;

import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.soap.*;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMResult;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import javax.xml.transform.stream.StreamSource;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import util.TestHelper;

import com.sun.xml.messaging.saaj.soap.SOAPVersionMismatchException;
import com.sun.xml.messaging.saaj.util.ByteInputStream;
import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.xml.transform.dom.DOMSource;

/*
 * A class that contains test cases that verify some of the bug fixes made.
 * This is just a convinience class that makes sure the fix is in place,
 * and is intended to be a part of our local test package and is not meant
 * to be shipped ofcourse.
 *
 * @author Manveen Kaur (manveen.kaur@sun.com)
 */
public class BugfixesTest extends TestCase {
    private static TestHelper th = TestHelper.getInstance();

    public BugfixesTest(String name) {
        super(name);
    }

    private SOAPMessage createMessageOne() throws SOAPException {
        MessageFactory msgFactory = MessageFactory.newInstance();

        SOAPMessage msg = msgFactory.createMessage();

        SOAPEnvelope envelope = msg.getSOAPPart().getEnvelope();

        SOAPHeader hdr = envelope.getHeader();
        SOAPBody bdy = envelope.getBody();

        // create a fault element with a prefix other than soap-env
        SOAPBodyElement ltp =
            bdy.addBodyElement(
                envelope.createName(
                    "Fault",
                    "soap",
                    "http://schemas.xmlsoap.org/soap/envelope/"));

        ltp.addChildElement(envelope.createName("faultcode")).addTextNode(
            "100");

        return msg;
    }

    private SOAPMessage createMessageTwo() throws SOAPException {
        MessageFactory msgFactory = MessageFactory.newInstance();

        SOAPMessage msg = msgFactory.createMessage();

        SOAPEnvelope envelope = msg.getSOAPPart().getEnvelope();

        SOAPHeader hdr = envelope.getHeader();
        SOAPBody bdy = envelope.getBody();

        SOAPFault fault = bdy.addFault();
        String prefix = envelope.getElementName().getPrefix();
        fault.setFaultCode(prefix + ":100");
        fault.setFaultString("some reason for fault");
        Detail detail = fault.addDetail();
        detail.addTextNode("this is what she was talking abt?");
        DetailEntry de =
            detail.addDetailEntry(
                envelope.createName("DetailEntry", "e", "some-otherw-uri"));
        de.addTextNode("somedetailheretext");

        return msg;
    }

    /*
     * Test to verify that ClassCastException is not thrown on
     * SOAPBody.getFault() when the prefix is different from soap-env.
     */
    public void testFault() throws SOAPException {

        String exception = null;

        //        try {

        SOAPMessage msgOne = createMessageOne();

        SOAPEnvelope envelope = msgOne.getSOAPPart().getEnvelope();
        SOAPBody bdy = envelope.getBody();

        SOAPFault fault = bdy.getFault();

        //        } catch (Exception e) {
        //            exception = e.getMessage();
        //        }

        // no exception should be thrown
        assertTrue(
            "Exception should not have been thrown: " + exception,
            (exception == null));
    }

    /**
     * Test to verify XML files can be attached to a SOAP Message.
     * There is a testcase in JAXM SQE to reproduce the test case. 
     * It is under saaj13/soap/attachments.
     **/
    public void testAddAnXmlAttachment() throws Exception {

        SOAPMessage msg = createMessageOne();
        
        // These are failing in SQE tests (Bug ID- 6287927) -
        // (1) ap = msg.createAttachmentPart((Object)new URLDataSource(new URL
        // (hostname+data2)),"text/xml");
        
        // (2) ap = msg.createAttachmentPart((Object)new StreamSource(new File
        //(req.getParameter("basedir")+data2)),"text/xml");
        
        // (1)         

/*        URLDataSource urlDataS = new URLDataSource(
                new java.net.URL("file:/c:/ws/saaj-ri/build.xml")); 
        
        AttachmentPart ap1 = msg.createAttachmentPart(urlDataS ,"text/xml");
        msg.addAttachmentPart(ap1);
*/      
        java.io.File file = new File("src/test/bugfixes/data/setContent.xml");
        javax.activation.FileDataSource fd = new javax.activation.FileDataSource(file);
 //     StreamSource stream = new StreamSource(fd.getInputStream());
        AttachmentPart ap2 = msg.createAttachmentPart(fd,"text/xml");
        msg.addAttachmentPart(ap2);
        
        AttachmentPart ap3 = msg.createAttachmentPart(new StreamSource(file),"text/xml");        
        msg.addAttachmentPart(ap3);
        
        msg.writeTo(System.out);
    }
    
    /*
     * Detail.getDetailEntries() iterator returned should only contain
     * DetailEntry objects (and not Text objects).
     */
    public void testDetailEntry() throws Exception {
        SOAPMessage msg = createMessageTwo();

        SOAPEnvelope envelope = msg.getSOAPPart().getEnvelope();
        SOAPBody bdy = envelope.getBody();

        SOAPFault fault = bdy.getFault();
        Detail detail = fault.getDetail();

        Iterator iter = detail.getDetailEntries();
        while (iter.hasNext()) {
            Object object = iter.next();
            assertTrue(
                "WRONG TYPE:" + object.getClass().toString(),
                object instanceof DetailEntry);
        }
    }

    /*
     * Test to verify that the setContent bug has been fixed.
     * Input is from a StreamSource.
     */
    public void testSetContentStrSrc() throws Exception {
        MessageFactory mfactory = MessageFactory.newInstance();
        SOAPMessage msg = mfactory.createMessage();

        SOAPPart part = msg.getSOAPPart();
        StreamSource streamSource =
            new StreamSource(
                new FileInputStream("src/test/bugfixes/data/setContent.xml"));

        // white spaces should be retained
        part.setContent(streamSource);
        SOAPEnvelope envelope = part.getEnvelope();
        SOAPHeader header = envelope.getHeader();
        Iterator headerChildren = header.getChildElements();
        assertTrue("Header has first child", headerChildren.hasNext());
        Node firstChild = (Node) headerChildren.next();
        assertTrue("First text node contains newLine char",
                   firstChild.getNodeValue().equals("\n"));
        assertTrue("Header has second child", headerChildren.hasNext());
        Node secondChild = (Node) headerChildren.next();
        assertEquals("Second child has only one child",
                     secondChild.getFirstChild(), secondChild.getLastChild());
        assertTrue("Second child has a text node as a child "
                                   + "with a particular value",
                    secondChild.getFirstChild().getNodeValue()
                                   .equals("line 1\nline 2\nline 3\n"));
    }

    /*
     * Test to verify that whitespace between elements should be ignored.
     * Input is from a StreamSource.
     */
    // disabling this for now. This test hangs??
    public void xtestIgnoreInterElementWhiteSpace() throws Exception {
        MessageFactory mfactory = MessageFactory.newInstance();
        SOAPMessage msg = mfactory.createMessage();

        SOAPPart part = msg.getSOAPPart();
        StreamSource streamSource =
            new StreamSource(
                new FileInputStream("src/test/bugfixes/data/setContent.xml"));

        part.setContent(streamSource);
        
        // need to set property to ignore inter element whitespace here.
        
        int count = 0;
        
        SOAPEnvelope envelope = part.getEnvelope();
        Iterator i = envelope.getChildElements();
        while(i.hasNext()) {
            // System.out.println("######Iterator i="+i.next());
            count ++;
        } 
        
        // TODO: Uncomment this when the property is set, otherwise this 
        // test will fail
        /*
         if (count > 2)
            fail("Inter-element whitespace should have been ignored");
         */
    }

    public void testReadMultipleLines() throws Exception {
        MessageFactory mfactory = MessageFactory.newInstance();
        SOAPMessage msg = mfactory.createMessage();

        SOAPPart part = msg.getSOAPPart();
        StreamSource streamSource =
            new StreamSource(
                new FileInputStream("src/test/bugfixes/data/certificate.xml"));

        // part.setContent(streamSource);

        TransformerFactory transformerFactory =
            new com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl();
        Transformer transformer = transformerFactory.newTransformer();
        DOMResult result = new DOMResult(part);
        transformer.transform(streamSource, result);

        SOAPBody body = msg.getSOAPBody();
        Iterator eachChild = body.getChildElements();
        eachChild.next();
        SOAPElement element = (SOAPElement) eachChild.next();
        assertEquals("ds:X509Certificate", element.getTagName());
        //element.normalize();
        // System.out.println(element.getValue());
    }

//    public void testReadMultipleLinesControlCase() throws Exception {
//        MessageFactory mfactory = MessageFactory.newInstance();
//        SOAPMessage msg = mfactory.createMessage();
//
//        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
//        factory.setAttribute("http://apache.org/xml/features/dom/defer-node-expansion", Boolean.FALSE);
//        DocumentBuilder builder = factory.newDocumentBuilder();
//        Document content = builder.parse(new FileInputStream("src/test/bugfixes/data/certificate.xml"));
//        SOAPPart part = msg.getSOAPPart();
//        DOMSource source = new DOMSource(content.getDocumentElement());
//        part.setContent(source);
//
//
//        SOAPBody body = msg.getSOAPBody();
//        Iterator eachChild = body.getChildElements();
//        eachChild.next();
//        SOAPElement element = (SOAPElement) eachChild.next();
//        assertEquals("ds:X509Certificate", element.getTagName());
//        //element.normalize();
//        System.out.println(element.getValue());
//    }

    /*
     * Test to verify that the setContent bug has been fixed.
     * Input is from a DOMSource.
     */
    public void testSetContentDOMSrc() throws Exception {

        DocumentBuilderFactory factory =
            new com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();

        Document domDoc =
            builder.parse(new File("src/test/bugfixes/data/setContent.xml"));
        Source domSource = new javax.xml.transform.dom.DOMSource(domDoc);

        MessageFactory mfactory = MessageFactory.newInstance();
        SOAPMessage msg = mfactory.createMessage();

        SOAPPart part = msg.getSOAPPart();
        part.setContent(domSource);

        try {
            msg.writeTo(new FileOutputStream("src/test/bugfixes/data/tempFile"));
        } catch(Exception e) {
            e.printStackTrace();
            fail("Exception should not be thrown.");
        }

        // white spaces should be retained
        SOAPEnvelope envelope = part.getEnvelope();
        SOAPHeader header = envelope.getHeader();
        Iterator headerChildren = header.getChildElements();
        assertTrue("Header has first child", headerChildren.hasNext());
        Node firstChild = (Node) headerChildren.next();
        assertTrue("First text node contains newLine char",
                   firstChild.getNodeValue().equals("\n"));
        assertTrue("Header has second child", headerChildren.hasNext());
        Node secondChild = (Node) headerChildren.next();
        assertEquals("Second child has only one child",
                     secondChild.getFirstChild(), secondChild.getLastChild());
        assertTrue("Second child has a text node as a child "
                                   + "with a particular value",
                    secondChild.getFirstChild().getNodeValue()
                                   .equals("line 1\nline 2\nline 3\n"));
    }

    /*
     * Test to reproduce the 'inputStream closed' bug. 4642290.
     * Still can't seem to reproduce it?
     */

    // This test assumes that getContent returns a StreamSource which is not always true
    // so don't run it.
    public void xtestWriteTo() {

        boolean error = false;

        try {

            MessageFactory mfactory = MessageFactory.newInstance();
            SOAPMessage msg = mfactory.createMessage();

            SOAPPart part = msg.getSOAPPart();
            StreamSource streamSource =
                new StreamSource(
                    new FileInputStream("src/test/bugfixes/data/setContent.xml"));

            part.setContent(streamSource);

            // create and add an attachment
            AttachmentPart attachment = msg.createAttachmentPart();
            String stringContent = "blah";

            attachment.setContent(stringContent, "text/plain");
            msg.addAttachmentPart(attachment);

            // adding another attachment
            attachment.setContent(streamSource, "text/xml");
            msg.addAttachmentPart(attachment);

            System.out.println("First write To.... ");
            // msg.writeTo(System.out);

            // do something to the tree
            SOAPEnvelope envelope = msg.getSOAPPart().getEnvelope();
            SOAPHeader hdr = envelope.getHeader();
            SOAPBody bdy = envelope.getBody();

            // try to get stream source now
            StreamSource src = (StreamSource) msg.getSOAPPart().getContent();
            InputStream inStream = src.getInputStream();

            System.out.println("Trying to read input stream here");
            // trying to read this input stream
            inStream.read();

            // stream should not be closed on second write to...
            System.out.println("Second write To.... ");
            // msg.writeTo(System.out);

        } catch (Exception e) {
            e.printStackTrace();
            error = true;
        }

        // check that no exception should be thrown
        assertTrue(
            "Stream should not be closed;no exception should be thrown",
            (!error));

    }

    /*
     * Test to reproduce and verify that the URLStreamHandler bug has been fixed.
     * Bug id 4747050.
     * The value of data Handler should be set to the value set in the
     * test. (perhaps use a debugger to verify this?)
     */
    public void xtestURLStreamHandler() {

        boolean error = false;

        try {
            SOAPConnectionFactory factory = SOAPConnectionFactory.newInstance();
            SOAPConnection con = factory.createConnection();

            // create a request message and give it content
            MessageFactory mfactory = MessageFactory.newInstance();
            SOAPMessage msg = mfactory.createMessage();

            // Create an envelope in the message
            SOAPEnvelope envelope = msg.getSOAPPart().getEnvelope();
            SOAPBody body = envelope.getBody();

            body
                .addChildElement(
                    envelope.createName(
                        "find_business",
                        "",
                        "urn:uddi-org:api"))
                .addAttribute(envelope.createName("generic"), "1.0")
                .addAttribute(envelope.createName("maxRows"), "100")
                .addChildElement("name")
                .addTextNode("SUNW");

            //set proxy properties before sending message
            Properties props = System.getProperties();
            // setting these to sun settings for now
            props.put("http.proxyHost", "wcscaa.sfbay.sun.com");
            props.put("http.proxyPort", "8080");

            // URL without StreamHandler
            /*
            URL to_url = new
                URL("http://www-3.ibm.com/services/uddi/testregistry/inquiryapi");
            */

            // create a URL that takes a URLStramHandler in its c'tor
            // this value should be preserved.

            URL to_url =
                new URL(
                    "http",
                    "www-3.ibm.com",
                    -1,
                    "/services/uddi/testregistry/inquiryapi",
                    (URLStreamHandler) Class
                        .forName("sun.net.www.protocol.http.Handler")
                        .newInstance());

            SOAPMessage reply = con.call(msg, to_url);

            System.out.println("Received reply from: " + to_url);
            // reply.writeTo(System.out);
            con.close();
        } catch (Exception e) {
            error = true;
        }

        assertTrue("URLStreamhandler test failed ", (!error));
    }

    /*
     * 4793014 SAAJ needs to reject messages with DTDs
     *
     * Note: this does not actually test of messages with DTDs but messages
     * with <em>entity definitions</em> in the DTD.  Although messages with
     * DTDs should also be rejected, entity definitions can be used in a
     * denial of service attack and is thus important to check for. [eeg
     * 17dec02]
     */
    public void testRejectDtd() throws Exception {
        InputStream is = th.getInputStream("rejectDtd.xml");
        MimeHeaders mimeHeaders = new MimeHeaders();
        mimeHeaders.addHeader("Content-Type", "text/xml");

        MessageFactory msgFactory = MessageFactory.newInstance();
        try {
            SOAPMessage msg = msgFactory.createMessage(mimeHeaders, is);
            th.writeTo(msg);
            SOAPEnvelope envelope = msg.getSOAPPart().getEnvelope();
        } catch (SOAPException se) {
            // This is the expected outcome since SAAJ should reject this
            // document b/c it contains a general entity in its local DTD
            // subset
            return;
        }
        fail("SOAPMessage should have been rejected b/c it contains a DTD");
    }

    /*
     * Parse a simple SOAP message
     */
    public void testSanity() throws Exception {
        InputStream is = th.getInputStream("sanity.xml");
        MimeHeaders mimeHeaders = new MimeHeaders();
        mimeHeaders.addHeader("Content-Type", "text/xml");

        MessageFactory msgFactory = MessageFactory.newInstance();
        try {
            SOAPMessage msg = msgFactory.createMessage(mimeHeaders, is);
            SOAPEnvelope envelope = msg.getSOAPPart().getEnvelope();
        } catch (SOAPException se) {
            fail("SOAPException unexpected" + se);
        }
    }

    /*
     * Test case to reproduce text element getValue bug. This bug is a regression
     * found in the tck test suite.
     */
    public void testTextGetValue() throws Exception {

        MessageFactory msgFactory = MessageFactory.newInstance();
        SOAPMessage msg = msgFactory.createMessage();

        SOAPEnvelope envelope = msg.getSOAPPart().getEnvelope();
        SOAPBody body = envelope.getBody();

        Name name = envelope.createName("localname", "prefix", "uri");
        SOAPElement element = body.addChildElement(name);
        element.addTextNode("abctext");

        String value = element.getValue();

        if (!"abctext".equals(value))
            fail("Value should be abctext; got " + value);

    }

    /*
     * 4800266 Possible WSI releated saaj bug when creating a SOAPFault
     * message.  Closed as not reproducible. [eeg 21jan03]
     */
    public void testFaultUnqualifiedSubElements() throws Exception {
        MessageFactory msgFactory = MessageFactory.newInstance();
        SOAPMessage msg = msgFactory.createMessage();
        SOAPEnvelope envelope = msg.getSOAPPart().getEnvelope();
        SOAPHeader hdr = envelope.getHeader();
        SOAPBody body = envelope.getBody();

        SOAPFault sf = body.addFault();
        String envPrefix = envelope.getElementName().getPrefix();
        sf.setFaultCode(envPrefix + ":Client");
        sf.setFaultString("This is the fault string");
        sf.setFaultActor("http://example.org/faultactor");
        sf.setFaultActor(null);
        Detail d = sf.addDetail();
        d.addTextNode("This should be a valid SOAP Fault Message");
        
        Iterator faultChildren = sf.getChildElements();
        assertTrue("Fault has the first child", faultChildren.hasNext());
        SOAPElement faultCode = (SOAPElement) faultChildren.next();
        assertNull("FaultCode does not have an xmlns attribute",
                    faultCode.getAttributeValue(new QName("xmlns")));
        th.writeTo(msg);
    }

    public void testGetFaultActor() throws Exception {

        MessageFactory msgFactory = MessageFactory.newInstance();
        SOAPMessage msg = msgFactory.createMessage();
        SOAPEnvelope envelope = msg.getSOAPPart().getEnvelope();
        SOAPHeader hdr = envelope.getHeader();
        SOAPBody body = envelope.getBody();

        SOAPFault sf = body.addFault();
        String envPrefix = envelope.getElementName().getPrefix();
        sf.setFaultCode(envPrefix + ":Client");
        sf.setFaultString("This is the fault string");
        sf.setFaultActor("/faultActor");

        sf.setFaultCode(envPrefix + ":Client2");
        assertEquals(sf.getFaultCode(), new String(envPrefix + ":Client2"));

        sf.setFaultActor("/faultActor2");
        assertEquals(sf.getFaultActor(), new String("/faultActor2"));
    }

    public void testGetFaultCodeAsName() throws Exception {

        MessageFactory msgFactory = MessageFactory.newInstance();
        SOAPMessage msg = msgFactory.createMessage();
        SOAPPart soapPart = msg.getSOAPPart();
        SOAPEnvelope envelope = soapPart.getEnvelope();
        SOAPHeader hdr = envelope.getHeader();
        SOAPBody body = envelope.getBody();

        SOAPFault sf = body.addFault();
        String faultCodeLocalName = "Client2";
        String faultCodePrefix = "fcp";
        String faultCodeUri = "http://test/fault/code";
        body.addNamespaceDeclaration(faultCodePrefix, faultCodeUri);
        //Name faultCodeName = envelope.createName(faultCodeLocalName, faultCodePrefix, faultCodeUri);
        //sf.setFaultCode(faultCodeName);
        sf.setFaultCode(faultCodePrefix + ":" + faultCodeLocalName);
        sf.setFaultString("This is the fault string");

        Name faultCode = sf.getFaultCodeAsName();
        assertEquals(faultCodePrefix, faultCode.getPrefix());
        assertEquals(faultCodeUri, faultCode.getURI());
        assertEquals(faultCodeLocalName, faultCode.getLocalName());

    }

    public void testGetFaultCodeAsName2() throws Exception {

        MessageFactory msgFactory = MessageFactory.newInstance();
        SOAPMessage msg = msgFactory.createMessage();
        SOAPPart soapPart = msg.getSOAPPart();
        SOAPEnvelope envelope = soapPart.getEnvelope();
        SOAPHeader hdr = envelope.getHeader();
        SOAPBody body = envelope.getBody();

        SOAPFault sf = body.addFault();
        String faultCodeLocalName = "Client2";
        String faultCodePrefix = "fcp";
        String faultCodeUri = "http://test/fault/code";
        Name faultCodeName =
            envelope.createName(
                faultCodeLocalName,
                faultCodePrefix,
                faultCodeUri);
        sf.setFaultCode(faultCodeName);
        sf.setFaultString("This is the fault string");

        Name faultCode = sf.getFaultCodeAsName();
        assertEquals(faultCodePrefix, faultCode.getPrefix());
        assertEquals(faultCodeUri, faultCode.getURI());
        assertEquals(faultCodeLocalName, faultCode.getLocalName());

    }

    // Bug 4824922
    public void testEvelopeNamespacePropogation() throws Exception {

        DocumentBuilderFactory factory =
            new com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();

        Document domDoc =
            builder.parse(new File("src/test/bugfixes/data/env-prefix.xml"));
        Source domSource = new javax.xml.transform.dom.DOMSource(domDoc);

        MessageFactory mfactory = MessageFactory.newInstance();
        SOAPMessage msg = mfactory.createMessage();

        SOAPPart part = msg.getSOAPPart();

        part.setContent(domSource);

        SOAPEnvelope env = part.getEnvelope();
        SOAPHeader header = env.getHeader();
        if (header == null)
            header = env.addHeader();
        header.addTextNode("This goes inside the SOAP header");

        SOAPBody body = env.getBody();
        body.addTextNode("Some random stuff goes in here");
        assertEquals("env", header.getPrefix());

    }

    public void testVersionMismatch() throws Exception {
        String RPC =
            "<soap:Envelope\n"
                + "    xmlns:soap='http://schemas.xmlsoap.org/soap/envelope/bogus/'>\n"
                + "  <soap:Header/>\n"
                + "  <soap:Body/>\n"
                + "</soap:Envelope>\n";

        final MimeHeaders headers = new MimeHeaders();
        headers.addHeader("Content-Type", "text/xml");

        MessageFactory factory = MessageFactory.newInstance();
        InputStream istream = new StringBufferInputStream(RPC);
        BufferedInputStream bistream = new BufferedInputStream(istream);
        try {
            SOAPMessage m = factory.createMessage(headers, bistream);
            m.getSOAPBody();
        } catch (SOAPVersionMismatchException e) {
            assertTrue(true);
            return;
        }
        fail();
    }

    /**
     * Trying to reproduce bug 4819222.
     */
    public void testCreateSOAPMessage() throws Exception {
        String RPC =
            "<soap:Envelope\n"
                + "    xmlns:soap='http://schemas.xmlsoap.org/soap/envelope/'>"
                + "<soap:Body/>"
                + "</soap:Envelope>";

        final MimeHeaders headers = new MimeHeaders();
        headers.addHeader("Content-Type", "text/xml");

        MessageFactory factory = MessageFactory.newInstance();
        InputStream istream = new StringBufferInputStream(RPC);
        BufferedInputStream bistream = new BufferedInputStream(istream);
        SOAPMessage m = factory.createMessage(headers, bistream);

        SOAPPart part = m.getSOAPPart();
        SOAPEnvelope envelope = part.getEnvelope();
        SOAPHeader header = envelope.addHeader();
        assertTrue("Header has the same prefix as envelope",
                   header.getPrefix().equals(envelope.getPrefix()));
    }

    /*
     * Reproducing GetAllAttributes TCK failure.
     */
    public void testGetAllAttributes() throws Exception {

        MessageFactory msgFactory = MessageFactory.newInstance();
        SOAPMessage msg = msgFactory.createMessage();
        SOAPEnvelope envelope = msg.getSOAPPart().getEnvelope();
        SOAPHeader hdr = envelope.getHeader();
        SOAPBody body = envelope.getBody();

        Name name1 = envelope.createName("MyAttr1");
        String value1 = "MyValue1";
        Name name2 = envelope.createName("MyAttr2");
        String value2 = "MyValue2";
        Name name3 = envelope.createName("MyAttr3");
        String value3 = "MyValue3";

        body.addAttribute(name1, value1);
        body.addAttribute(name2, value2);
        body.addAttribute(name3, value3);

        Iterator i = body.getAllAttributes();
        int count = 0;
        while (i.hasNext()) {
            count++;
            i.next();
        }

        if (count != 3)
            fail("Wrong iterator count returned of " + count + ", expected 3");

        i = body.getAllAttributes();
        while (i.hasNext()) {
            Name name = (Name) i.next();
            assertEquals(
                "Wrong Name returned",
                name.getPrefix(),
                name1.getPrefix());
            // the bug was that the URI's were not matching.
            assertEquals("Wrong Name returned", name.getURI(), name1.getURI());

        }
    }

    /*
     * Add Namespace of the attribute if it is not declared before.
     * Namespace declaration should not be an attribute (complying
     * with saaj 1.1) 
     */
    public void testGetAllAttributesXmlns() throws Exception {

        MessageFactory msgFactory = MessageFactory.newInstance();
        SOAPMessage msg = msgFactory.createMessage();
        SOAPEnvelope envelope = msg.getSOAPPart().getEnvelope();
        SOAPHeader hdr = envelope.getHeader();
        SOAPBody body = envelope.getBody();

        Name name1 = envelope.createName("MyAttr1");
        String value1 = "MyValue1";
        Name name2 = envelope.createName("MyAttr2");
        String value2 = "MyValue2";
        Name name3 = envelope.createName("MyAttr3", "f", "http://www.ee.com");
        String value3 = "MyValue3";

        body.addAttribute(name1, value1);
        body.addAttribute(name2, value2);
        body.addAttribute(name3, value3);

        Iterator i = body.getAllAttributes();
        int count = 0;
        while (i.hasNext()) {
            count++;
            i.next();
        }
        if (count != 3)
            fail("Wrong iterator count returned of " + count + ", expected 3");

    }

    /*
     * Bug Id 4823704
     */
    public void testSOAPBodyAddDocument() throws Exception {

        DocumentBuilderFactory factory =
            new com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();

        InputStream istream =
            new StringBufferInputStream("<foo> bar <w> we </w> </foo>");
        Document doc = builder.parse(istream);

        MessageFactory mfactory = MessageFactory.newInstance();
        SOAPMessage msg = mfactory.createMessage();
        SOAPPart part = msg.getSOAPPart();
        SOAPBody body = part.getEnvelope().getBody();

        SOAPBodyElement e = body.addDocument(doc);
        // System.out.println("e " + e.getNodeName());

        String expected =
            "<SOAP-ENV:Envelope xmlns:SOAP-ENV"
                + "=\"http://schemas.xmlsoap.org/soap/envelope/\"><SOAP-ENV:Header/>"
                + "<SOAP-ENV:Body><foo> bar <w> we </w> </foo></SOAP-ENV:Body>"
                + "</SOAP-ENV:Envelope>";
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        msg.writeTo(output);
        String actual = output.toString();
        assertEquals(expected, actual);
    }

    /*
     * TCK failure for addChildElementTest5.
     */
    public void testAddChildElementTest5() throws Exception {

        MessageFactory mfactory = MessageFactory.newInstance();
        SOAPMessage msg = mfactory.createMessage();
        SOAPPart part = msg.getSOAPPart();
        SOAPEnvelope envelope = part.getEnvelope();
        SOAPBody body = envelope.getBody();

        SOAPElementFactory sfactory = SOAPElementFactory.newInstance();
        Name name = envelope.createName("MyName1", "MyPrefix1", "MyUri1");

        SOAPElement myse = sfactory.create(name);
        SOAPElement se = body.addChildElement(myse);

        if (se == null) {
            fail("addChildElement() did not return SOAPElement");
        } else {
            Iterator i = body.getChildElements(name);
            int count = 0;
            while (i.hasNext()) {
                count++;
                i.next();
            }
            if (count != 1)
                fail("Count should be 1, but got " + count);

            i = body.getChildElements(name);

            SOAPElement se2 = (SOAPElement) i.next();
            if (!se.equals(se2)) {
                fail("addChildElementTest5() test FAILED");
            }
        }

        Name n = se.getElementName();
        if (!n.equals(name)) {
            fail(
                "addChildElement() did not return "
                    + "correct name object expected localname="
                    + name.getLocalName()
                    + ", got localname="
                    + n.getLocalName());
        }
    }

    public void testAddTextNode1() throws Exception {

        MessageFactory mfactory = MessageFactory.newInstance();
        SOAPMessage msg = mfactory.createMessage();
        SOAPPart part = msg.getSOAPPart();
        SOAPEnvelope envelope = part.getEnvelope();
        SOAPBody body = envelope.getBody();

        Iterator iStart = envelope.getChildElements();
        SOAPElement se = envelope.addTextNode("<txt>This is text</txt>");

        if (se == null) {
            fail("addTextNode() did not return SOAPElement");
        } else if (!envelope.getValue().equals("<txt>This is text</txt>")) {
            String s = body.getValue();
            fail("Returned " + s + ", Expected <txt>" + "This is text</txt>");
        }
    }

    public void testAddDocument() throws Exception {

        Document document = null;
        DocumentBuilderFactory factory =
            new com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl();
        factory.setNamespaceAware(true);

        DocumentBuilder builder = factory.newDocumentBuilder();
        // document = builder.parse(new File("src/test/bugfixes/data/slide.xml"));
        document =
            builder.parse(new File("src/test/bugfixes/data/message.xml"));

        // Create message factory and SOAP factory
        MessageFactory messageFactory = MessageFactory.newInstance();
        SOAPFactory soapFactory = SOAPFactory.newInstance();

        // Create a message
        SOAPMessage message = messageFactory.createMessage();

        // Get the SOAP header from the message and remove it
        SOAPHeader header = message.getSOAPHeader();
        header.detachNode();

        // Get the SOAP body from the message
        SOAPBody body = message.getSOAPBody();

        SOAPBodyElement sbe = body.addBodyElement(
            new QName("http://schemas.xmlsoap.org/soap/envelope/",
                      "Envelope",
                      "SOAP-ENV"));

        // Add the DOM to the message body
        SOAPBodyElement docElement = body.addDocument(document);

        assertTrue("Both body elements have the same name.",
                   sbe.getElementQName().equals(docElement.getElementQName()));

        message.saveChanges();

        Iterator iter1 = body.getChildElements();

        SOAPBodyElement firstChild = (SOAPBodyElement) iter1.next();
        assertNull("firstChild (sbe) has no child.",
                   firstChild.getFirstChild());

        SOAPBodyElement secondChild = (SOAPBodyElement) iter1.next();
        assertNotNull("secondChild (docElement) has atleast one child.",
                      secondChild.getFirstChild());
        assertEquals("secondChild (docElement) has exactly one child.",
                     secondChild.getFirstChild(),
                     secondChild.getLastChild());

        // Get contents using SAAJ APIs
        getContents(iter1, "", false);
    }

    /*
     * Add a document which contains an undeclared namespace.
     */
    public void testAddDocWithUndeclaredNS() throws Exception {

        try {
            Document document = null;
            DocumentBuilderFactory factory =
                new com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl();
            factory.setNamespaceAware(true);
  
            DocumentBuilder builder = factory.newDocumentBuilder();
            document =
                builder.parse(new File("src/test/bugfixes/data/undeclNS.xml"));
  
            // Create message factory and SOAP factory
            MessageFactory messageFactory = MessageFactory.newInstance();
            SOAPFactory soapFactory = SOAPFactory.newInstance();
  
            // Create a message
            SOAPMessage message = messageFactory.createMessage();
  
            // Get the SOAP header from the message and remove it
            SOAPHeader header = message.getSOAPHeader();
            header.detachNode();
  
            // Get the SOAP body from the message
            SOAPBody body = message.getSOAPBody();
  
            // Add the DOM to the message body
            SOAPBodyElement docElement = body.addDocument(document);
  
            message.saveChanges();
        } catch (Exception e) {
            return;
        }
        fail("An exception should have been thrown");
 
        // Get contents using SAAJ APIs
        //Iterator iter1 = body.getChildElements();
        //getContents(iter1, "", false);
    }

    /*
     * Recursive method to get and print contents of elements
     */
    private void getContents(
        Iterator iterator,
        String indent,
        boolean display) {
        String displayStr = null;

        while (iterator.hasNext()) {
            Node node = (Node) iterator.next();
            SOAPElement element = null;
            Text text = null;
            if (node instanceof SOAPElement) {
                element = (SOAPElement) node;
                Name name = element.getElementName();
                displayStr = indent + "Name is " + name.getQualifiedName();
                if (display) {
                    System.out.println(displayStr);
                }
                Iterator attrs = element.getAllAttributes();
                while (attrs.hasNext()) {
                    Name attrName = (Name) attrs.next();
                    displayStr =
                        indent
                            + " Attribute name is "
                            + attrName.getQualifiedName();
                    if (display) {
                        System.out.println(displayStr);
                    }
                    displayStr =
                        indent
                            + " Attribute value is "
                            + element.getAttributeValue(attrName);
                    if (display) {
                        System.out.println(displayStr);
                    }
                }
                Iterator iter2 = element.getChildElements();
                getContents(iter2, indent + " ", display);
            } else {
                text = (Text) node;
                displayStr = indent + "Content is: " + text.getValue();
                if (display) {
                    System.out.println(displayStr);
                }
            }
        }
    }
    
    /*
     * Bug : 4863987
     * Creates unnecessary xmlns:SOAP-ENV attributes for header elements
     */
    public void testExamineHeaderElements() throws Exception {
        
        MessageFactory mf = MessageFactory.newInstance();
        SOAPMessage msg = mf.createMessage();        
        SOAPPart sp = msg.getSOAPPart();
        
        SOAPEnvelope envelope = sp.getEnvelope();
        
        SOAPHeader hdr = envelope.getHeader();
        SOAPBody bdy = envelope.getBody();

        // Add to body 
        SOAPBodyElement gltp = bdy.addBodyElement(
            envelope.createName("GetLastTradePrice", "ztrade",
                "http://wombat.ztrade.com"));
        
        gltp.addChildElement(envelope.createName("symbol", "ztrade",
            "http://wombat.ztrade.com")).addTextNode("SUNW");
        
        // Attach header        
        SOAPHeaderElement she = null;
        Name reservation = envelope.createName("reservation", "tr",
            "http://trs.org/reservation");
        SOAPHeaderElement resHeaderElem = hdr.addHeaderElement(reservation);
        resHeaderElem.setActor(SOAPConstants.URI_SOAP_ACTOR_NEXT);
        resHeaderElem.setMustUnderstand(false);

        // Save the soap message to file
        FileOutputStream sentFile = new FileOutputStream(
            "src/test/bugfixes/data/examine.xml");
        msg.writeTo(sentFile);
        sentFile.close();

        // Examine the headers
        FileInputStream fin= new FileInputStream(
            "src/test/bugfixes/data/examine.xml");
        SOAPMessage recvMsg = mf.createMessage(msg.getMimeHeaders(), fin);
        ByteArrayOutputStream correct = new ByteArrayOutputStream();
        recvMsg.writeTo(correct);
        SOAPHeader recvHdr = recvMsg.getSOAPHeader();
        recvHdr.examineHeaderElements(SOAPConstants.URI_SOAP_ACTOR_NEXT);
        ByteArrayOutputStream incorrect = new ByteArrayOutputStream();
        recvMsg.writeTo(incorrect);
        fin.close();
        Iterator it = ((javax.xml.soap.SOAPHeader)recvHdr).getChildElements();
        SOAPHeaderElement n = (SOAPHeaderElement)it.next();
        //making sure there was a Actor.
        assertTrue(n.getActor() != null);
        //making sure MU was "0""
        assertTrue(!n.getMustUnderstand());
        
        // the check cannot be like this because with the latest workspace
        // there is just a re-ordering of the attributes.
        //assertTrue(correct.toString().equals(incorrect.toString()));
    }
    
	/*
	 * Doesn't produce the text node value correctly
	 */
	public void testSplitAttrValue() throws Exception {
		byte[] junk = new byte[10000];
		FileInputStream fin = new FileInputStream(
			"src/test/bugfixes/data/bugAttr.xml");
		int chars = fin.read(junk);
		fin.close();
		MessageFactory mfactory = MessageFactory.newInstance();
		SOAPMessage msg = mfactory.createMessage();

		SOAPPart part = msg.getSOAPPart();
		StreamSource streamSource =
			new StreamSource(new ByteInputStream(junk, chars));

		part.setContent(streamSource);
		SOAPElement root = part.getEnvelope();
		org.w3c.dom.Node node = root.getFirstChild();
		node = node.getFirstChild();
		node = node.getNextSibling();
		node = node.getNextSibling();
		node = node.getNextSibling();
		node = node.getNextSibling();
		node = node.getNextSibling();
		node = node.getNextSibling();
		SOAPElement item = (SOAPElement)node.getFirstChild();
		assertEquals("s12", item.getValue());
	}

    /*
     * Doesn't produce the text node value correctly
     */
    public void testSimpleSplitText() throws Exception {
        String msgText =
            "<SOAP-ENV:Envelope "
                +"xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                +"<SOAP-ENV:Body>"
                    +"<foo>hello9world</foo>"
                +"</SOAP-ENV:Body>"
            +"</SOAP-ENV:Envelope>";

        for(int i=0; i < 1950; i++) {
            msgText = msgText.replaceFirst("9", "99");
        }
//System.out.println("Msg text="+msgText);
//System.out.println("Endorsed="+System.getProperty("java.endorsed.dirs"));
        MessageFactory mfactory = MessageFactory.newInstance();
        SOAPMessage msg = mfactory.createMessage();

        SOAPPart part = msg.getSOAPPart();
        StreamSource streamSource =
            new StreamSource(new ByteArrayInputStream(msgText.getBytes()));

        part.setContent(streamSource);
        SOAPEnvelope envelope = part.getEnvelope();
        SOAPHeader hdr = envelope.getHeader();
        SOAPBody body = envelope.getBody();
        SOAPElement foo = (SOAPElement)body.getFirstChild();
        if (!foo.getValue().endsWith("world")) {
            fail("The text is broken into multiple nodes");
        }
    }

    public void testBug4742689() throws Exception {
        MessageFactory mf = MessageFactory.newInstance();
        SOAPMessage sm=mf.createMessage();
        sm.getSOAPPart().getEnvelope().getBody().addChildElement("test").addTextNode("<![CDATA[testing]]>");
        SOAPBody body = sm.getSOAPBody();
        SOAPBodyElement element = (SOAPBodyElement) body.getFirstChild();
        assertEquals(element.getValue(), "testing");
    }
    
    public void testBug6389297() throws SOAPException{
        MessageFactory factory = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
        SOAPMessage message = factory.createMessage();
        SOAPEnvelope envelope = message.getSOAPPart().getEnvelope();
        SOAPBody body = envelope.getBody();
        SOAPFault fault = body.addFault();
        fault.addFaultReasonText("Version Mismatch", Locale.ENGLISH);
        org.w3c.dom.Node reason = fault.getLastChild();
        Element text = (Element)reason.getFirstChild();
        //returns an empty string for xmlns:xml
        assertEquals("", text.getAttribute("xmlns:xml"));       
    }

    //TODO : Need to add assert statements in all the tests below.....
     public static void testSAAJIssue44And37() throws Exception {
         //TestCases for SAAJ Issue 44 and 37
        byte[] bytes = new byte[0];
        InputStream in = new ByteArrayInputStream(bytes);
        MimeHeaders headers = new MimeHeaders();
        MimeHeader header = new MimeHeader("Content-Type", "text/xml");
        MessageFactory mf = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
        SOAPMessage m = mf.createMessage();
        SOAPBody body = m.getSOAPBody();
        String action = "exampleAction";
        ((MessageImpl)m).setAction(action);
        m.writeTo(System.out);
        String[] ctyp = m.getMimeHeaders().getHeader("Content-Type");
    }

    public static void testSAAJIssue39() throws SOAPException, IOException {
        MessageFactory mf = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
        SOAPMessage m = mf.createMessage();
        m.getSOAPBody().addTextNode("This is a test body");

        SOAPHeader hdr = m.getSOAPHeader();
        SOAPHeaderElement hdre = (SOAPHeaderElement)hdr.addChildElement("MYHeader","test", "http://tmpuri");
        hdre.addTextNode("This is a test header");
        m.saveChanges();
        AttachmentPart ap = m.createAttachmentPart(new DataHandler(new FileDataSource("src/test/mime/data/java.gif")));
        m.addAttachmentPart(ap);
        m.saveChanges();
        //m.writeTo(System.out);
        //test here if we call removeAttachments will it set back the
        //optimizeAttachments flag to true again before/while doing a savechanges.
    }

    public static void testSAAJIssue38() throws Exception {
        MessageFactory mf = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
        SOAPMessage m = mf.createMessage();
        SOAPPart sp = m.getSOAPPart();
        sp.setContent(new StreamSource(new FileInputStream(new File("src/test/bugfixes/data/service.wsdl"))));
        SOAPEnvelope env = sp.getEnvelope();
        //m.writeTo(System.out);
        System.out.println("=======================\n\n");
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        Document doc = dbf.newDocumentBuilder().parse(new File("src/test/bugfixes/data/sts.wsdl"));
        Element elem = doc.getDocumentElement();
        org.w3c.dom.Node xmlDecl = elem.getFirstChild();
        if (xmlDecl.getNodeType() == Node.DOCUMENT_TYPE_NODE) {
            System.out.println("This Node is an XMLDecl node");
        }
        //elem.getNodeType().
        DOMSource domSource = new DOMSource(elem);
        sp.setContent(domSource);
        env = sp.getEnvelope();
        //m.writeTo(System.out);
    }
    public static void testSAAJIssue47() throws Exception {
       //should work and treat the QName as NCName
        MessageFactory mf1 = MessageFactory.newInstance();
        SOAPMessage m1 = mf1.createMessage();
        SOAPFault fault1 = SOAPFactory.newInstance().createFault("This is a test Fault", new QName(null, "TestFaultCode", ""));
        m1.getSOAPBody().addChildElement(fault1);
        //m1.writeTo(System.out);

        //should throw and exception
        try {
        MessageFactory mf = MessageFactory.newInstance();
        SOAPMessage m = mf.createMessage();
        SOAPFault fault = SOAPFactory.newInstance().createFault("This is a test Fault", new QName(null, "TestFaultCode", "myprefix"));
        m.getSOAPBody().addChildElement(fault);
        //m.writeTo(System.out);
        } catch (Exception e) {
            //should throw an exception
        }

    }

    public static void testSAAJIssue46() throws SOAPException, FileNotFoundException {
        /* uncomment and fix the file being used. and add assert stmts
        for (int j = 0; j < 10; j++) {
            long start = System.currentTimeMillis();
            for (int i = 0; i < 10000; i++) {
                MessageFactory mf = MessageFactory.newInstance(SOAPConstants.SOAP_1_2_PROTOCOL);
                SOAPMessage m = mf.createMessage();
                SOAPPart sp = m.getSOAPPart();
                sp.setContent(new StreamSource(new FileInputStream(new File("C:\\Users\\Kumar\\Desktop\\service.wsdl"))));
                SOAPEnvelope env = sp.getEnvelope();
                SOAPBody body = env.getBody();
            }
            long end = System.currentTimeMillis();
            System.out.println("Time Taken =" + (end - start));
        } */
    }

    public static void testSAAJIssue31() throws Exception {
        /* TODO: uncomment and fix the file being used and add assert stmts
        System.setProperty("saaj.use.mimepull", "true");
        MessageFactory mf = MessageFactory.newInstance();
        SOAPMessage m = mf.createMessage();
        m.getSOAPBody().addTextNode("This is a test body");

        SOAPHeader hdr = m.getSOAPHeader();
        SOAPHeaderElement hdre = (SOAPHeaderElement)hdr.addChildElement("MYHeader","test", "http://tmpuri");
        hdre.addTextNode("This is a test header");
        m.saveChanges();
        AttachmentPart ap = m.createAttachmentPart(new DataHandler(new FileDataSource("C:\\glassfish.zip")));
        m.addAttachmentPart(ap);
        m.saveChanges();
        m.writeTo(new FileOutputStream(new File("C:\\bigmessage.xml")));


        SOAPMessage created = mf.createMessage(m.getMimeHeaders(), new FileInputStream(new File("C:\\bigmessage.xml")));
        Iterator it = created.getAttachments();
        AttachmentPart at = (AttachmentPart)it.next();
        created.writeTo(new FileOutputStream(new File("C:\\bigmessage1.xml")));*/

    }

    public static void testSAAJIssue48() throws Exception {
        /* TODO: add a testcase here 
         * The data folder has the MTOM 1.1 and 1.2 messages for creating the
         * test */

    }
    public static void testSAAJIssue49() throws SOAPException, FileNotFoundException, IOException {
        MessageFactory mf = MessageFactory.newInstance();
        QName faultCode = new QName("http://schemas.xmlsoap.org/soap/envelope/",
                "MustUnderstand");
        String faultString = "test message";
        SOAPFault fault = SOAPFactory.newInstance().createFault(faultString, faultCode);
        Detail d = fault.addDetail();
        d.addDetailEntry(new QName("", "entry1"));
        fault.setFaultActor("http://example.org/actor");
        SOAPMessage m = mf.createMessage();
        m.getSOAPBody().addChildElement(fault);
        //m.writeTo(System.out);
    }


    public static void main(String[] args) throws Exception {
        if (th.isDebug()) {
            // Run a subset of tests.  Developers should feel free to
            // change which tests are run here.  As of Dec 2002, one can
            // use the "test-1" Ant target to run these tests in debug
            // mode. [eeg 07jan03]
            TestSuite ts = new TestSuite();
            //ts.addTest(new BugfixesTest("testFaultDetailSoap1_2"));
            ts.addTest(new BugfixesTest("testFaultUnqualifiedSubElements"));
            junit.textui.TestRunner.run(ts);
        } else {
            junit.textui.TestRunner.run(BugfixesTest.class);
        }
    }
}
