/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
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

package com.sun.xml.messaging.saaj.soap.impl;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import java.util.logging.Level;

import javax.xml.namespace.QName;
import javax.xml.soap.*;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import com.sun.xml.messaging.saaj.SOAPExceptionImpl;
import com.sun.xml.messaging.saaj.soap.LazyEnvelope;
import com.sun.xml.messaging.saaj.soap.SOAPDocumentImpl;
import com.sun.xml.messaging.saaj.soap.StaxBridge;
import com.sun.xml.messaging.saaj.soap.StaxLazySourceBridge;
import com.sun.xml.messaging.saaj.soap.name.NameImpl;
import com.sun.xml.messaging.saaj.util.FastInfosetReflection;
import com.sun.xml.messaging.saaj.util.stax.LazyEnvelopeStaxReader;
import com.sun.xml.messaging.saaj.util.transform.EfficientStreamingTransformer;

import org.jvnet.staxex.util.DOMStreamReader;
import org.jvnet.staxex.util.XMLStreamReaderToXMLStreamWriter;

/**
 * Our implementation of the SOAP envelope.
 *
 * @author Anil Vijendran (anil@sun.com)
 */
public abstract class EnvelopeImpl extends ElementImpl implements LazyEnvelope {
    protected HeaderImpl header;
    protected BodyImpl body;
    String omitXmlDecl = "yes";
    String charset = "utf-8";
    String xmlDecl = null;
    
    protected EnvelopeImpl(SOAPDocumentImpl ownerDoc, Name name) {
        super(ownerDoc, name);
    }

    protected EnvelopeImpl(SOAPDocumentImpl ownerDoc, QName name) {
        super(ownerDoc, name);
    }

    protected EnvelopeImpl(
        SOAPDocumentImpl ownerDoc,
        NameImpl name,
        boolean createHeader,
        boolean createBody)
        throws SOAPException {
        this(ownerDoc, name);

        ensureNamespaceIsDeclared(
            getElementQName().getPrefix(), getElementQName().getNamespaceURI());

        // XXX
        if (createHeader)
            addHeader();

        if (createBody)
            addBody();
    }

    protected abstract NameImpl getHeaderName(String prefix);
    protected abstract NameImpl getBodyName(String prefix);

    public SOAPHeader addHeader() throws SOAPException {
        return addHeader(null);
    }
    
    public SOAPHeader addHeader(String prefix) throws SOAPException {
        
        if (prefix == null || prefix.equals("")) {
            prefix = getPrefix();
        }
        
        NameImpl headerName = getHeaderName(prefix);
        NameImpl bodyName = getBodyName(prefix);
        
        HeaderImpl header = null;
        SOAPElement firstChild = (SOAPElement) getFirstChildElement();
 
        if (firstChild != null) {
            if (firstChild.getElementName().equals(headerName)) {
                log.severe("SAAJ0120.impl.header.already.exists");
                throw new SOAPExceptionImpl("Can't add a header when one is already present.");
            } else if (!firstChild.getElementName().equals(bodyName)) {
                log.severe("SAAJ0121.impl.invalid.first.child.of.envelope");
                throw new SOAPExceptionImpl("First child of Envelope must be either a Header or Body");
            }
        }

        header = (HeaderImpl) createElement(headerName);
        insertBefore(header, firstChild);
        header.ensureNamespaceIsDeclared(headerName.getPrefix(), headerName.getURI());

        return header;
    }

    protected void lookForHeader() throws SOAPException {
        NameImpl headerName = getHeaderName(null);

        HeaderImpl hdr = (HeaderImpl) findChild(headerName);
        header = hdr;
    }

    public SOAPHeader getHeader() throws SOAPException {
        lookForHeader();
        return header;
    }

    protected void lookForBody() throws SOAPException {
        NameImpl bodyName = getBodyName(null);

        BodyImpl bodyChildElement = (BodyImpl) findChild(bodyName);
        body = bodyChildElement;
    }

    public SOAPBody addBody() throws SOAPException {
        return addBody(null);
    }
    
    public SOAPBody addBody(String prefix) throws SOAPException {
        lookForBody();

        if (prefix == null || prefix.equals("")) {
            prefix = getPrefix();
        }
        
        if (body == null) {
            NameImpl bodyName = getBodyName(prefix);
            body = (BodyImpl) createElement(bodyName);
            insertBefore(body, null);
            body.ensureNamespaceIsDeclared(bodyName.getPrefix(), bodyName.getURI());
        } else {
            log.severe("SAAJ0122.impl.body.already.exists");
            throw new SOAPExceptionImpl("Can't add a body when one is already present.");
        }

        return body;
    }

