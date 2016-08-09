package com.ibm.hursley.ccc.watson;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.client.StandardHttpRequestRetryHandler;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class AlchemyCombined extends WatsonServices implements Serializable{

	private static final long serialVersionUID = -9145311254690597965L;
	private static final Logger logger = Logger.getLogger(AlchemyCombined.class);
	private JSONArray combinedTaggers = null;
	private HashSet<String> includedTopics = null;
	private static HashSet<String> ALLOWED_TYPES = new HashSet<>();

	public AlchemyCombined(JSONArray combinedTaggers, ArrayList<String> excludedTopics, ArrayList<String> excludedEntities){
		this.combinedTaggers = combinedTaggers;
		
		// these are in addition to disambiguated names
		ALLOWED_TYPES.add("geographicfeature");
		ALLOWED_TYPES.add("organization");
		ALLOWED_TYPES.add("sport");
		ALLOWED_TYPES.add("company");
		ALLOWED_TYPES.add("city");
		ALLOWED_TYPES.add("facility");
				
		this.includedTopics = new HashSet<>();
		if(combinedTaggers != null){
			if(combinedTaggers.length() > 0){
				try {
					JSONObject tagger = combinedTaggers.getJSONObject(0);
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
	
	
	public JSONObject tag(String text){
		JSONObject results = new JSONObject();
		try{
			results.put("entities", new JSONArray());
			results.put("concepts", new JSONArray());
			results.put("sentiment", 0);
			
			if(combinedTaggers == null || combinedTaggers.length() < 1){
				logger.log(Level.ERROR, "combined taggers json not defined");
				return null;
			}
			
			CloseableHttpClient httpclient = HttpClients.custom()
					.setConnectionManager(WatsonServices.getConnectionManager())
					.setConnectionManagerShared(true)
					.setRetryHandler(new StandardHttpRequestRetryHandler(1,true))
					.build();
			
			//.getParams().setParameter("http.connection.stalecheck", false)
			
			int taggerIndex = (int) Math.floor(Math.random()*combinedTaggers.length());
			JSONObject tagger = combinedTaggers.getJSONObject(taggerIndex);
			
			URIBuilder builder = new URIBuilder();
	    	builder
	    		.setScheme("http")
	    		.setHost(tagger.getString("host"))
	            .setPort(80)
	            .setPath("/calls/text/TextGetCombinedData")
	            .setParameter("apikey", tagger.getString("apikey"))
	            .setParameter("outputMode", "json")
	            .setParameter("extract", "entity,concept,doc-sentiment")
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
	        	if(responseJson != null){
	        		if(responseJson.has("docSentiment") && responseJson.getJSONObject("docSentiment").has("score")){
	        			String scoreString = responseJson.getJSONObject("docSentiment").getString("score");
	        			if(scoreString != null && scoreString.length() > 0){
	        				double score = Double.parseDouble(scoreString);
	        				results.put("sentiment", score);
	        			}
	        		}
	        		
	        		if(responseJson.has("entities")){
	        			JSONArray entities = responseJson.getJSONArray("entities");
	        			if(entities != null && entities.length() > 0){
	        				for(int i=0; i <entities.length(); i++){
	        					JSONObject entity = entities.getJSONObject(i);
		        				if(entity != null && entity.has("type") && entity.getString("type").equalsIgnoreCase("Person")){
		        					if(entity.has("disambiguated")){
		        						JSONObject disambiguated = entity.getJSONObject("disambiguated");
		        						if(disambiguated != null && disambiguated.has("name")){
		        							JSONObject resultEntity = new JSONObject();
		        							resultEntity.put("name",disambiguated.getString("name"));
		        							resultEntity.put("id", WatsonServices.generateId(disambiguated.getString("name")));
		        							results.getJSONArray("entities").put(resultEntity);
		        						}
		        					}
		        				}
		        				else if(ALLOWED_TYPES.contains(entity.getString("type").toLowerCase())){
		        					logger.log(Level.INFO, "Topic:"+ entity.getString("text") + " allowed in category: "+entity.getString("type"));
		        					JSONObject resultEntity = new JSONObject();
	    							resultEntity.put("name", entity.getString("text"));
	    							resultEntity.put("id", WatsonServices.generateId(entity.getString("text")));
	    							results.getJSONArray("entities").put(resultEntity);
		        				}
	        				}
	        			}
	        		}
	        		
	        		if(responseJson.has("concepts")){
	        			JSONArray concepts = responseJson.getJSONArray("concepts");
		        		for(int i=0; i < concepts.length(); i++){
		        			JSONObject concept = concepts.getJSONObject(i);
		        			String topicText = concept.getString("text");
		        			if(this.include(topicText)){
		        				JSONObject topic = new JSONObject();
		        				topic.put("name", topicText);
		        				topic.put("id", WatsonServices.generateId(topicText));
		        				results.getJSONArray("concepts").put(topic);
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

		return results;
	}
	
}
