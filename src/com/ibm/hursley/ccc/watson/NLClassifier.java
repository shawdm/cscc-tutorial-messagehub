package com.ibm.hursley.ccc.watson;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;


public class NLClassifier extends WatsonServices implements Serializable {

	private static final long serialVersionUID = 1601951258125950361L;
	private static final Logger logger = Logger.getLogger(NLClassifier.class);
	
	private JSONArray serializableClassifiers = null;
	private JSONArray keywordClassifier = null;
	private double minConfidence = 0d;
	private boolean simulation = false;
	
	public NLClassifier(JSONArray classifiers, JSONArray keywordClassifier,  double minConfidence, boolean simulation){
		if(classifiers != null){
			this.serializableClassifiers = classifiers;
		}
		
		this.keywordClassifier = keywordClassifier;
		
		if(this.keywordClassifier != null){
			System.out.println("Local keyword classifier is looking for " + this.keywordClassifier.length());
		}
		
		this.minConfidence = minConfidence;
		this.simulation = simulation;
		
		logger.log(Level.INFO,"Classifier using minumum confidence of: " + this.minConfidence);
	}

	public ArrayList<String> getClasses(String text){
		ArrayList<String> classes = new ArrayList<String>();
		
		if(simulation){
			if(Math.random() > 0.7){
				classes.add("event");
			}
			else {
				classes.add("sport");
			}
		}
		
		// try doing local classes
		if(classes.size() < 1){
			classes = this.getClassesLocal(text);
		}
		
		// try doing remote classes using service
		if(classes.size() < 1){
			classes = this.getClassesRemote(text);
		}
		
		return classes;
	}
	
	
	private ArrayList<String> getClassesLocal(String text){
		ArrayList<String> classes = new ArrayList<String>();
		text = text.toLowerCase();
		
		for(int i=0; i < this.keywordClassifier.length(); i++){
			try{
				JSONObject keywordClassifier = this.keywordClassifier.getJSONObject(i);
				if(keywordClassifier != null){
					String className = keywordClassifier.getString("class");
					JSONArray keywords = keywordClassifier.getJSONArray("keywords");
					if(keywords != null){
						for(int j=0; j < keywords.length(); j++){
							String keyword = keywords.getString(j).toLowerCase().trim();
							if(text.contains(keyword)){
								classes.add(className);
								// found class skip to end
								j = keywords.length() + 1;
							}
						}
					}
				}
			}
			catch(Exception e){
				e.printStackTrace();
			}
		}
		
		return classes;
	}
	
	private ArrayList<String> getClassesRemote(String text){
		logger.log(Level.INFO, "REMOTE CLASSIFY FOR: " + text);
		ArrayList<String> classes = new ArrayList<String>();

		JSONArray classifiers = null;
		if(this.serializableClassifiers != null){
			classifiers = this.serializableClassifiers;
		}
		
		if(classifiers == null || classifiers.length() < 1){
			logger.log(Level.ERROR, "classifiers json for nl classifier not defined");
			return classes;
		}
		
		
		CloseableHttpClient httpclient = null;
		try {
			int classifierIndex = (int) Math.floor(Math.random()*classifiers.length());
			JSONObject classifier = classifiers.getJSONObject(classifierIndex);
			
			CredentialsProvider credsProvider = new BasicCredentialsProvider();
		    credsProvider.setCredentials(
		    		new AuthScope(classifier.getString("host"), 443),
		            new UsernamePasswordCredentials(classifier.getString("user"), classifier.getString("password")));
		    
		    httpclient = HttpClients.custom()
		    		.setDefaultCredentialsProvider(credsProvider)
		    		.setConnectionManager(WatsonServices.getConnectionManager())
		    		.setConnectionManagerShared(true)
		            .build();
		
       
        	URIBuilder builder = new URIBuilder();
            builder
            	.setScheme("https")
            	.setHost(classifier.getString("host"))
            	.setPort(443)
            	.setPath(classifier.getString("path"))
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
                    	EntityUtils.consume(response.getEntity());
                    	logger.log(Level.ERROR, EntityUtils.toString(response.getEntity()));
                        throw new ClientProtocolException("Unexpected response status: " + status);
                    }
                  
                    
                }
                
                
            };
            
            String responseBody = httpclient.execute(httpget, responseHandler, context);
            
            if(responseBody != null && responseBody.length() > 0){
            	JSONObject classifierResponse = new JSONObject(responseBody);
            	if(classifierResponse != null){
            		if(classifierResponse.has("classes")){
            			JSONArray identifiedClasses = classifierResponse.getJSONArray("classes");
            			for(int i=0; i < identifiedClasses.length(); i++){
            				JSONObject identifiedClass = identifiedClasses.getJSONObject(i);
            				if(identifiedClass != null){
            					double confidence = identifiedClass.getDouble("confidence");
            					if(confidence > minConfidence){
            						classes.add(identifiedClass.getString("class_name").toLowerCase());
            					}
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
        
	
		return classes;
	}
	
	
}