    protected SOAPElement addElement(Name name) throws SOAPException {
        if (getBodyName(null).equals(name)) {
            return addBody(name.getPrefix());
        }
        if (getHeaderName(null).equals(name)) {
            return addHeader(name.getPrefix());
        }

        return super.addElement(name);
    }

    protected SOAPElement addElement(QName name) throws SOAPException {
        if (getBodyName(null).equals(NameImpl.convertToName(name))) {
            return addBody(name.getPrefix());
        }
        if (getHeaderName(null).equals(NameImpl.convertToName(name))) {
            return addHeader(name.getPrefix());
        }

        return super.addElement(name);
    }

    public SOAPBody getBody() throws SOAPException {
        lookForBody();
        return body;
    }

    public Source getContent() {
        return new DOMSource(getOwnerDocument());
    }

    public Name createName(String localName, String prefix, String uri)
        throws SOAPException {

        // validating parameters before passing them on
        // to make sure that the namespace specification rules are followed

        // reserved xmlns prefix cannot be used.
        if ("xmlns".equals(prefix)) {
            log.severe("SAAJ0123.impl.no.reserved.xmlns");
            throw new SOAPExceptionImpl("Cannot declare reserved xmlns prefix");
        }
        // Qualified name cannot be xmlns.
        if ((prefix == null) && ("xmlns".equals(localName))) {
            log.severe("SAAJ0124.impl.qualified.name.cannot.be.xmlns");
            throw new SOAPExceptionImpl("Qualified name cannot be xmlns");
        }

        return NameImpl.create(localName, prefix, uri);
    }

    public Name createName(String localName, String prefix)
        throws SOAPException {
        String namespace = getNamespaceURI(prefix);
        if (namespace == null) {
            log.log(
                Level.SEVERE,
                "SAAJ0126.impl.cannot.locate.ns", 
                new String[] { prefix });
            throw new SOAPExceptionImpl(
                "Unable to locate namespace for prefix " + prefix);
        }
        return NameImpl.create(localName, prefix, namespace);
    }

    public Name createName(String localName) throws SOAPException {
        return NameImpl.createFromUnqualifiedName(localName);
    }
    
    public void setOmitXmlDecl(String value) {
        this.omitXmlDecl = value;        
    }

    public void setXmlDecl(String value) {
        this.xmlDecl = value;        
    }
    
    private String getOmitXmlDecl() {
        return this.omitXmlDecl;
    }
    
    public void setCharsetEncoding(String value) {
        charset = value;
    }
    
    public void output(OutputStream out) throws IOException {
        try {
//            materializeBody();
            Transformer transformer =
                EfficientStreamingTransformer.newTransformer();

            transformer.setOutputProperty(
                OutputKeys.OMIT_XML_DECLARATION, "yes");
                /*omitXmlDecl);*/
            // no equivalent for "setExpandEmptyElements"
            transformer.setOutputProperty(
                OutputKeys.ENCODING, 
                charset);

            if (omitXmlDecl.equals("no") && xmlDecl == null) {
                xmlDecl = "<?xml version=\"" + getOwnerDocument().getXmlVersion() + "\" encoding=\"" + 
                    charset + "\" ?>";
            }
        
           StreamResult result = new StreamResult(out);
            if (xmlDecl != null) {
                OutputStreamWriter writer = new OutputStreamWriter(out, charset);
                writer.write(xmlDecl);
                writer.flush();
                result = new StreamResult(writer);
            }
           
            if (log.isLoggable(Level.FINE)) {
                log.log(Level.FINE, "SAAJ0190.impl.set.xml.declaration",
                        new String[] { omitXmlDecl });
                log.log(Level.FINE, "SAAJ0191.impl.set.encoding",
                        new String[] { charset });
            }
                
            //StreamResult result = new StreamResult(out);
            transformer.transform(getContent(), result);
        } catch (Exception ex) {
            throw new IOException(ex.getMessage());
        }
    }

    /**
     * Serialize to FI if boolean parameter set.
     */
    public void output(OutputStream out, boolean isFastInfoset) 
        throws IOException 
    {
        if (!isFastInfoset) {
            output(out);
        }
        else {
            try {
                // Run transform and generate FI output from content
                Source source = getContent();
                Transformer transformer = EfficientStreamingTransformer.newTransformer(); 
                    transformer.transform(getContent(),
                        FastInfosetReflection.FastInfosetResult_new(out));
            }
            catch (Exception ex) {
                throw new IOException(ex.getMessage());
            }
        }
    }

