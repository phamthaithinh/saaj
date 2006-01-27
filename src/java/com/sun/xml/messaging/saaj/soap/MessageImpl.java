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
 * $Id: MessageImpl.java,v 1.1.1.1 2006-01-27 13:10:56 kumarjayanti Exp $
 * $Revision: 1.1.1.1 $
 * $Date: 2006-01-27 13:10:56 $
 */

/*
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.sun.xml.messaging.saaj.soap;

import java.io.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.xml.soap.*;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import com.sun.xml.messaging.saaj.packaging.mime.Header;
import com.sun.xml.messaging.saaj.packaging.mime.internet.*;
import com.sun.xml.messaging.saaj.packaging.mime.util.*;
import com.sun.xml.messaging.saaj.packaging.mime.MessagingException;

import com.sun.xml.messaging.saaj.SOAPExceptionImpl;
import com.sun.xml.messaging.saaj.soap.impl.EnvelopeImpl;
import com.sun.xml.messaging.saaj.util.*;

/**
 * The message implementation for SOAP messages with
 * attachments. Messages for specific profiles will likely extend this
 * MessageImpl class and add more value for that particular profile.
 *
 * @author Anil Vijendran (akv@eng.sun.com)
 * @author Rajiv Mordani (rajiv.mordani@sun.com)
 * @author Manveen Kaur (manveen.kaur@sun.com)
 */

