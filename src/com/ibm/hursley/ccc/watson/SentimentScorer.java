package com.ibm.hursley.ccc.watson;

import java.io.IOException;
import java.io.Serializable;

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


public class SentimentScorer extends WatsonServices implements Serializable{

	private static final long serialVersionUID = 2027713411823662285L;
	private static final Logger logger = Logger.getLogger(SentimentScorer.class);
	private JSONArray serializableScorers = null;

	private boolean simulation = false;

	public SentimentScorer(JSONArray serializableScorers, boolean simulation){
		this.serializableScorers = serializableScorers;
		this.simulation = simulation;
	}

	public double score(String text){
		double score = 0;
		if(simulation){
			if(Math.random() > 0.3){
				return 1;
			}
			else{
				return -1;
			}
		}
		
		JSONArray scorers = null;
		if(this.serializableScorers != null){
			scorers = this.serializableScorers;
		}
		
		if(scorers == null || scorers.length() < 1){
			logger.log(Level.ERROR, "classifiers json for nl classifier not defined");
			return 0;
		}
		
	
		CloseableHttpClient httpclient = HttpClients.custom()
				.setConnectionManager(WatsonServices.getConnectionManager())
				.setConnectionManagerShared(true)
				.build();
		
	    try {
	    	int scorerIndex = (int) Math.floor(Math.random()*scorers.length());
			JSONObject scorer = scorers.getJSONObject(scorerIndex);
			
	    	URIBuilder builder = new URIBuilder();
	    	builder
	    		.setScheme("http")
	    		.setHost(scorer.getString("host"))
	            .setPort(80)
	            .setPath(scorer.getString("path"))
	            .setParameter("apikey", scorer.getString("apikey"))
	            .setParameter("outputMode", "json")
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
	        	JSONObject scorerResponse = new JSONObject(responseBody);
	        	if(scorerResponse != null){
	        		if(scorerResponse.has("docSentiment")){
	        			JSONObject docSentiment = scorerResponse.getJSONObject("docSentiment");
	        			if(docSentiment.has("score")){
	        				score = Double.parseDouble(docSentiment.getString("score"));
	        			}
	        		}
	        		else if(scorerResponse.has("statusInfo") && scorerResponse.getString("statusInfo").equalsIgnoreCase("unsupported-text-language")){
	        			// ignore this
	        		}
	        		else{
	        			logger.log(Level.ERROR, scorerResponse.toString(1));
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
	    
		return score;
	}

}
