package com.gloo;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import com.google.gson.Gson;
import com.wowza.wms.application.IApplication;
import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.server.Server;
import com.wowza.wms.stream.publish.IStreamActionNotify;
import com.wowza.wms.stream.publish.PlaylistItem;
import com.wowza.wms.stream.publish.Stream;
import com.wowza.wms.vhost.IVHost;
import com.wowza.wms.vhost.VHostSingleton;

public class Channel {
    private String id;
    private String title;
    private String defaultFile;
    private PLAY_STATUS status;  //status
    private IApplicationInstance appInst;
    private Stream stream;
    private ArrayList<Program> programs;
    private Date lastStart;
    
	public enum PLAY_STATUS { STOPPED, PLAYING };
    private WMSLogger logger = WMSLoggerFactory.getLogger(null);
    
    /**
     * Constructor
     */
	public Channel(String id, String title, String defaultFile) {
		this.id = id;
		this.title = title;
		this.defaultFile = defaultFile;
		
		status = PLAY_STATUS.STOPPED;
		programs = new ArrayList<Program>();
	}
	
	public String getId() {
		return id;
	}

	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	
	public String getDefaultFile() {
		return defaultFile;
	}
	public void setDefaultFile(String defaultFile) {
		this.defaultFile = defaultFile;
	}
	
	/**
	 * Get programs
	 * @return
	 */
	public ArrayList<Program> getPlaylist() {
		return programs;
	}
	
	/**
	 * Get current program index
	 */
	private int getCurrentIndex() {
		return stream.getCurrentItem().getIndex();
	}
	
	/**
	 * Get current program, if not return null
	 * @return
	 */
	public Program getCurrentProgram() {
		if(status == PLAY_STATUS.STOPPED) return null;
		
		programs.get(getCurrentIndex()).progress = (Calendar.getInstance().getTime().getTime() - lastStart.getTime()) / 1000;
		return programs.get(getCurrentIndex());
	}
	
	public PLAY_STATUS getStatus() {
		return status;
	}
	
	public String getStatusStr() {
		String result = "";
		
		switch(status) {
		    case STOPPED:
		    	result = "Stopped";
		    	break;
		    case PLAYING:
		    	result = "Play...";
		    	break;
		}
		
		return result;
	}
	
	
	public String start_with_playlist(String playlist) throws Exception{
		
		if(status == PLAY_STATUS.PLAYING) return "success";
		
		valid_server();
        
		// Get playlist, if no, will play default file 
		
		String de_playlist = URLDecoder.decode(playlist);
		logger.info("Decode playlist = " + de_playlist);

        Gson gson = new Gson();
        PlaylistRequest pr = gson.fromJson(de_playlist, PlaylistRequest.class);
        
        for(Program p : pr.playlist) {
        	if(p.path != null && (new File(appInst.getStreamStorageDir() + '/' + p.path)).exists()
                	&& p.start != null && p.length != null) {
                programs.add(p);
            } else {
                logger.info("FileError: " + p.date + " - " + p.no + " - " + p.start + " - " + p.length + " - " + p.title + " - " + p.path);
            }       	
        }
        
    	if(programs.size() == 0) {
    		String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
    		programs.add(new Program(today, 0, "DEFAULT FILE", defaultFile, 0, -1, null));
    	}   

		try {
			status = PLAY_STATUS.PLAYING;
	        stream = Stream.createInstance(appInst, id);
	        stream.setSendOnMetadata(true);
	        IStreamActionNotify actionNotify  = new StreamListener(appInst);
	        stream.addListener(actionNotify);
	        
	        boolean newPlaylist = true;
	        for(Program p : programs) {
	        	stream.play("mp4:" + p.path, p.start, p.length, newPlaylist);
	        	newPlaylist = false;
	        }
	        
	        stream.setRepeat(true);
//	        stream.setSendOnMetadata(true);
	        
	        return "success";
		} catch(Exception e) {
			return e.getMessage();
		}
        
	}
	
