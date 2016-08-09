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
import org.json.JSONException;
import org.json.JSONObject;


public class ConceptTagger extends WatsonServices implements Serializable{

	private static final long serialVersionUID = 3245898244343952135L;
	private static final Logger logger = Logger.getLogger(ConceptTagger.class);
	private JSONArray serializableConceptTaggers = null;

	private boolean simulation = false;
	private double minimumConfidence = 0;
	private HashSet<String> includedTopics = null;

	public ConceptTagger(JSONArray serializableConceptTaggers, double minimumConfidence, boolean simulation, ArrayList<String> excludedTopics){
		this.serializableConceptTaggers = serializableConceptTaggers;
		this.simulation = simulation;
		this.includedTopics = new HashSet<>();
		this.init();
	}

	private void init(){
		JSONArray taggers = null;
		if(this.serializableConceptTaggers != null){
			taggers = this.serializableConceptTaggers;
			if(taggers.length() > 0){
				try {
					JSONObject tagger = taggers.getJSONObject(0);
					if(tagger.has("include")){
						JSONArray includes = tagger.getJSONArray("include");
						for(int i=0; i < includes.length(); i++){
							JSONObject include = includes.getJSONObject(i);
							if(include != null){
								if(include.has("match")){
									this.includedTopics.add(include.getString("match").trim().toLowerCase());
								}
							}
						}
					}
				} 
				catch (JSONException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	private boolean include(String topic){
		if(topic != null && topic.length() > 0){
			return this.includedTopics.contains(topic.toLowerCase().trim());
		}
		return false;
	}

	public JSONArray tag(String text){
		JSONArray resultTopics = new JSONArray();

		if(simulation){
			double random = Math.random();
			try{
				if(random > 0.8){
					JSONObject topic = new JSONObject();
					topic.put("name", "One Direction at Wimbledon");
					topic.put("id", WatsonServices.generateId("One Direction at Wimbledon"));
					resultTopics.put(topic);
				}
				else if(random > 0.6){
					JSONObject topic = new JSONObject();
					topic.put("name", "Taylor Swift");
					topic.put("id", WatsonServices.generateId("Taylor Swift"));
					resultTopics.put(topic);
				}
				else if(random > 0.4){
					JSONObject topic = new JSONObject();
					topic.put("name", "Star Wars Released");
					topic.put("id", WatsonServices.generateId("Star Wars Released"));
					resultTopics.put(topic);
				}
				else if(random > 0.35){
					JSONObject topic = new JSONObject();
					topic.put("name", "Borris Becker Commentary");
					topic.put("id", WatsonServices.generateId("Borris Becker Commentary"));
					resultTopics.put(topic);
				}
				else if(random > 0.30){
					JSONObject topic = new JSONObject();
					topic.put("name", "Sunshine");
					topic.put("id", WatsonServices.generateId("Sunshine"));
					resultTopics.put(topic);
				}
				else if(random > 0.25){
					JSONObject topic = new JSONObject();
					topic.put("name", "Weather");
					topic.put("id", WatsonServices.generateId("Weather"));
					resultTopics.put(topic);
				}
				else if(random > 0.20){
					JSONObject topic = new JSONObject();
					topic.put("name", "Royal Box");
					topic.put("id", WatsonServices.generateId("Royal Box"));
					resultTopics.put(topic);
				}
				else if(random > 0.15){
					JSONObject topic = new JSONObject();
					topic.put("name", "The Ashes");
					topic.put("id", WatsonServices.generateId("The Ashes"));
					resultTopics.put(topic);
				}
				else if(random > 0.10){
					JSONObject topic = new JSONObject();
					topic.put("name", "Beyonce");
					topic.put("id", WatsonServices.generateId("Beyonce"));
					resultTopics.put(topic);
				}
				else if(random > 0.5){
					JSONObject topic = new JSONObject();
					topic.put("name", "Borris Becker Commentary");
					topic.put("id", WatsonServices.generateId("Borris Becker Commentary"));
					resultTopics.put(topic);
				}
				else{
					JSONObject topic = new JSONObject();
					topic.put("name", "Lunchtime and food");
					topic.put("id", WatsonServices.generateId("Lunchtime and food"));
					resultTopics.put(topic);
				}
			}
			catch(Exception e){
				e.printStackTrace();
			}
			return resultTopics;
		}
		
		JSONArray taggers = null;
		if(this.serializableConceptTaggers != null){
			taggers = this.serializableConceptTaggers;
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
	            .setParameter("maxRetrieve", "4")
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
	        		if(responseJson.has("concepts")){
	        			JSONArray concepts = responseJson.getJSONArray("concepts");
	        			for(int i=0; i < concepts.length(); i++){
	        				JSONObject concept = concepts.getJSONObject(i);
	        				String topicText = concept.getString("text");
	        				if(this.include(topicText)){
	        					JSONObject topic = new JSONObject();
	        					topic.put("name", topicText);
	        					topic.put("id", WatsonServices.generateId(topicText));
	        					resultTopics.put(topic);
	        				}
	        				else{
	        					logger.log(Level.INFO, "Excluding topic: " + topicText + ":"+concept.getDouble("relevance"));
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
	
		return resultTopics;
	}

}
