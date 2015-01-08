package com.gloo;

import java.io.*;
import java.net.URLDecoder;
import java.util.*;


import com.gloo.Channel.PlaylistRequest;
import com.google.gson.Gson;
import com.wowza.wms.vhost.*;
import com.wowza.wms.http.*;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;


public class ChannelService extends HTTPProvider2Base {

		
	public void onHTTPRequest(IVHost vhost, IHTTPRequest req, IHTTPResponse resp)
    {
		WMSLogger logger = WMSLoggerFactory.getLogger(null);
		if (!doHTTPAuthentication(vhost, req, resp))
			return;
		if (req.getMethod().equals("POST")) 
			req.parseBodyForParams();

		Map<String, List<String>> params = req.getParameterMap();
		logger.info("Request params");
		Set<String> key = params.keySet();
		for (Iterator it = key.iterator(); it.hasNext();) {
			String s = (String)it.next();
			logger.info(" >> " + s + ":=" + params.get(s).get(0));
		}
				
		String action = "";

		if (params.containsKey("action"))
			action = params.get("action").get(0);

		String retStr = "Hello World!";
		String[] br = {"1","3"}; 
		

		try {
			if (action.equals("create-stream")) {
				retStr = createStream(params,br);
			} else if (action.equals("start-stream")) {
				retStr = startStream(params,br);
			} else if (action.equals("stop-stream")) {
				retStr = stopStream(params,br);
			} else if (action.equals("delete-stream")) {
				retStr = deleteStream(params,br);
			} else if (action.equals("update-playlist")) {
				retStr = updatePlaylist(params,br);
			}

			if (params.containsKey("callback")) {
				retStr = params.get("callback").get(0) + "(" + retStr + ");";
			}

		} catch (Exception e) {
			retStr = e.getMessage();
		}
		

		try {
			OutputStream out = resp.getOutputStream();
			byte[] outBytes = retStr.getBytes();
			out.write(outBytes);
		} catch (Exception e) {
			WMSLoggerFactory.getLogger(null).error(
					"ChannelService: " + e.toString());
		}

    }

	/**
	 * 
	 * @param params
	 * @return 
	 * 0 - Stream Created!
	 * 1 - Please choose channel!
	 * 2 - ID can not be blank!
	 * 3 - Title can not be blank!
	 * 4 - Default config file can not be blank!
	 * 5 - ID has existed!
	 * @throws Exception
	 */
	private String createStream(Map<String,List<String>> params,String[] br) throws Exception{
		if(! params.containsKey("id")) throw new Exception("Please choose channel!");
		
		String id = params.get("id").get(0);
		String title = params.get("title").get(0);
		String default_file = params.get("default_file").get(0);
		String de_d_file = URLDecoder.decode(default_file);
		WMSLoggerFactory.getLogger(null).info("Decode playlist = " + de_d_file);
		
		for(int i=0; i< br.length; i++){
			String c_id = id + "_" + br[i];
			ChannelAdmin.addChannel(c_id, title, de_d_file);
//			ChannelAdmin.addChannel(id, title, de_d_file);
		}
		
		
		return "Stream Created!";
	}
	
	private String startStream(Map<String,List<String>> params, String[] br) throws Exception{
		if(! params.containsKey("id")) throw new Exception("Please choose channel!");
		String id = params.get("id").get(0);
		
		String playlist = "";
//		if (params.containsKey("playlist")) 
//			playlist = params.get("playlist").get(0);
		
		String result[] = {"",""};
		
		for(int i=0;i< br.length; i++){
			String c_id = id + "_" + br[i];
			String pl_id = "playlist" + "_" + br[i];
			if (params.containsKey(pl_id)) 
				playlist = params.get(pl_id).get(0);
			Channel c = ChannelAdmin.getChannel(c_id);
			result[i] = c.start_with_playlist(playlist);
		}
		
		if (result[0].equals("success") && result[1].equals("success")) {
			return "Stream Started!";
		} else {
			return result[0] + "-" + result[1];
		}
		
	}
	
	private String stopStream(Map<String,List<String>> params,String[] br) throws Exception{
		if(! params.containsKey("id")) throw new Exception("Please choose channel!");
		String id = params.get("id").get(0);
		
		for(int i=0;i<br.length;i++){
			String c_id = id + "_" + br[i];
			Channel c = ChannelAdmin.getChannel(c_id);
			c.stop();
		}
		
		return "Stream Stopped!";
	}
	
	private String deleteStream(Map<String,List<String>> params, String[] br) throws Exception{
		if(! params.containsKey("id")) throw new Exception("Please choose channel!");
		
		String id = params.get("id").get(0);
	
		for(int i=0;i<br.length;i++){
			String c_id = id + "_" + br[i];
			ChannelAdmin.deleteChannel(c_id);
		}
		
		return "Stream Deleted!";
	}
	
	private String updatePlaylist(Map<String,List<String>> params,String[] br) throws Exception{
		ReturnData rd = new ReturnData();
    	rd.result = 1;
		try {
    		if(! params.containsKey("id")) throw new Exception("Please choose channel!");
    		
			String id = params.get("id").get(0);		
			for(int i=0;i<br.length;i++){
				String c_id = id + "_" + br[i];
				Channel c = ChannelAdmin.getChannel(c_id);			
				c.updatePlaylist();
			}
			rd.message = "Playlist Updated!";
    	} catch(Exception e) {			
			rd.result = 0;
    		rd.message = e.getMessage();
		}
		
    	Gson gson = new Gson();
		return gson.toJson(rd);
	}
	
	class ReturnData {
    	public Integer result;
    	public String message;
    }
	
}