package com.ibm.hursley.ccc.watson;

import java.io.Serializable;

import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;


public class WatsonServices implements Serializable {
	
	private static final long serialVersionUID = -5034651457164601147L;
	private static PoolingHttpClientConnectionManager connetionManager = null;
	private static final int MAX_CONNECTIONS = 200;

	public static String generateId(String text){
		String id = text.replaceAll("[\u0000-\u001f]", "");
		id = id.replaceAll(" ", "");
		id = id.replaceAll("\"", "");
		id = id.replaceAll("\\,", "");
		id = id.replaceAll("\\\\", "");
		id = id.replaceAll("-", "");
		id = id.toLowerCase();
		return id;
	}
	
	
	public static PoolingHttpClientConnectionManager getConnectionManager(){
		if(WatsonServices.connetionManager == null){
			WatsonServices.connetionManager = new PoolingHttpClientConnectionManager();
			// Increase max total connection to 200
			WatsonServices.connetionManager .setMaxTotal(WatsonServices.MAX_CONNECTIONS);
			// Increase default max connection per route to 20
			WatsonServices.connetionManager .setDefaultMaxPerRoute(WatsonServices.MAX_CONNECTIONS);
			WatsonServices.connetionManager.setValidateAfterInactivity(4000);
		}
		return WatsonServices.connetionManager;
	}
}
