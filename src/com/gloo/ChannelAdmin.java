package com.gloo;

/**
 * @author denny
 *
 */
import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.wowza.wms.bootstrap.Bootstrap;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.server.Server;

public class ChannelAdmin {
    private static HashMap<String, Channel> channels;
	private static WMSLogger log = WMSLoggerFactory.getLogger(null); 
	
	public static HashMap<String, Channel> getChannels() throws Exception {
		initChannels(false);
		return channels;
	}
	
	// Get Config file path which is default conf path/GlooChannels.xml
	private static String getChannelXmlPath() {
	
		String p = Bootstrap.getServerHome(Bootstrap.APPHOME) + File.separator + "conf" + File.separator + "GlooChannels.xml";
    	String xmlPath = Server.getInstance().getProperties().getPropertyStr("GlooChannelXMLPath", p);	
    	
    	return xmlPath;
	}
	
	/**
	 * Builder channels array base on XML file
	 * read success, builder channels, or throw exception
	 * 
	 * If will be called when follow :
	 *  initialize channel
	 *  update channel
	 *  add channel
	 *  delete channel
	 * 
	 * @throws Exception
	 */
	private static void initChannels(Boolean rebuild) throws Exception {		
		if(channels != null && !rebuild) return;
		
    	String xmlPath = getChannelXmlPath();
    	
    	File channelXml = new File(xmlPath);
    	if(!channelXml.exists()) {
    		throw new Exception("Channels Config File : " + xmlPath + "do not exist!");
    	}

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document document = db.parse(channelXml);
        document.getDocumentElement().normalize();
        
        if(channels == null) channels = new HashMap<String, Channel>();
        Set<String> keys = new HashSet<String>(channels.keySet());
        
        NodeList xmlChannels = document.getElementsByTagName("channel");
        for (int i = 0; i < xmlChannels.getLength(); i++) {
            Node xmlChannel = xmlChannels.item(i);
            
            if (xmlChannel.getNodeType() == Node.ELEMENT_NODE)
            {
                Element e = (Element) xmlChannel;    
                String id = e.getAttribute("id");
                keys.remove(id);
                
                if(channels.containsKey(id)) {
                	channels.get(id).setTitle(e.getAttribute("title"));
                	channels.get(id).setDefaultFile(e.getAttribute("default_file"));
                } else {
                	//log.info("add " + id);
                	
                    Channel c = new Channel(id, e.getAttribute("title"), e.getAttribute("default_file"));
                    channels.put(id, c);
                }

            }   
        }
        
        for (String s : keys) {
        	//log.info("remove " + s);
        	
        	channels.remove(s);
        }
	}
	
	public static Channel getChannel(String id) throws Exception {
		initChannels(false);
		
		if(! channels.containsKey(id)) throw new Exception("There is not this channel.");
		
		return channels.get(id);
	}
	
	public static void addChannel(String id, String title, String file) throws Exception {
		initChannels(false);
		
		id = id.trim();
		title = title.trim();
		file = file.trim();
		
		if(id.isEmpty()) throw new Exception("ID can not be blank!");
		if(title.isEmpty()) throw new Exception("Title can not be blank!");
		if(file.isEmpty()) throw new Exception("Default mp4 file can not be blank!");

		if(channels.containsKey(id))  throw new Exception("ID has existed!");
		
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document document = db.parse(new File(getChannelXmlPath()));
        //document.getDocumentElement().normalize();
		
        Element e = document.createElement("channel");
        e.setAttribute("id", id);
        e.setAttribute("title", title);
        e.setAttribute("default_file", file);
        
        Element xmlRoot = document.getDocumentElement();
        xmlRoot.appendChild(e);
        
        saveChannelXml(document);

//		return "";
	}
	
	public static void saveChannel(String id, String title, String file) throws Exception {
		initChannels(false);
		
		id = id.trim();
		title = title.trim();
		file = file.trim();
		
		if(id.isEmpty()) throw new Exception("ID can not be blank!");
		if(title.isEmpty()) throw new Exception("Title can not be blank!");
		if(file.isEmpty()) throw new Exception("Default config file can not be blank!");

		if(! channels.containsKey(id))  throw new Exception("ID do not exist!");
		
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document document = db.parse(new File(getChannelXmlPath()));
        
        NodeList xmlChannels = document.getElementsByTagName("channel");
        for (int i = 0; i < xmlChannels.getLength(); i++) {
            Node xmlChannel = xmlChannels.item(i);
            
            if (xmlChannel.getNodeType() == Node.ELEMENT_NODE)
            {
                Element e = (Element) xmlChannel;    
                if(e.getAttribute("id").equals(id)) {
                    e.setAttribute("title", title);
                    e.setAttribute("default_file", file);
                }
            }
        }
        
        saveChannelXml(document);
	}
	
	public static void deleteChannel(String id) throws Exception {
		initChannels(false);
		
		id = id.trim();
		if(id.isEmpty()) throw new Exception("ID can not be blank!");
		if(! channels.containsKey(id))  throw new Exception("ID does not exist!");

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document document = db.parse(new File(getChannelXmlPath()));
        
        NodeList xmlChannels = document.getElementsByTagName("channel");
        for (int i = 0; i < xmlChannels.getLength(); i++) {
            Node xmlChannel = xmlChannels.item(i);
            
            if (xmlChannel.getNodeType() == Node.ELEMENT_NODE)
            {
                Element e = (Element) xmlChannel;    
                if(e.getAttribute("id").equals(id)) {
                    document.getDocumentElement().removeChild(xmlChannel);
                }
            }
        }
        
        saveChannelXml(document);
	}
	
    private static void saveChannelXml(Document xml) throws Exception {
	    Transformer tf = TransformerFactory.newInstance().newTransformer();
	    tf.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
	    tf.setOutputProperty(OutputKeys.INDENT, "yes");
	    tf.transform(new DOMSource(xml), new StreamResult(new File(getChannelXmlPath())));
	    
	    initChannels(true);
	}
	
}
