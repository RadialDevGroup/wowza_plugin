package com.gloo;

import java.io.OutputStream;

import java.util.*;

import com.wowza.wms.application.IApplicationInstance;
import com.wowza.wms.dvr.*;
import com.wowza.wms.dvr.io.IDvrFileSystem;
import com.wowza.wms.http.*;
import com.wowza.wms.logging.WMSLogger;
import com.wowza.wms.logging.WMSLoggerFactory;
import com.wowza.wms.stream.IMediaStream;
import com.wowza.wms.stream.livedvr.*;
import com.wowza.wms.stream.mediacaster.MediaStreamMediaCasterUtils;
import com.wowza.wms.vhost.*;


public class HTTPDvrStreamRecord extends HTTProvider2Base {
    private static final String CLASSNAME = "HTTPDvrStreamRecord";
    

    private Map<String, ILiveStreamDvrRecorder> dvrRecorders = new HashMap<String, ILiveStreamDvrRecorder>();

    public void onHTTPRequest(IVHost vhost, IHTTPRequest req, IHTTPResponse resp) {
    	
    	WMSLogger logger = WMSLoggerFactory.getLogger(null);
    	
    	logger.info(CLASSNAME + " ------- HTTPRequest before authentication !");
    	
        if (!doHTTPAuthentication(vhost, req, resp)) {
            return;
        }

        logger.info(CLASSNAME + " HTTPRequest");

        Map<String, List<String>> params = req.getParameterMap();

        String action = "";
        String app = "";
        String streamName = "";
        String report = "";
        String recordingName = "";

        if (req.getMethod().equalsIgnoreCase("get") || req.getMethod().equalsIgnoreCase("post")) {
            req.parseBodyForParams(true);

            try {

                if (params.containsKey("action")) {
                    action = params.get("action").get(0);
                } else {
                    report += "<BR>" + "action" + " is required";
                }
                logger.info(CLASSNAME + " action: " + action);

                if (params.containsKey("app")) {
                    app = params.get("app").get(0);
                } else {
                    report += "<BR>" + "app" + " is required";
                }
                logger.info(CLASSNAME + " app: " + app);

                if (params.containsKey("streamname")) {
                    streamName = params.get("streamname").get(0);
                    recordingName = streamName; // default to stream name
                } else {
                    report += "<BR>" + "streamname" + " is required";
                }
                logger.info(CLASSNAME + " streamName: " + streamName);

                // If recordingName is specified, use it instead
                if (params.containsKey("recordingname")) {
                    recordingName = params.get("recordingname").get(0);
                    logger.info(CLASSNAME + " recordingName: " + recordingName);
                }
                
            } catch (Exception ex) {
                report = "Error: " + ex.getMessage();
            }
        } else {
            report = "Nothing to do.";
        }

        try {
            IApplicationInstance appInstance = vhost.getApplication(app).getAppInstance("_definst_");

            if (!appInstance.getPublishStreamNames().contains(streamName)) {
                report = "Live stream " + streamName + " does not exist.";
            }

            if (action.equalsIgnoreCase("start") && report.equalsIgnoreCase("")) {
            	logger.info(String.format("%s.%s: %s", CLASSNAME, "start", streamName));

                String streamTypeStr = appInstance.getStreamType();

                boolean isLiveRepeaterEdge = false;
                while (true) {
                    StreamList streamDefs = appInstance.getVHost().getStreamTypes();
                    StreamItem streamDef = streamDefs.getStreamDef(streamTypeStr);
                    if (streamDef == null)
                        break;
                    isLiveRepeaterEdge = streamDef.getProperties().getPropertyBoolean("isLiveRepeaterEdge",
                            isLiveRepeaterEdge);
                    break;
                }

                if (isLiveRepeaterEdge)
                    streamName = MediaStreamMediaCasterUtils.mapMediaCasterName(appInstance, null, streamName);

                IMediaStream stream = appInstance.getStreams().getStream(streamName);
                if (stream != null) {
                    startRecording(stream, recordingName);
                    report = action + " " + streamName + " as " + recordingName;
                } else {
                	logger.warn(String.format("%s.%s: stream '%s' not found.", CLASSNAME, "start", streamName));
                    report = "Stream Not Found: " + streamName;
                }

            } else if (action.equalsIgnoreCase("stop") & report.equalsIgnoreCase("")) {
            	logger.info(String.format("%s.%s: %s", CLASSNAME, "stop", streamName));

                String path = stopRecording(streamName);
                report = action + " " + streamName + " " + path;
            }

        } catch (Exception e) {
            report = "Error: " + e.getMessage();
        }

        String retStr = "<html><head><title>HTTPProvider DvrStreamRecord</title></head><body><h1>" + report + "</h1></body></html>";

        try {
            OutputStream out = resp.getOutputStream();
            byte[] outBytes = retStr.getBytes();
            out.write(outBytes);
        } catch (Exception e) {
        	logger.error(CLASSNAME + ": " + e.toString());
        }

    }

    public void startRecording(IMediaStream stream, String recordingName) {

        String streamName = stream.getName();

        // add it to the recorders list
        synchronized (dvrRecorders) {
            // Stop previous recorder
            ILiveStreamDvrRecorder prevRecorder = dvrRecorders.get(streamName);
            if (prevRecorder != null && prevRecorder.isRecording()) {
                prevRecorder.stopRecording();
            }

            // get the stream's DVR recorder and save it in a map of recorders
            ILiveStreamDvrRecorder dvrRecorder = stream.getDvrRecorder(IDvrConstants.DVR_DEFAULT_RECORDER_ID);

            if (dvrRecorder != null) {

                if (dvrRecorder.isRecording()) {
                    dvrRecorder.stopRecording();
                }

                // start recording
                dvrRecorder.setRecordingName(recordingName);
                dvrRecorder.startRecording();

                dvrRecorders.put(streamName, dvrRecorder);

            } else {
                WMSLoggerFactory.getLogger(null).warn(String.format("%s.%s: DVR Recorder not found for stream '%s'.", CLASSNAME, "start", streamName));
            }
        }
    }


    public String stopRecording(String streamName) {
        String path = "";
        ILiveStreamDvrRecorder dvrRecorder = null;
        synchronized (dvrRecorders) {
            dvrRecorder = dvrRecorders.remove(streamName);
        }

        if (dvrRecorder != null) {
            IDvrStreamManager dvrManager = dvrRecorder.getDvrManager();
            if (dvrManager != null) {
                IDvrStreamStore store = dvrManager.getRecordingStreamStore();
                IDvrFileSystem fs = store.getFileSystem();
                path = fs.getBasePath();
            }

            // stop recording
            dvrRecorder.stopRecording();

            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        } else {
            WMSLoggerFactory.getLogger(null).warn(String.format("%s.%s: DVR Manager not found for stream '%s'.", CLASSNAME, "stop", streamName));
        }

        return path;
    }
}