	/*
	 * valid 
	 * 	- if host and application are ready 
	 *  - if channel default file exist
	 */
	public void valid_server() throws Exception{
		
		if(appInst == null) {
	        IVHost vhost = VHostSingleton.getInstance(Server.getInstance().getProperties().getPropertyStr("ChannelVhost", "_defaultVHost_"));
	        IApplication app = vhost.getApplication(Server.getInstance().getProperties().getPropertyStr("ChannelApp", "live"));
	        appInst = app.getAppInstance("_definst_");
	        
	        if ( vhost == null ) throw new Exception("Can not get vhost.");
	        if ( app == null || appInst == null ) throw new Exception("Can not get application.");
		}
		
		logger.info("StreamStoreDir = " + appInst.getStreamStorageDir());

        if(!(new File(appInst.getStreamStorageDir() + '/' + defaultFile)).exists())  throw new Exception("Default file not exist.");

	}
	
	
	/**
	 * start stream for this application
	 * 	throws Exception
	 */
	
	public void start() throws Exception{
		if(status == PLAY_STATUS.PLAYING) return;
		
		if(appInst == null) {
	        IVHost vhost = VHostSingleton.getInstance(Server.getInstance().getProperties().getPropertyStr("ChannelVhost", "_defaultVHost_"));
	        IApplication app = vhost.getApplication(Server.getInstance().getProperties().getPropertyStr("ChannelApp", "live"));
	        appInst = app.getAppInstance("_definst_");
	        
	        if ( vhost == null ) throw new Exception("Can not get vhost.");
	        if ( app == null || appInst == null ) throw new Exception("Can not get application.");
		}

        if(!(new File(appInst.getStreamStorageDir() + '/' + defaultFile)).exists())  throw new Exception("Default file not exist.");
        
        String today = new SimpleDateFormat("yyyyMMdd").format(new Date());
        
        // Get playlist, if no, will play default file   	
    	try {
    		programs = fetchPlaylist(today, -1);
    	} catch(Exception e) {
    		logger.info("Get Playlist for Rails Error: " + e.getMessage());
    	} finally {
        	if(programs.size() == 0) {
        		programs.add(new Program(today, 0, "DEFAULT FILE", defaultFile, 0, -1, null));
        	}
    	}
		
        status = PLAY_STATUS.PLAYING;
        stream = Stream.createInstance(appInst, id);
        stream.setSendOnMetadata(true);
        IStreamActionNotify actionNotify  = new StreamListener(appInst);
        stream.addListener(actionNotify);
        
        boolean newPlaylist = true;
        for(Program p : programs) {
        	stream.play("mp4:" + p.path, p.start, p.length, newPlaylist);
        	newPlaylist = false;
        }
        
        stream.setRepeat(true);
	}
	
	/**
	 * Stop stream for this application
	 * @throws Exception
	 */
	public void stop() {
		if(status == PLAY_STATUS.STOPPED) return;
		
		status = PLAY_STATUS.STOPPED;
		stream.close();
		stream = null;
		programs.clear();
		
		appInst.getStreams().clearStreamName(id);  
	}
	
	
		
	
	/**
	 * Fetch playlist from rails server
	 * 	params[day] -- which day 
	 *  params[no] -- which no
	 *  return ArrayList of this day, after this no
	 */
	
	private ArrayList<Program> fetchPlaylist(String day, Integer no) throws Exception{
		if(appInst == null) throw new Exception("Application not iniatilize, can not get playlist!");
		
		ArrayList<Program> ps = new ArrayList<Program>();
        String plUrl = Server.getInstance().getProperties().getPropertyStr("GlooChannelPlaylistUrl", "http://www.gloo.tv/api/wowzas/playlist?stream_name=#CODE_CHANNEL_ID#&date=#CODE_DATE#&no=#CODE_NO#");
        plUrl = plUrl.replace("#FLAG#", "&").replace("#CODE_CHANNEL_ID#", id).replace("#CODE_DATE#", day).replace("#CODE_NO#", "-1");
        //System.out.println(plUrl);
        logger.info(plUrl);
        
        BufferedReader reader = null;
        
        try {
            URL url = new URL(plUrl);
            reader = new BufferedReader(new InputStreamReader(url.openStream()));

            Gson gson = new Gson();
            PlaylistRequest pr = gson.fromJson(reader, PlaylistRequest.class);
            
            for(Program p : pr.playlist) {
            	if(p.path != null && (new File(appInst.getStreamStorageDir() + '/' + p.path)).exists()
            	&& p.start != null && p.length != null) {
            		ps.add(p);
            	} else {
                	logger.info("FileError: " + p.date + " - " + p.no + " - " + p.start + " - " + p.length + " - " + p.title + " - " + p.path);
            	}
            	
            }
        } catch (Exception e) {
			throw new Exception(e.getMessage());
		} finally {
        	if(reader != null) reader.close();
        }
		
		return ps;
	}
	
