package com.gloo;

import java.net.URL;

import com.wowza.wms.amf.*;
import com.wowza.wms.application.*;
import com.wowza.wms.client.*;
import com.wowza.wms.module.*;
import com.wowza.wms.request.*;
import com.wowza.wms.server.Server;

public class Live extends ModuleBase {

	public void doSomething(IClient client, RequestFunction function,
			AMFDataList params) {
		getLogger().info("doSomething");
		sendResult(client, params, "Hello Wowza");
	}

	public void onAppStart(IApplicationInstance appInstance) {
//		String fullname = appInstance.getApplication().getName() + "/"
//				+ appInstance.getName();
//		getLogger().info("onAppStart: " + fullname);
//		
//		String restartUrl = Server.getInstance().getProperties().getPropertyStr("GlooChannelRestartUrl", "http://www.gloo.tv/api/wowzas/restart");
//        
//        
//		getLogger().info("Restart all channel url := " + restartUrl);
//        
//		try {
//			URL url = new URL(restartUrl);
//			url.openStream();
//		} catch (Exception e1) {
//			// TODO Auto-generated catch block
//		}
	}

	public void onAppStop(IApplicationInstance appInstance) {
//		String fullname = appInstance.getApplication().getName() + "/"
//				+ appInstance.getName();
//		getLogger().info("onAppStop: " + fullname);
	}

}
