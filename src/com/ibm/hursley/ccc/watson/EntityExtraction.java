package com.ibm.hursley.ccc.watson;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;


public class EntityExtraction extends WatsonServices implements Serializable{

	private static final long serialVersionUID = 3245898244343952135L;
	private static final Logger logger = Logger.getLogger(EntityExtraction.class);
	private JSONArray serializableEntityTaggers = null;

	private boolean simulation = false;
	private double minimumConfidence = 0;
	private ArrayList<String> excludedEntities = null;
	
	private static HashSet<String> ALLOWED_TYPES = new HashSet<>();

	public EntityExtraction(JSONArray serializableEntityTaggers, double minimumConfidence, boolean simulation, ArrayList<String> excludedEntities){
		this.serializableEntityTaggers = serializableEntityTaggers;
		this.simulation = simulation;
		this.minimumConfidence = minimumConfidence;
		this.excludedEntities = excludedEntities;
		
		// these are in addition to disambiguated names
		ALLOWED_TYPES.add("geographicfeature");
		ALLOWED_TYPES.add("organization");
		ALLOWED_TYPES.add("sport");
		ALLOWED_TYPES.add("company");
		ALLOWED_TYPES.add("city");
		ALLOWED_TYPES.add("facility");
		ALLOWED_TYPES.add("quantity");
		
		logger.log(Level.INFO, "Entity extraction minimum confidence: " + this.minimumConfidence);
	}
	
	private boolean exclude(String topic){
		if(this.excludedEntities != null && this.excludedEntities.size() > 0){
			Iterator<String> i = this.excludedEntities.iterator();
			while(i.hasNext()){
				if(i.next().equalsIgnoreCase(topic.trim())){
					return true;
				}
			}
		}
		return false;
	}

	public JSONArray tag(String text){
		JSONArray resultEntities = new JSONArray();

		if(simulation){
			try{
				double random = Math.random();
				
				if(random > 0.8){
					JSONObject entity = new JSONObject();
					entity.put("name","Alan Shearer");
					entity.put("id", WatsonServices.generateId("Alan Shearer"));
					resultEntities.put(entity);
				}
				else if(random > 0.6){
					JSONObject entity = new JSONObject();
					entity.put("name","Taylor Swift");
					entity.put("id", WatsonServices.generateId("Taylor Swift"));
					resultEntities.put(entity);
				}
				else if(random > 0.4){
					JSONObject entity = new JSONObject();
					entity.put("name","Kanye West");
					entity.put("id", WatsonServices.generateId("Kanye West"));
					resultEntities.put(entity);
				}
				else if(random > 0.35){
					JSONObject entity = new JSONObject();
					entity.put("name","Borris Becker");
					entity.put("id", WatsonServices.generateId("Borris Becker"));
					resultEntities.put(entity);
				}
				else if(random > 0.30){
					JSONObject entity = new JSONObject();
					entity.put("name","Sunshine");
					entity.put("id", WatsonServices.generateId("Sunshine"));
					resultEntities.put(entity);
				}
				else{
					JSONObject entity = new JSONObject();
					entity.put("name","Nick Faldo");
					entity.put("id", WatsonServices.generateId("Nick Faldo"));
					resultEntities.put(entity);
				}
			}
			catch(Exception e){
				e.printStackTrace();
			}
			return resultEntities;
		}
		
		JSONArray taggers = null;
		if(this.serializableEntityTaggers != null){
			taggers = this.serializableEntityTaggers;
		}
		
		if(taggers == null || taggers.length() < 1){
			logger.log(Level.ERROR, "concept taggers json not defined");
			return null;
		}
		
		CloseableHttpClient httpclient = HttpClients.custom()
				.setConnectionManager(WatsonServices.getConnectionManager())
				.setConnectionManagerShared(true)
				.build();
		
	    try {
	    	int scorerIndex = (int) Math.floor(Math.random()*taggers.length());
			JSONObject tagger = taggers.getJSONObject(scorerIndex);
			
	    	URIBuilder builder = new URIBuilder();
	    	builder
	    		.setScheme("http")
	    		.setHost(tagger.getString("host"))
	            .setPort(80)
	            .setPath(tagger.getString("path"))
	            .setParameter("apikey", tagger.getString("apikey"))
	            .setParameter("outputMode", "json")
	            .setParameter("maxRetrieve", "1")
	            .setParameter("showSourceText", "1")
	            .setParameter("text", text);
	    	
	   
	    	HttpGet httpget = new HttpGet(builder.build());
	    	HttpClientContext context =  HttpClientContext.create();
	            
	    	// Create a custom response handler
	    	ResponseHandler<String> responseHandler = new ResponseHandler<String>() {
	    		@Override
	    		public String handleResponse(final HttpResponse response) throws ClientProtocolException, IOException {
	    			int status = response.getStatusLine().getStatusCode();
	    			if (status >= 200 && status < 300) {
	    				HttpEntity entity = response.getEntity();
	    				String returnContent = entity != null ? EntityUtils.toString(entity) : null;
	    				EntityUtils.consume(entity);
	    				return returnContent;
	    			} 
	    			else {
	    				logger.log(Level.ERROR, EntityUtils.toString(response.getEntity()));
	    				EntityUtils.consume(response.getEntity());
	    				throw new ClientProtocolException("Unexpected response status: " + status);
	    			}
	    		}
	    	};
	            
	        String responseBody = httpclient.execute(httpget, responseHandler, context);

	        if(responseBody != null && responseBody.length() > 0){
	        	JSONObject responseJson = new JSONObject(responseBody);
	        	if(responseJson != null && responseJson.has("status") && responseJson.getString("status").equalsIgnoreCase("OK")){
	        		if(responseJson.has("entities") && responseJson.getJSONArray("entities").length() > 0){
	        			JSONArray entities = responseJson.getJSONArray("entities");
	        			if(entities != null && entities.length() > 0){
	        				JSONObject entity = entities.getJSONObject(0);
	        				if(entity != null && entity.has("type") && entity.getString("type").equalsIgnoreCase("Person")){
	        					if(entity.has("disambiguated")){
	        						JSONObject disambiguated = entity.getJSONObject("disambiguated");
	        						if(disambiguated != null && disambiguated.has("name")){
	        							JSONObject resultEntity = new JSONObject();
	        							resultEntity.put("name",disambiguated.getString("name"));
	        							resultEntity.put("id", WatsonServices.generateId(disambiguated.getString("name")));
	        							resultEntities.put(resultEntity);
	        						}
	        					}
	        				}
	        				else if(ALLOWED_TYPES.contains(entity.getString("type").toLowerCase())){
	        					logger.log(Level.INFO, "Topic:"+ entity.getString("text") + " allowed in category: "+entity.getString("type"));
	        					JSONObject resultEntity = new JSONObject();
    							resultEntity.put("name", entity.getString("text"));
    							resultEntity.put("id", WatsonServices.generateId(entity.getString("text")));
    							resultEntities.put(resultEntity);
	        				}
	        			}
	        		}
	        	}
	        }
	    }
	    catch(Exception e){
	    	e.printStackTrace();
	    }
	    finally{
			try {
				httpclient.close();
			} 
			catch (IOException e) {
				e.printStackTrace();
			}
		}
	
		return resultEntities;
	}

}