	/**
	 * Update playlist when receive message from Rails
	 */
	
	public void updatePlaylist() throws Exception {
		if(status != PLAY_STATUS.PLAYING) throw new Exception("Application not iniatilize, can not get playlist!");
		
		logger.info("Recevie playlist update request");
		
		//Get current program index
		int index =  getCurrentIndex();
		
		logger.info("Playlist index = " + index);
		
		//Get playlist from rails server for new playlist
		ArrayList<Program> ps = fetchPlaylist(programs.get(index).date, -1);
		
		
		//If get playlist of this day, then update stream playlist
		if(ps.size() > 0) {
		
			logger.info("Before programs size = " + programs.size());	
			for(int i = programs.size() - 1; i > index; i--) {
				programs.remove(i);
				stream.removeFromPlaylist(i);
			}

			for(Program p : ps) {
				programs.add(p);
				stream.play("mp4:" + p.path, p.start, p.length, false);
			}
			
			logger.info("After programs size = " + programs.size());	
		}
		
		
		// Get next day's playlist and update stream playlist  not need get this at 2014.4.17 
//		SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
//		Date t = sdf.parse(programs.get(index).date);
//		Calendar cal = Calendar.getInstance();
//		cal.setTime(t);
//		cal.add(cal.DAY_OF_MONTH, 1);
//		
//		ps = fetchPlaylist(sdf.format(cal.getTime()), 0);
//		for(Program p : ps) {
//			programs.add(p);
//			stream.play("mp4:" + p.path, p.start, p.length, false);
//		}
		
	}

	
	
	
	
	/**
	 * PlaylistRequest class
	 * @author denny
	 *
	 */
	class PlaylistRequest {
		public Program[] playlist;
	}
	
	/**
	 * StreamListener class
	 *  for notifying stream change
	 * @author denny
	 *
	 */
	class StreamListener implements IStreamActionNotify {
		StreamListener(IApplicationInstance appInstance) {
			
		}
		
		public void onPlaylistItemStart(Stream stream, PlaylistItem item) {
			
			// 1. Notify rails to update current program
			lastStart = Calendar.getInstance().getTime();
			
			SimpleDateFormat bartDateFormat = new SimpleDateFormat("yyyy-MM-dd");
			String strDate = bartDateFormat.format(lastStart);
			
	        String plUrl = Server.getInstance().getProperties().getPropertyStr("GlooChannelSwitchNotifyUrl", "http://www.gloo.tv/api/wowzas/current?stream_name=#CODE_CHANNEL_ID##FLAG#date=#CODE_DATE##FLAG#no=#CODE_TITLE#");
	        
	        plUrl = plUrl.replace("#FLAG#","&").replace("#CODE_CHANNEL_ID#", id).replace("#CODE_DATE#", strDate).replace("#CODE_TITLE#", String.valueOf(programs.get(item.getIndex()).no));
            
	        logger.info("Update current program url := " + plUrl);
	        
			try {
				URL url = new URL(plUrl);
				url.openStream();
			} catch (Exception e1) {
				// TODO Auto-generated catch block
			}
			
			// 2. remove this program from playlist
			if(item.getIndex() > 0) {
				programs.remove(item.getIndex() - 1);
				stream.removeFromPlaylist(item.getIndex() - 1);
			}
			
			// 3. check if need get new playlist and do it
			try {
				if((item.getIndex() + 1) == stream.getPlaylist().size()) updatePlaylist();
			} catch (Exception e) {
				
			}
			
			System.out.println("start item " + item.getIndex() + "/" + stream.getPlaylist().size());
		}
		
		public void onPlaylistItemStop(Stream stream, PlaylistItem item) {
			//System.out.println("stop item " + item.getIndex() + "/" + stream.getPlaylist().size());
		}
		
	}
	

}