    //    public void prettyPrint(OutputStream out) throws IOException {
    //        if (getDocument() == null)
    //            initDocument();
    //
    //        OutputFormat format = OutputFormat.createPrettyPrint();
    //
    //        format.setIndentSize(2);
    //        format.setNewlines(true);
    //        format.setTrimText(true);
    //        format.setPadText(true);
    //        format.setExpandEmptyElements(false);
    //
    //        XMLWriter writer = new XMLWriter(out, format);
    //        writer.write(getDocument());
    //    }
    //
    //    public void prettyPrint(Writer out) throws IOException {
    //        if (getDocument() == null)
    //            initDocument();
    //
    //        OutputFormat format = OutputFormat.createPrettyPrint();
    //
    //        format.setIndentSize(2);
    //        format.setNewlines(true);
    //        format.setTrimText(true);
    //        format.setPadText(true);
    //        format.setExpandEmptyElements(false);
    //
    //        XMLWriter writer = new XMLWriter(out, format);
    //        writer.write(getDocument());
    //    }


     public SOAPElement setElementQName(QName newName) throws SOAPException {
        log.log(Level.SEVERE,
                "SAAJ0146.impl.invalid.name.change.requested",
                new Object[] {elementQName.getLocalPart(),
                              newName.getLocalPart()});
        throw new SOAPException("Cannot change name for "
                                + elementQName.getLocalPart() + " to "
                                + newName.getLocalPart());
     }

    @Override
    public void setStaxBridge(StaxBridge bridge) throws SOAPException {
        //set it on the body
        ((BodyImpl) getBody()).setStaxBridge(bridge);
    }

    @Override
    public StaxBridge getStaxBridge() throws SOAPException {
        return ((BodyImpl) getBody()).getStaxBridge();
    }

    @Override
    public XMLStreamReader getPayloadReader() throws SOAPException {
        return ((BodyImpl) getBody()).getPayloadReader();
    }
    
    @Override
    public void writeTo(final XMLStreamWriter writer) throws XMLStreamException, SOAPException {
    	StaxBridge readBridge = this.getStaxBridge();
    	if (readBridge != null && readBridge instanceof StaxLazySourceBridge) {
//        	StaxSoapWriteBridge writingBridge =  new StaxSoapWriteBridge(this);
//        	writingBridge.write(writer);     
        	final String soapEnvNS = this.getNamespaceURI();
        	final DOMStreamReader reader = new DOMStreamReader(this);
	        XMLStreamReaderToXMLStreamWriter writingBridge =  new XMLStreamReaderToXMLStreamWriter();	        
	        writingBridge.bridge( new XMLStreamReaderToXMLStreamWriter.Breakpoint(reader, writer) {
        		public boolean proceedAfterStartElement()  { 
        			if ("Body".equals(reader.getLocalName()) && soapEnvNS.equals(reader.getNamespaceURI()) ){
        				return false;
        			} else
        				return true; 
        		}
            });//bridgeToBodyStartTag
            ((StaxLazySourceBridge)readBridge).writePayloadTo(writer);
            writer.writeEndElement();//body         
            writer.writeEndElement();//env
            writer.writeEndDocument();
            writer.flush();     
    	} else {
	        LazyEnvelopeStaxReader lazyEnvReader = new LazyEnvelopeStaxReader(this);
	        XMLStreamReaderToXMLStreamWriter writingBridge = new XMLStreamReaderToXMLStreamWriter();
	        writingBridge.bridge(lazyEnvReader, writer);
//            writingBridge.bridge(new XMLStreamReaderToXMLStreamWriter.Breakpoint(lazyEnvReader, writer));
    	}
        //Assume the staxBridge is exhausted now since we would have read the body reader
        ((BodyImpl) getBody()).setPayloadStreamRead();
    }

    @Override
    public QName getPayloadQName() throws SOAPException {
        return ((BodyImpl) getBody()).getPayloadQName();
    }

    @Override
    public String getPayloadAttributeValue(String localName) throws SOAPException {
        return ((BodyImpl) getBody()).getPayloadAttributeValue(localName);
    }

    @Override
    public String getPayloadAttributeValue(QName qName) throws SOAPException {
        return ((BodyImpl) getBody()).getPayloadAttributeValue(qName);
    }

    @Override
    public boolean isLazy() {
        try {
            return ((BodyImpl) getBody()).isLazy();
        } catch (SOAPException e) {
            return false;
        }
    }
    
}