public abstract class MessageImpl
    extends SOAPMessage
    implements SOAPConstants {


    public static final String CONTENT_ID             = "Content-ID";
    public static final String CONTENT_LOCATION       = "Content-Location";

    protected static Logger log =
        Logger.getLogger(LogDomainConstants.SOAP_DOMAIN,
                         "com.sun.xml.messaging.saaj.soap.LocalStrings");

    protected static final int PLAIN_XML_FLAG      = 1;      // 00001
    protected static final int MIME_MULTIPART_FLAG = 2;      // 00010
    protected static final int SOAP1_1_FLAG = 4;             // 00100
    protected static final int SOAP1_2_FLAG = 8;             // 01000
    protected static final int MIME_MULTIPART_XOP_FLAG = 14; // 01110
    protected static final int XOP_FLAG = 13;                // 01101
    protected static final int FI_ENCODED_FLAG     = 16;     // 10000
    
    protected MimeHeaders headers;
    protected SOAPPartImpl soapPart;
    protected FinalArrayList attachments;
    protected boolean saved = false;
    protected byte[] messageBytes;
    protected int messageByteCount;
    protected HashMap properties = new HashMap();

    // used for lazy attachment initialization
    protected MimeMultipart multiPart = null;
    protected boolean attachmentsInitialized = false;

    /**
     * True if this part is encoded using Fast Infoset.
     * MIME -> application/fastinfoset
     */
    protected boolean isFastInfoset = false;  
    
    /**
     * True if the Accept header of this message includes
     * application/fastinfoset
     */
    protected boolean acceptFastInfoset = false;

    // switch back to old MimeMultipart incase of problem
    private static boolean switchOffBM = false;
    private static boolean switchOffLazyAttachment = false;

    static {
        try {
            String s = System.getProperty("saaj.mime.optimization");
            if ((s != null) && s.equals("false")) {
                switchOffBM = true;
            }
            s = System.getProperty("saaj.lazy.mime.optimization");
            if ((s != null) && s.equals("false")) {
                switchOffLazyAttachment = true;
            }
        } catch (SecurityException ex) {
            // ignore it
        }
    }

    //property to indicate optimized serialization for lazy attachments
    private boolean lazyAttachments = false;

    // most of the times, Content-Types are already all lower cased.
    // String.toLowerCase() works faster in this case, so even if you
    // are only doing one comparison, it pays off to use String.toLowerCase()
    // than String.equalsIgnoreCase(). When you do more than one comparison,
    // the benefits of String.toLowerCase() dominates.
    //
    //
    // for FI,
    //   use application/fastinfoset for SOAP 1.1
    //   use application/soap+fastinfoset for SOAP 1.2
    // to speed up comparisons, test methods always use lower cases.

    /**
     * @param primary
     *      must be all lower case
     * @param sub
     *      must be all lower case
     */
    private static boolean isSoap1_1Type(String primary, String sub) {
        return primary.equals("text") && sub.equals("xml")
            || primary.equals("application")
               && sub.equals("fastinfoset");
    }

    /**
     * @param type
     *      must be all lower case
     */
    private static boolean isEqualToSoap1_1Type(String type) {
        return type.startsWith("text/xml") ||
               type.startsWith("application/fastinfoset");
    }

    /**
     * @param primary
     *      must be all lower case
     * @param sub
     *      must be all lower case
     */
    private static boolean isSoap1_2Type(String primary, String sub) {
        return primary.equals("application")
               && (sub.equals("soap+xml")
                   || sub.equals("soap+fastinfoset"));
    }

    /**
     * @param type
     *      must be all lower case
     */
    private static boolean isEqualToSoap1_2Type(String type) {
        return type.startsWith("application/soap+xml") ||
               type.startsWith("application/soap+fastinfoset");
    }

    /**
      * Construct a new message. This will be invoked before message
      * sends.
      */
    protected MessageImpl() {
        this(false, false);
        attachmentsInitialized = true;
    }
    
    /**
      * Construct a new message. This will be invoked before message
      * sends.
      */
    protected MessageImpl(boolean isFastInfoset, boolean acceptFastInfoset) {
        this.isFastInfoset = isFastInfoset;
        this.acceptFastInfoset = acceptFastInfoset;
        
        headers = new MimeHeaders();
        headers.setHeader("Accept", getExpectedAcceptHeader());
    }

    /**
     * Shallow copy.
     */
    protected MessageImpl(SOAPMessage msg) {
        if (!(msg instanceof MessageImpl)) {
            // don't know how to handle this.
        }
        MessageImpl src = (MessageImpl) msg;
        this.headers = src.headers;
        this.soapPart = src.soapPart;
        this.attachments = src.attachments;
        this.saved = src.saved;
        this.messageBytes = src.messageBytes;
        this.messageByteCount = src.messageByteCount;
        this.properties = src.properties;
    }

    /**
     * @param stat
     *      the mask value obtained from {@link #identifyContentType(ContentType)}
     */
    protected static boolean isSoap1_1Content(int stat) {
        return (stat & SOAP1_1_FLAG) != 0;
    }

    /**
     * @param stat
     *      the mask value obtained from {@link #identifyContentType(ContentType)}
     */
    protected static boolean isSoap1_2Content(int stat) {
        return (stat & SOAP1_2_FLAG) != 0;
    }

     private static boolean isMimeMultipartXOPPackage(ContentType contentType) {
        String type = contentType.getParameter("type");
        if(type==null)
            return false;
                                                                                                                             
        type = type.toLowerCase();
        if(!type.startsWith("application/xop+xml"))
            return false;
                                                                                                                             
        String startinfo = contentType.getParameter("start-info");
        if(startinfo == null)
            return false;
        startinfo = startinfo.toLowerCase();
        return isEqualToSoap1_2Type(startinfo) || isEqualToSoap1_1Type(startinfo);
    }
 
    private static boolean isSOAPBodyXOPPackage(ContentType contentType){
        String primary = contentType.getPrimaryType();
        String sub = contentType.getSubType();

        if (primary.equalsIgnoreCase("application")) {
            if (sub.equalsIgnoreCase("xop+xml")) {
                String type = getTypeParameter(contentType);
                return isEqualToSoap1_2Type(type) || isEqualToSoap1_1Type(type);
            }
        }
        return false;
    }
    
    /**
     * Construct a message from an input stream. When messages are
     * received, there's two parts -- the transport headers and the
     * message content in a transport specific stream.
     */
    protected MessageImpl(MimeHeaders headers, final InputStream in)
        throws SOAPExceptionImpl {
        ContentType ct = parseContentType(headers);
        init(headers,identifyContentType(ct),ct,in);
    }

    private static ContentType parseContentType(MimeHeaders headers) throws SOAPExceptionImpl {
        final String ct;
        if (headers != null)
            ct = getContentType(headers);
        else {
            log.severe("SAAJ0550.soap.null.headers");
            throw new SOAPExceptionImpl("Cannot create message: " +
                                        "Headers can't be null");
        }

        if (ct == null) {
            log.severe("SAAJ0532.soap.no.Content-Type");
            throw new SOAPExceptionImpl("Absent Content-Type");
        }
        try {
            return new ContentType(ct);
        } catch (Throwable ex) {
            log.severe("SAAJ0535.soap.cannot.internalize.message");
            throw new SOAPExceptionImpl("Unable to internalize message", ex);
        }
    }

    /**
     * Construct a message from an input stream. When messages are
     * received, there's two parts -- the transport headers and the
     * message content in a transport specific stream.
     *
     * @param contentType
     *      The parsed content type header from the headers variable.
     *      This is redundant parameter, but it avoids reparsing this header again.
     * @param stat
     *      The result of {@link #identifyContentType(ContentType)} over
     *      the contentType parameter. This redundant parameter, but it avoids
     *      recomputing this information again.
     */
    protected MessageImpl(MimeHeaders headers, final ContentType contentType, int stat, final InputStream in) throws SOAPExceptionImpl {
        init(headers, stat, contentType, in);

    }

    private void init(MimeHeaders headers, int stat, final ContentType contentType, final InputStream in) throws SOAPExceptionImpl {
        this.headers = headers;

        try {

            // Set isFastInfoset/acceptFastInfoset flag based on MIME type
            if ((stat & FI_ENCODED_FLAG) > 0) {
                isFastInfoset = acceptFastInfoset = true;
            }

            // If necessary, inspect Accept header to set acceptFastInfoset
            if (!isFastInfoset) {
                String[] values = headers.getHeader("Accept");
                if (values != null) {
                    for (int i = 0; i < values.length; i++) {
                        StringTokenizer st = new StringTokenizer(values[i], ",");
                        while (st.hasMoreTokens()) {
                            final String token = st.nextToken().trim();
                            if (token.equalsIgnoreCase("application/fastinfoset") ||
                                token.equalsIgnoreCase("application/soap+fastinfoset")) {
                                acceptFastInfoset = true;
                                break;
                            }
                        }
                    }
                }
            }

            if (!isCorrectSoapVersion(stat)) {
                log.log(
                    Level.SEVERE,
                    "SAAJ0533.soap.incorrect.Content-Type",
                    new String[] {
                        contentType.toString(),
                        getExpectedContentType()});
                throw new SOAPVersionMismatchException(
                    "Cannot create message: incorrect content-type for SOAP version. Got: "
                        + contentType
                        + " Expected: "
                        + getExpectedContentType());
            }

            if ((stat & PLAIN_XML_FLAG) != 0) {
                if (isFastInfoset) {
                    getSOAPPart().setContent(
                        FastInfosetReflection.FastInfosetSource_new(in));
                } else {
                    initCharsetProperty(contentType);
                    getSOAPPart().setContent(new StreamSource(in));
                }
            }
            else if ((stat & MIME_MULTIPART_FLAG) != 0) {
                DataSource ds = new DataSource() {
                    public InputStream getInputStream() {
                        return in;
                    }

                    public OutputStream getOutputStream() {
                        return null;
                    }

                    public String getContentType() {
                        return contentType.toString();
                    }

                    public String getName() {
                        return "";
                    }
                };

                multiPart = null;
                if (switchOffBM) {
                    multiPart = new MimeMultipart(ds,contentType);
                } else {
                    multiPart = new BMMimeMultipart(ds,contentType);
                }

                String startParam = contentType.getParameter("start");
                MimeBodyPart soapMessagePart = null;
                String contentID = null;
                if (switchOffBM || switchOffLazyAttachment) {
                    if(startParam == null) {
                        soapMessagePart = multiPart.getBodyPart(0);
                        for (int i = 1; i < multiPart.getCount(); i++) {
                            initializeAttachment(multiPart, i);
                        }
                    } else {
                        soapMessagePart = multiPart.getBodyPart(startParam);
                        for (int i = 0; i < multiPart.getCount(); i++) {
                            contentID = multiPart.getBodyPart(i).getContentID();
                            if(!contentID.equals(startParam))
                                initializeAttachment(multiPart, i);
                        }
                    }
                } else {
                    BMMimeMultipart bmMultipart =
                        (BMMimeMultipart)multiPart;
                    InputStream stream = bmMultipart.initStream();

                    SharedInputStream sin = null;
                    if (stream instanceof SharedInputStream) {
                        sin = (SharedInputStream)stream;
                    }

                    String boundary = "--" +
                        contentType.getParameter("boundary");
                    byte[] bndbytes = ASCIIUtility.getBytes(boundary);
                    if (startParam == null) {
                        soapMessagePart =
                            bmMultipart.getNextPart(stream, bndbytes, sin);
                        bmMultipart.removeBodyPart(soapMessagePart);
                    } else {
                        MimeBodyPart bp = null;
                        try {
                            while(!startParam.equals(contentID)) {
                                bp = bmMultipart.getNextPart(
                                    stream, bndbytes, sin);
                                contentID = bp.getContentID();
                            }
                            soapMessagePart = bp;
                            bmMultipart.removeBodyPart(bp);
                        } catch (Exception e) {
                            throw new SOAPExceptionImpl(e);
                        }
                    }
                } 

                ContentType soapPartCType = new ContentType(
                                            soapMessagePart.getContentType());
                initCharsetProperty(soapPartCType);
                String baseType = soapPartCType.getBaseType().toLowerCase();
                if(!(isEqualToSoap1_1Type(baseType)
                  || isEqualToSoap1_2Type(baseType)
                  || isSOAPBodyXOPPackage(soapPartCType))) {
                    log.log(Level.SEVERE,
                            "SAAJ0549.soap.part.invalid.Content-Type",
                            new Object[] {baseType});
                    throw new SOAPExceptionImpl(
                            "Bad Content-Type for SOAP Part : " +
                            baseType);
                }

                SOAPPart soapPart = getSOAPPart();
                setMimeHeaders(soapPart, soapMessagePart);
                soapPart.setContent(isFastInfoset ?
                     (Source) FastInfosetReflection.FastInfosetSource_new(
                         soapMessagePart.getInputStream()) :
                     (Source) new StreamSource(soapMessagePart.getInputStream()));
            } else {
                log.severe("SAAJ0534.soap.unknown.Content-Type");
                throw new SOAPExceptionImpl("Unrecognized Content-Type");
            }
        } catch (Throwable ex) {
            log.severe("SAAJ0535.soap.cannot.internalize.message");
            throw new SOAPExceptionImpl("Unable to internalize message", ex);
        }
        needsSave();
    }

    public boolean isFastInfoset() {
        return isFastInfoset;
    }

    public boolean acceptFastInfoset() {
        return acceptFastInfoset;
    }
    
    public void setIsFastInfoset(boolean value) {
        if (value != isFastInfoset) {
            isFastInfoset = value;
            if (isFastInfoset) {
                acceptFastInfoset = true;
            }
            saved = false;      // ensure transcoding if necessary
        }
    }
    
    public Object getProperty(String property) {
        return (String) properties.get(property);
    }

    public void setProperty(String property, Object value) {
        verify(property, value);
        properties.put(property, value);
    }

    private void verify(String property, Object value) {
        if (property.equalsIgnoreCase(SOAPMessage.WRITE_XML_DECLARATION)) {
            if (!("true".equals(value) || "false".equals(value)))
                throw new RuntimeException(
                    property + " must have value false or true");

            try {
                EnvelopeImpl env = (EnvelopeImpl) getSOAPPart().getEnvelope();
                if ("true".equalsIgnoreCase((String)value)) {
                    env.setOmitXmlDecl("no");
                } else if ("false".equalsIgnoreCase((String)value)) {
                    env.setOmitXmlDecl("yes");
                }
            } catch (Exception e) {
                log.log(Level.SEVERE, "SAAJ0591.soap.exception.in.set.property", 
                    new Object[] {e.getMessage(), "javax.xml.soap.write-xml-declaration"});
                throw new RuntimeException(e);
            }
            return;
        }

        if (property.equalsIgnoreCase(SOAPMessage.CHARACTER_SET_ENCODING)) {
            try {
                ((EnvelopeImpl) getSOAPPart().getEnvelope()).setCharsetEncoding((String)value);
            } catch (Exception e) {
                log.log(Level.SEVERE, "SAAJ0591.soap.exception.in.set.property", 
                    new Object[] {e.getMessage(), "javax.xml.soap.character-set-encoding"});
                throw new RuntimeException(e);
            }
        }
    }

    protected abstract boolean isCorrectSoapVersion(int contentTypeId);
    
    protected abstract String getExpectedContentType();
    protected abstract String getExpectedAcceptHeader();

    /**
     * Sniffs the Content-Type header so that we can determine how to process.
     *
     * <p>
     * In the absence of type attribute we assume it to be text/xml.
     * That would mean we're easy on accepting the message and
     * generate the correct thing (as the SWA spec also specifies
     * that the type parameter should always be text/xml)
     *
     * @return
     *      combination of flags, such as PLAIN_XML_CODE and MIME_MULTIPART_CODE.
     */
    // SOAP1.2 allow SOAP1.2 content type
    static int identifyContentType(ContentType contentType)
        throws SOAPExceptionImpl {
        // TBD
        //    Is there anything else we need to verify here?

        String primary = contentType.getPrimaryType().toLowerCase();
        String sub = contentType.getSubType().toLowerCase();

        if (primary.equals("multipart")) {
            if (sub.equals("related")) {
                String type = getTypeParameter(contentType);
                if (isEqualToSoap1_1Type(type)) {
                    return (type.equals("application/fastinfoset") ?
                           FI_ENCODED_FLAG : 0) | MIME_MULTIPART_FLAG | SOAP1_1_FLAG;
                } 
                else if (isEqualToSoap1_2Type(type)) {
                    return (type.equals("application/soap+fastinfoset") ?
                           FI_ENCODED_FLAG : 0) | MIME_MULTIPART_FLAG | SOAP1_2_FLAG;
                } else if (isMimeMultipartXOPPackage(contentType)) {
                    return MIME_MULTIPART_XOP_FLAG;
                } else {
                    log.severe("SAAJ0536.soap.content-type.mustbe.multipart");
                    throw new SOAPExceptionImpl(
                        "Content-Type needs to be Multipart/Related "
                            + "and with \"type=text/xml\" "
                            + "or \"type=application/soap+xml\"");
                }
            } else {
                log.severe("SAAJ0537.soap.invalid.content-type");
                throw new SOAPExceptionImpl(
                    "Invalid Content-Type: " + primary + '/' + sub);
            }
        } 
        else if (isSoap1_1Type(primary, sub)) {
            return (primary.equalsIgnoreCase("application")
                    && sub.equalsIgnoreCase("fastinfoset") ?
                        FI_ENCODED_FLAG : 0) 
                   | PLAIN_XML_FLAG | SOAP1_1_FLAG;
        } 
        else if (isSoap1_2Type(primary, sub)) {
            return (primary.equalsIgnoreCase("application")
                    && sub.equalsIgnoreCase("soap+fastinfoset") ?
                        FI_ENCODED_FLAG : 0) 
                   | PLAIN_XML_FLAG | SOAP1_2_FLAG;
        } else if(isSOAPBodyXOPPackage(contentType)){
            return XOP_FLAG;
        } else {
            log.severe("SAAJ0537.soap.invalid.content-type");
            throw new SOAPExceptionImpl(
                "Invalid Content-Type:"
                    + primary
                    + '/'
                    + sub
                    + ". Is this an error message instead of a SOAP response?");
        }
    }

    /**
     * Obtains the type parameter of the Content-Type header. Defaults to "text/xml".
     */
    private static String getTypeParameter(ContentType contentType) {
        String p = contentType.getParameter("type");
        if(p!=null)
            return p.toLowerCase();
        else
            return "text/xml";
    }

    public MimeHeaders getMimeHeaders() {
        return this.headers;
    }

    final static String getContentType(MimeHeaders headers) {
        String[] values = headers.getHeader("Content-Type");
        if (values == null)
            return null;
        else
            return values[0];
    }
    
    /*
     * Get the complete ContentType value along with optional parameters.
     */
    public String getContentType() {
        return getContentType(this.headers);
    }

    public void setContentType(String type) {
        headers.setHeader("Content-Type", type);
        needsSave();
    }

    private ContentType ContentType() {
        ContentType ct = null;
        try {
            ct = new ContentType(getContentType());
        } catch (Exception e) {
            // what to do here?
        }
        return ct;
    }

    /*
     * Return the MIME type string, without the parameters.
     */
    public String getBaseType() {
        return ContentType().getBaseType();
    }

    public void setBaseType(String type) {
        ContentType ct = ContentType();
        ct.setParameter("type", type);
        headers.setHeader("Content-Type", ct.toString());
        needsSave();
    }

    public String getAction() {
        return ContentType().getParameter("action");
    }

    public void setAction(String action) {
        ContentType ct = ContentType();
        ct.setParameter("action", action);
        headers.setHeader("Content-Type", ct.toString());
        needsSave();
    }

    public String getCharset() {
        return ContentType().getParameter("charset");
    }

    public void setCharset(String charset) {
        ContentType ct = ContentType();
        ct.setParameter("charset", charset);
        headers.setHeader("Content-Type", ct.toString());
        needsSave();
    }

    /**
     * All write methods (i.e setters) should call this method in
     * order to make sure that a save is necessary since the state
     * has been modified.
     */
    private final void needsSave() {
        saved = false;
    }

    public  boolean saveRequired() {
        return saved != true;
    }

    public String getContentDescription() {
        String[] values = headers.getHeader("Content-Description");
        if (values != null && values.length > 0)
            return values[0];
        return null;
    }

    public void setContentDescription(String description) {
        headers.setHeader("Content-Description", description);
        needsSave();
    }

    public abstract SOAPPart getSOAPPart();

    public void removeAllAttachments() {
        try {
            initializeAllAttachments();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        if (attachments != null) {
            attachments.clear();
            needsSave();
        }
    }

    public int countAttachments() {
        try {
            initializeAllAttachments();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (attachments != null)
            return attachments.size();
        return 0;
    }

    public void addAttachmentPart(AttachmentPart attachment) {
        try {
            initializeAllAttachments();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (attachments == null)
            attachments = new FinalArrayList();

        attachments.add(attachment);

        needsSave();
    }

    static private final Iterator nullIter = Collections.emptyList().iterator();

    public Iterator getAttachments() {
        try {
            initializeAllAttachments();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (attachments == null)
            return nullIter;
        return attachments.iterator();
    }

    private class MimeMatchingIterator implements Iterator {
        public MimeMatchingIterator(MimeHeaders headers) {
            this.headers = headers;
            this.iter = attachments.iterator();
        }

        private Iterator iter;
        private MimeHeaders headers;
        private Object nextAttachment;

        public boolean hasNext() {
            if (nextAttachment == null)
                nextAttachment = nextMatch();
            return nextAttachment != null;
        }

        public Object next() {
            if (nextAttachment != null) {
                Object ret = nextAttachment;
                nextAttachment = null;
                return ret;
            }

            if (hasNext())
                return nextAttachment;

            return null;
        }

        Object nextMatch() {
            while (iter.hasNext()) {
                AttachmentPartImpl ap = (AttachmentPartImpl) iter.next();
                if (ap.hasAllHeaders(headers))
                    return ap;
            }
            return null;
        }

        public void remove() {
            iter.remove();
        }
    }

    public Iterator getAttachments(MimeHeaders headers) {
        try {
            initializeAllAttachments();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (attachments == null)
            return nullIter;

        return new MimeMatchingIterator(headers);
    }

    public void removeAttachments(MimeHeaders headers) {
        try {
            initializeAllAttachments();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (attachments == null)
            return ;

        Iterator it =  new MimeMatchingIterator(headers);
        while (it.hasNext()) {
            int index = attachments.indexOf(it.next());
            attachments.set(index, null);
        }
        FinalArrayList f = new FinalArrayList();
        for (int i = 0; i < attachments.size(); i++) {
            if (attachments.get(i) != null) {
                f.add(attachments.get(i));
            }
        }
        attachments = f;
    }

    public AttachmentPart createAttachmentPart() {
        return new AttachmentPartImpl();
    }

    public  AttachmentPart getAttachment(SOAPElement element) 
        throws SOAPException {
        try {
            initializeAllAttachments();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        String uri;
        String hrefAttr = element.getAttribute("href");
        if ("".equals(hrefAttr)) {
            Node node = getValueNodeStrict(element);
            String swaRef = null;
            if (node != null) {
                swaRef = node.getValue();
            }
            if (swaRef == null || "".equals(swaRef)) {
                return null;
            } else {
                uri = swaRef;
            }
        } else {
            uri = hrefAttr;
        }
        return getAttachmentPart(uri);
    }

    private Node getValueNodeStrict(SOAPElement element) {
        Node node = (Node)element.getFirstChild();
        if (node != null) {
            if (node.getNextSibling() == null
                && node.getNodeType() == org.w3c.dom.Node.TEXT_NODE) {
                return node;
            } else {
                return null;
            }
        }
        return null;
    }

    
    private AttachmentPart getAttachmentPart(String uri) throws SOAPException {
        AttachmentPart _part;
        try {
            if (uri.startsWith("cid:")) {
                // rfc2392
                uri = '<'+uri.substring("cid:".length())+'>';
                
                MimeHeaders headersToMatch = new MimeHeaders();
                headersToMatch.addHeader(CONTENT_ID, uri);
                
                Iterator i = this.getAttachments(headersToMatch);
                _part = (i == null) ? null : (AttachmentPart)i.next();
            } else {
                // try content-location
                MimeHeaders headersToMatch = new MimeHeaders();
                headersToMatch.addHeader(CONTENT_LOCATION, uri);
                   
                Iterator i = this.getAttachments(headersToMatch);
                _part = (i == null) ? null : (AttachmentPart)i.next();
            }

            // try  auto-generated JAXRPC CID
            if (_part == null) {
                Iterator j = this.getAttachments();
                    
                while (j.hasNext()) {
                    AttachmentPart p = (AttachmentPart)j.next();
                    String cl = p.getContentId();
                    if (cl != null) {    
                        // obtain the partname
                        int eqIndex = cl.indexOf("=");
                        if (eqIndex > -1) {
                            cl = cl.substring(1, eqIndex);
                            if (cl.equalsIgnoreCase(uri)) {
                                _part = p;
                                 break;
                            }
                        }
                    } 
                }
            }
            
        } catch (Exception se) {
            log.log(Level.SEVERE, "SAAJ0590.soap.unable.to.locate.attachment", new Object[] {uri});
            throw new SOAPExceptionImpl(se);
        }
        return _part;
    }
    
    private final ByteInputStream getHeaderBytes()
        throws IOException {
        SOAPPartImpl sp = (SOAPPartImpl) getSOAPPart();
        return sp.getContentAsStream();
    }

    private String convertToSingleLine(String contentType) {
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < contentType.length(); i ++) {
            char c = contentType.charAt(i);
            if (c != '\r' && c != '\n' && c != '\t')
                buffer.append(c);
        }
        return buffer.toString();
    }

    private MimeMultipart getMimeMessage() throws SOAPException {
        try {
            SOAPPartImpl soapPart = (SOAPPartImpl) getSOAPPart();
            MimeBodyPart mimeSoapPart = soapPart.getMimePart();
            
            /*
             * Get content type from this message instead of soapPart
             * to ensure agreement if soapPart is transcoded (XML <-> FI)
             */
            ContentType soapPartCtype = new ContentType(getExpectedContentType());          
            
            if (!isFastInfoset) {
                soapPartCtype.setParameter("charset", initCharset());
            }
            mimeSoapPart.setHeader("Content-Type", soapPartCtype.toString());

            MimeMultipart headerAndBody = null;

            if (!switchOffBM && !switchOffLazyAttachment && 
                   (multiPart != null) && !attachmentsInitialized) {
                headerAndBody = new BMMimeMultipart();
                headerAndBody.addBodyPart(mimeSoapPart);
                if (attachments != null) { 
                    for (Iterator eachAttachment = attachments.iterator();
                         eachAttachment.hasNext();) {
                        headerAndBody.addBodyPart(
                            ((AttachmentPartImpl) eachAttachment.next())
                                .getMimePart());
                    }
                }
                InputStream in = ((BMMimeMultipart)multiPart).getInputStream();
                if (!((BMMimeMultipart)multiPart).lastBodyPartFound() &&
                    !((BMMimeMultipart)multiPart).isEndOfStream()) {
                    ((BMMimeMultipart)headerAndBody).setInputStream(in);
                    ((BMMimeMultipart)headerAndBody).setBoundary(
                        ((BMMimeMultipart)multiPart).getBoundary());
                    ((BMMimeMultipart)headerAndBody).
                        setLazyAttachments(lazyAttachments);
                }

            } else { 
                headerAndBody = new MimeMultipart();
                headerAndBody.addBodyPart(mimeSoapPart);

                for (Iterator eachAttachement = getAttachments();
                    eachAttachement.hasNext();
                    ) {
                    headerAndBody.addBodyPart(
                        ((AttachmentPartImpl) eachAttachement.next())
                            .getMimePart());
                }
            }

            ContentType contentType = headerAndBody.getContentType();

            ParameterList l = contentType.getParameterList();

            // set content type depending on SOAP version
            l.set("type", getExpectedContentType());
            l.set("boundary", contentType.getParameter("boundary"));
            ContentType nct = new ContentType("multipart", "related", l);

            headers.setHeader(
                "Content-Type",
                convertToSingleLine(nct.toString()));
          // TBD
          //    Set content length MIME header here.

            return headerAndBody;
        } catch (SOAPException ex) {
            throw ex;
        } catch (Throwable ex) {
            log.severe("SAAJ0538.soap.cannot.convert.msg.to.multipart.obj");
            throw new SOAPExceptionImpl(
                "Unable to convert SOAP message into "
                    + "a MimeMultipart object",
                ex);
        }
    }

    private String initCharset() {

        String charset = null;

        String[] cts = getMimeHeaders().getHeader("Content-Type");
        if ((cts != null) && (cts[0] != null)) {
            charset = getCharsetString(cts[0]);
        }

        if (charset == null) {
            charset = (String) getProperty(CHARACTER_SET_ENCODING);
        }

        if (charset != null) {
            return charset;
        }

        return "utf-8";
    }

    private String getCharsetString(String s) {
        try {
            int index = s.indexOf(";");
            if(index < 0)
                return null;
            ParameterList pl = new ParameterList(s.substring(index));
            return pl.get("charset");
        } catch(Exception e) {
            return null;
        }
    }

    public void saveChanges() throws SOAPException {

        // suck in all the data from the attachments and have it
        // ready for writing/sending etc.

        String charset = initCharset();

        /*if (countAttachments() == 0) {*/
        int attachmentCount = (attachments == null) ? 0 : attachments.size();
        if (attachmentCount == 0) {
            if (!switchOffBM && !switchOffLazyAttachment && 
                !attachmentsInitialized && (multiPart != null)) {
                // so there might be attachments
                attachmentCount = 1;
            }
        }

        try {
            if ((attachmentCount == 0) && !hasXOPContent()) {
                ByteInputStream in;
                try{
                /*
                 * Not sure why this is called getHeaderBytes(), but it actually
                 * returns the whole message as a byte stream. This stream could
                 * be either XML of Fast depending on the mode.
                 */
                    in = getHeaderBytes();
                } catch (IOException ex) {
                    log.severe("SAAJ0539.soap.cannot.get.header.stream");
                    throw new SOAPExceptionImpl(
                            "Unable to get header stream in saveChanges: ",
                            ex);
                }
                
                messageBytes = in.getBytes();
                messageByteCount = in.getCount();
                
                headers.setHeader(
                        "Content-Type",
                        getExpectedContentType() +
                        (isFastInfoset ? "" : "; charset=" + charset));
            } else {
                ByteOutputStream out = new ByteOutputStream();
                if(hasXOPContent()){
                    getXOPMessage().writeTo(out);
                }else{
                    MimeMultipart mmp = getMimeMessage();
                    mmp.writeTo(out);
                    if (!switchOffBM && !switchOffLazyAttachment &&
                            (multiPart != null) && !attachmentsInitialized) {
                        ((BMMimeMultipart)multiPart).setInputStream(
                                ((BMMimeMultipart)mmp).getInputStream());
                    }
                }
                messageBytes = out.getBytes();
                messageByteCount = out.getCount();
            }
        } catch (Throwable ex) {
            log.severe("SAAJ0540.soap.err.saving.multipart.msg");
            throw new SOAPExceptionImpl(
                    "Error during saving a multipart message",
                    ex);
        }
          
        headers.setHeader(
            "Content-Length",
            Integer.toString(messageByteCount));

        // FIX ME -- SOAP Action replaced by Content-Type optional parameter action
        /*
        if(isCorrectSoapVersion(SOAP1_1_FLAG)) {

            String[] soapAction = headers.getHeader("SOAPAction");

            if (soapAction == null || soapAction.length == 0)
                headers.setHeader("SOAPAction", "\"\"");

        } 
        */

        saved = true;
    }

    private MimeMultipart getXOPMessage() throws SOAPException {
        try {
            MimeMultipart headerAndBody = new MimeMultipart();
            SOAPPartImpl soapPart =  (SOAPPartImpl)getSOAPPart();
            MimeBodyPart mimeSoapPart = soapPart.getMimePart();
            ContentType soapPartCtype =
                new ContentType("application/xop+xml");
            soapPartCtype.setParameter("type", getExpectedContentType());
            String charset = initCharset();
            soapPartCtype.setParameter("charset", charset);
            mimeSoapPart.setHeader("Content-Type", soapPartCtype.toString());
            headerAndBody.addBodyPart(mimeSoapPart);
                                                                                                                                
            for (Iterator eachAttachement = getAttachments();
                eachAttachement.hasNext();
                ) {
                headerAndBody.addBodyPart(
                    ((AttachmentPartImpl) eachAttachement.next())
                        .getMimePart());
            }
                                                                                                                                
            ContentType contentType = headerAndBody.getContentType();
                                                                                                                                
            ParameterList l = contentType.getParameterList();
                                                                                                                                
            //lets not write start-info for now till we get servlet fix done
            l.set("start-info", getExpectedContentType());//+";charset="+initCharset());
                                                                                                                                
            // set content type depending on SOAP version
            l.set("type", "application/xop+xml");

            if (isCorrectSoapVersion(SOAP1_2_FLAG)) { 
                 String action = getAction();
                 if(action != null)
                     l.set("action", action);
            }
       
            l.set("boundary", contentType.getParameter("boundary"));
            ContentType nct = new ContentType("Multipart", "Related", l);
            headers.setHeader(
                "Content-Type",
                convertToSingleLine(nct.toString()));
            // TBD
            //    Set content length MIME header here.
                                                                                                                                
            return headerAndBody;
        } catch (SOAPException ex) {
            throw ex;
        } catch (Throwable ex) {
            log.severe("SAAJ0538.soap.cannot.convert.msg.to.multipart.obj");
            throw new SOAPExceptionImpl(
                "Unable to convert SOAP message into "
                    + "a MimeMultipart object",
                ex);
        }
                                                                                                                                
    }

    private boolean hasXOPContent() throws ParseException {
        String type = getContentType();
        if(type == null)
            return false;
        ContentType ct = new ContentType(type);
        return isMimeMultipartXOPPackage(ct) || isSOAPBodyXOPPackage(ct);
    }

    public void writeTo(OutputStream out) throws SOAPException, IOException {
        if (saveRequired())
            saveChanges();


        out.write(messageBytes, 0, messageByteCount);

        if(isCorrectSoapVersion(SOAP1_1_FLAG)) {

            String[] soapAction = headers.getHeader("SOAPAction");

            if (soapAction == null || soapAction.length == 0)
                headers.setHeader("SOAPAction", "\"\"");

        } 
        
        messageBytes = null;
        needsSave();
    }
    
    public SOAPBody getSOAPBody() throws SOAPException {
        return getSOAPPart().getEnvelope().getBody();
    }

    public SOAPHeader getSOAPHeader() throws SOAPException {
        return getSOAPPart().getEnvelope().getHeader();
    }

    private void initializeAllAttachments () 
        throws MessagingException, SOAPException {
        if (switchOffBM || switchOffLazyAttachment) {
            return;
        }

        if (attachmentsInitialized || (multiPart == null)) {
            return;
        }
                                                                                
        if (attachments == null)
            attachments = new FinalArrayList();
                                                                                
        int count = multiPart.getCount();
        for (int i=0; i < count; i++ ) {
            initializeAttachment(multiPart.getBodyPart(i));
        }
        attachmentsInitialized = true;
        //multiPart = null;
        needsSave();
     }
                                                                                
    private void initializeAttachment(MimeBodyPart mbp) throws SOAPException {
        AttachmentPartImpl attachmentPart = new AttachmentPartImpl();
        DataHandler attachmentHandler = mbp.getDataHandler();
        attachmentPart.setDataHandler(attachmentHandler);
                                                                                
        AttachmentPartImpl.copyMimeHeaders(mbp, attachmentPart);
        attachments.add(attachmentPart);
    }

    private void initializeAttachment(MimeMultipart multiPart, int i)
        throws Exception {
        MimeBodyPart currentBodyPart = multiPart.getBodyPart(i);
        AttachmentPartImpl attachmentPart = new AttachmentPartImpl();

        DataHandler attachmentHandler = currentBodyPart.getDataHandler();
        attachmentPart.setDataHandler(attachmentHandler);

        AttachmentPartImpl.copyMimeHeaders(currentBodyPart, attachmentPart);
        addAttachmentPart(attachmentPart);
    }

    private void setMimeHeaders(SOAPPart soapPart,
            MimeBodyPart soapMessagePart) throws Exception {

        // first remove the existing content-type
        soapPart.removeAllMimeHeaders();
        // add everything present in soapMessagePart
        List headers = soapMessagePart.getAllHeaders();
        int sz = headers.size();
        for( int i=0; i<sz; i++ ) {
            Header h = (Header) headers.get(i);
            soapPart.addMimeHeader(h.getName(), h.getValue());
        }
    }

    private void initCharsetProperty(ContentType contentType) {
        String charset = contentType.getParameter("charset");
        if (charset != null) {
            ((SOAPPartImpl) getSOAPPart()).setSourceCharsetEncoding(charset);
            if(!charset.equalsIgnoreCase("utf-8"))
                setProperty(CHARACTER_SET_ENCODING, charset);
        }
    }

    public void setLazyAttachments(boolean flag) {
        lazyAttachments = flag;
    }

}
