package com.ibm.ccc.annotate;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.json.JSONArray;
import org.json.JSONObject;

import com.ibm.ccc.analytics.GeotagScorer;
import com.ibm.hursley.ccc.watson.AlchemyCombined;
import com.ibm.hursley.ccc.watson.ConceptTagger;
import com.ibm.hursley.ccc.watson.EntityExtraction;
import com.ibm.hursley.ccc.watson.NLClassifier;
import com.ibm.hursley.ccc.watson.SentimentScorer;
import com.ibm.hursley.ccc.watson.WatsonServices;


public class Annotator{
	
	private static final Logger logger = Logger.getLogger(Annotator.class);
	private static final String JAAS_CONFIG_PROPERTY = "java.security.auth.login.config";
	public static final String DATE_FORMAT_GNIP = "yyyy-MM-dd'T'HH:mm:ss.SSSX";
	public static final String DATE_FORMAT_FACEBOOK = "yyyy-MM-dd'T'HH:mm:ss'+'SSSS"; //2016-03-28T17:21:56+0000
	public static final String DATE_FORMAT_YOUTUBE = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"; //2016-03-28T17:21:56.032Z
	private static final int QUEUE_SIZE = 40;
	protected static String userDir = null;
	protected static String resourceDir = null;
	protected static boolean isDistribution = false;
	public static String PROCESS_ID = null;

	public static ArrayList<ProcessQueueThread> threads = null;
	
	public static int THREAD_COUNT = 10;
	public static boolean SIMULATION = false;
	public static double TOPIC_CONFIDENCE = 0.8;
	public static double CLASS_CONFIDENCE = 0.8;
	public static ArrayList<String> EXCLUDED_TOPICS = new ArrayList<String>();
	public static ArrayList<String> EXCLUDED_ENTITIES = new ArrayList<String>();

	protected ArrayBlockingQueue<ConsumerRecord<String, String>> recordsQueue;
	private boolean running = true;
	private Properties consumerProperties = null;
	
	private NLClassifier nlClassifier = null;
	private GeotagScorer geotagScorer = null;
	private AlchemyCombined alchemyCombined = null;
	
	KafkaConsumer<String, String> kafkaConsumer = null;
	KafkaProducer<String, String> kafkaProducer = null;
	
	
	 class ProcessQueueThread extends Thread{
		 private String threadId = getRandomId();
			public void run() {
				logger.log(Level.INFO, "Starting to process the queue");
				while(running){
					if(recordsQueue.isEmpty()){
						try {
							Thread.sleep(Math.round(5000 * Math.random()));
						}
						catch (InterruptedException e) {}
					}
					else{
						try {
							ConsumerRecord<String, String> record = recordsQueue.poll(200, TimeUnit.MILLISECONDS);
							if(record != null){
					        	String message = record.value();
					        	String key = record.key();
					        	handleMessage(threadId, key, message);
					        	record = null;
							}
							
						} 
						catch (InterruptedException e) {
							e.printStackTrace();
						} 				
					}
				}
				logger.log(Level.INFO, "Stopped Processing the queue.");
			}
	    	
	    }
	
	public Annotator(String resourceDir){
		PROCESS_ID = getRandomId();
		Annotator.resourceDir = resourceDir;
		this.init(resourceDir);
	}
	
	public static String getRandomId() {
		SecureRandom random = new SecureRandom();
		return new BigInteger(130, random).toString(32);
	}
	
	
	private void addVcapToProperties(){
		JSONObject userProvidedServiceConfig = null;
		String vcapServices = System.getenv("VCAP_SERVICES");
		if(vcapServices != null) {
			try {
				JSONObject vcapJson = new JSONObject(vcapServices);
                // get user-provided-service config
                if(vcapJson != null && vcapJson.has("user-provided")){
                	 JSONArray userProvidedServices = vcapJson.getJSONArray("user-provided");
                	 for(int i=0; i < userProvidedServices.length(); i++){
                		 JSONObject userProvidedService = userProvidedServices.getJSONObject(i);
                		 if(userProvidedService != null && userProvidedService.has("name") && userProvidedService.getString("name").equalsIgnoreCase("ccc-annotator-config")){
                			 if(userProvidedService.has("credentials")){
                				 userProvidedServiceConfig = userProvidedService.getJSONObject("credentials");
                				 if(userProvidedServiceConfig.has("annotation.simulation")){
                					 this.consumerProperties.setProperty("annotation.simulation", userProvidedServiceConfig.getString("annotation.simulation"));
                				 }
                				 if(userProvidedServiceConfig.has("annotation.topic.confidence")){
                					 this.consumerProperties.setProperty("annotation.topic.confidence", userProvidedServiceConfig.getString("annotation.topic.confidence"));
                				 }
                				 if(userProvidedServiceConfig.has("annotation.class.confidence")){
                					 this.consumerProperties.setProperty("annotation.class.confidence", userProvidedServiceConfig.getString("annotation.class.confidence"));
                				 }
                				 if(userProvidedServiceConfig.has("annotation.topic.ignore")){
                					 String list = userProvidedServiceConfig.getString("annotation.topic.ignore");
                					 if(list != null && list.length() > 0){
                						String items[] = list.split(",");
                						for(int j=0; j <items.length; j++){
                							String excludeItem = items[j];
                							if(excludeItem != null && excludeItem.length() > 0){
                								excludeItem = excludeItem.toLowerCase().trim();
                								EXCLUDED_TOPICS.add(excludeItem);
                							}
                						}
                					 }
                				 }
                				 if(userProvidedServiceConfig.has("annotation.entity.ignore")){
                					 String list = userProvidedServiceConfig.getString("annotation.entity.ignore");
                					 if(list != null && list.length() > 0){
                						String items[] = list.split(",");
                						for(int j=0; j <items.length; j++){
                							String excludeItem = items[j];
                							if(excludeItem != null && excludeItem.length() > 0){
                								excludeItem = excludeItem.toLowerCase().trim();
                								EXCLUDED_ENTITIES.add(excludeItem);
                							}
                						}
                					 }
                				 }
                				 if(userProvidedServiceConfig.has("annotation.threads")){
                					 this.consumerProperties.setProperty("annotation.threads", userProvidedServiceConfig.getString("annotation.threads"));
                				 }
                			 }
                		 }
                		 
                	 }
                 }
                 
                 if(vcapJson != null &&  vcapJson.has("messagehub")){
                	 JSONArray messageHubServices = vcapJson.getJSONArray("messagehub");
                	 if(messageHubServices != null && messageHubServices.length() > 0){
                		 JSONObject messageHubService = messageHubServices.getJSONObject(0);
                		 if(messageHubService != null && messageHubService.has("credentials")){
                			 JSONObject messageHubServiceConfig = messageHubService.getJSONObject("credentials");
                			 if(messageHubServiceConfig.has("api_key")){
                			 }
                			 if(messageHubServiceConfig.has("user")){
                				 consumerProperties.put("broker_user", messageHubServiceConfig.getString("user"));
                			 }
                			 if(messageHubServiceConfig.has("password")){
                				 consumerProperties.put("broker_password", messageHubServiceConfig.getString("password"));
                			 }
                			 if(messageHubServiceConfig.has("kafka_brokers_sasl")){
                				 JSONArray brokers = messageHubServiceConfig.getJSONArray("kafka_brokers_sasl");
                				 if(brokers != null && brokers.length() > 0){
                					 String bootstrapProperty = "";
                					 for(int i=0; i < brokers.length(); i++){
                						 String broker = brokers.getString(i);
                						 if(i>0){
                							 bootstrapProperty = bootstrapProperty+",";
                						 }
                						 bootstrapProperty = bootstrapProperty + broker;
                					 }
                					 consumerProperties.put("bootstrap.servers",bootstrapProperty);                					
                				 }
                			 }
                		 }
                	 }
                 }
            } 
			catch(final Exception e) {
				e.printStackTrace();
				return;
            }             
		} 
		else { 
			logger.log(Level.ERROR, "VCAP_SERVICES environment variable is null, are you running outside of Bluemix? Or are you running locally and haven't setup your local.env file? See README.");
			System.exit(0);
         }
	}
	
	
	private void init(String overrideResourceDir){
		
		
		userDir = System.getProperty("user.dir");
		if(userDir.startsWith("/vagrant")){
			logger.log(Level.WARN, "Annotator is running in Vagrant");
		}
		isDistribution = new File(userDir + File.separator + ".java-buildpack").exists();
	
		String propertiesFile = "/kafka.properties";
		if(isDistribution){
			System.out.println("Running on Bluemix");
			resourceDir = Annotator.class.getResource("/").getFile();
		}
		else{
			System.out.println("Running on Local");
			resourceDir = userDir + File.separator + "resources";
		}
		
		this.initLog4J();
		
		logger.log(Level.INFO, "Using properties:" + propertiesFile);
		
		consumerProperties = new Properties();
		
		// load default properties from file if given
		InputStream propertiesInputStream  =  Annotator.class.getResourceAsStream(propertiesFile);
		try {
			consumerProperties.load(propertiesInputStream);
			propertiesInputStream.close();
		} 
		catch (IOException e1) {
			e1.printStackTrace();
		}
		
		// add vcap services to 
		this.addVcapToProperties();
	
		
		if(isDistribution) {
            consumerProperties.put("ssl.truststore.location", userDir + "/.java-buildpack/open_jdk_jre/lib/security/cacerts");
        }
	
    	// Set JAAS configuration property
        Annotator.updateJaasConfiguration(consumerProperties.getProperty("broker_user"), consumerProperties.getProperty("broker_password"));
        if(System.getProperty(JAAS_CONFIG_PROPERTY) == null) {
            System.setProperty(JAAS_CONFIG_PROPERTY, resourceDir + File.separator + "jaas.conf");
        }
        
        SIMULATION = Boolean.parseBoolean(consumerProperties.getProperty("annotation.simulation", "false"));
        TOPIC_CONFIDENCE = Double.parseDouble(consumerProperties.getProperty("annotation.topic.confidence","0.8"));
        THREAD_COUNT = Integer.parseInt(consumerProperties.getProperty("annotation.threads", "5"));
        CLASS_CONFIDENCE = Double.parseDouble(consumerProperties.getProperty("annotation.class.confidence","0.8"));
        		
        // create services
        this.nlClassifier = new NLClassifier(getClassifiers(), getKeywordClassifier(), CLASS_CONFIDENCE, SIMULATION);
        this.geotagScorer = new GeotagScorer();
        this.alchemyCombined = new AlchemyCombined(getAlchemyCombinedTaggers(), EXCLUDED_TOPICS, EXCLUDED_ENTITIES);
        
        
        // create processing queue
        this.recordsQueue = new ArrayBlockingQueue<ConsumerRecord<String,String>>(QUEUE_SIZE);
        
        // create kafka consumer/producer
        kafkaConsumer = this.createKafkaConsumer();
		kafkaProducer = this.createKafkaProducer();
	}
	
	
	private void initLog4J(){
    	Properties log4jProperties = new Properties();
    	
    	FileReader fileReader = null;
    	String log4jPropertiesPath = resourceDir  + File.separator + "log4j.properties";
       
    	try {
    		File log4jPropertiesFile = new File(log4jPropertiesPath);
    		fileReader = new FileReader(log4jPropertiesFile);
            log4jProperties.load(fileReader);
            PropertyConfigurator.configure(log4jProperties);
            System.out.println("Using log4j.properties from: " + log4jPropertiesPath);
    	}
    	catch (IOException e) {
			e.printStackTrace();
		}	
        finally{
        	try{
        		fileReader.close();
        	}
        	catch(Exception e){}
        }
    }
	
	private KafkaConsumer<String, String> createKafkaConsumer(){
		if(System.getProperty(JAAS_CONFIG_PROPERTY) == null) {
            System.setProperty(JAAS_CONFIG_PROPERTY, resourceDir + File.separator + "jaas.conf");
            File jassFile = new File(System.getProperty(JAAS_CONFIG_PROPERTY));
            if(jassFile.exists()){
            	logger.log(Level.INFO, "Setting JAAS_CONFIG_PROPERTY to " + System.getProperty(JAAS_CONFIG_PROPERTY));
            }
            else{
            	logger.log(Level.ERROR, System.getProperty(JAAS_CONFIG_PROPERTY) + " DOES NOT EXIST");
            }
        }
		
		// create producer
		KafkaConsumer<String,String> kafkaConsumer = new KafkaConsumer<String, String>(consumerProperties);
        if(consumerProperties.getProperty("kafka.incoming.topic") == null || consumerProperties.getProperty("kafka.incoming.topic").length() < 1){
        	logger.log(Level.ERROR,"kafka.topic property not specified");
        }
        ArrayList<String> topicList = new ArrayList<String>();
        topicList.add(consumerProperties.getProperty("kafka.incoming.topic"));
        
        kafkaConsumer.subscribe(topicList);
        return kafkaConsumer;
	}
	
	
	private KafkaProducer<String,String> createKafkaProducer(){
		if(consumerProperties.getProperty("kafka.outgoing.topic") == null || consumerProperties.getProperty("kafka.outgoing.topic").length() < 1){
        	logger.log(Level.ERROR,"kafka.outgoing.topic property not specified");
        }
		KafkaProducer<String, String> kafkaProducer = new KafkaProducer<String, String>(consumerProperties);
		return kafkaProducer;
	}

	/**
     * Updates JAAS config file with provided credentials.
     * @param credentials {MessageHubCredentials} Object which stores Message Hub credentials
     *      retrieved from the VCAP_SERVICES environment variable.
     */
    protected static void updateJaasConfiguration(String userId, String password) {
    	
        String templatePath = resourceDir  + File.separator + "jaas.conf.template";
        String path = resourceDir + File.separator + "jaas.conf";
        OutputStream jaasStream = null;

        logger.log(Level.INFO, "Updating JAAS configuration");
        logger.log(Level.INFO, "Setting broker user/password to " + userId + ":" + password);

        try {
            String templateContents = new String(Files.readAllBytes(Paths.get(templatePath)));
            jaasStream = new FileOutputStream(path, false);

            String fileContents = templateContents
                .replace("$USERNAME", userId)
                .replace("$PASSWORD", password);
            
            jaasStream.write(fileContents.getBytes(Charset.forName("UTF-8")));
           
        } 
        catch (final FileNotFoundException e) {
            logger.log(Level.ERROR, "Could not load JAAS config file at: " + path);
        } 
        catch (final IOException e) {
            logger.log(Level.ERROR, "Writing to JAAS config file:");
            e.printStackTrace();
        } 
        finally {
            if(jaasStream != null) {
                try {
                    jaasStream.close();
                } 
                catch(final Exception e) {
                    logger.log(Level.ERROR, "Closing JAAS config file:");
                    e.printStackTrace();
                }
            }
        }
    }
	

	public void start() {
		threads = new ArrayList<Annotator.ProcessQueueThread>(THREAD_COUNT);
		for(int i=0; i < THREAD_COUNT; i++){
			ProcessQueueThread thread = new ProcessQueueThread();
			thread.start();
			threads.add(thread);
		}
		
	
		while(running){
			try {
				// Poll on the Kafka consumer every second.
				Iterator<ConsumerRecord<String, String>> it = kafkaConsumer.poll(10000).iterator();
				kafkaConsumer.commitSync();
				boolean empty = true;
		        while (it.hasNext() ) {
		        	empty = false;
		        	ConsumerRecord<String, String> record = it.next();
		        	this.recordsQueue.put(record);
		        }
		      
		        it = null;
		        
		        if(empty){
		        	Thread.sleep(Math.round(Math.random()*500));
		        	System.gc();
		        }
		               
			} 
			catch (final Exception e) {
				logger.log(Level.ERROR, "Consumer has failed with exception: " + e);
			}
		}
		
		logger.log(Level.ERROR, "Shutting down Kafka Consumer");
		kafkaConsumer.close();
	}
	
	
	private void handleMessage(String threadId, String key, String message){
		//logger.log(Level.INFO, "Processing a message: " +key + " : " + message);
		if(key == null || message == null){
			logger.log(Level.ERROR, "Empty message");
		}
		try{
			JSONObject sourceJson = new JSONObject(message);
			sourceJson.put("ccc-annotate-id", PROCESS_ID + "-" + threadId);
			sourceJson = handleMessageGeneric(sourceJson);
			if(key.equalsIgnoreCase("twitter")){
				sourceJson = handleMessageTwitter(sourceJson);
			}
			else if(key.equalsIgnoreCase("facebook")){
				sourceJson = handleMessageFacebook(sourceJson);
			}
			else if(key.equalsIgnoreCase("youtube")){
				sourceJson = handleMessageYoutube(sourceJson);
			}
			else{
				logger.log(Level.ERROR, "No parser to handle content from: " + key);
			}
			this.publishMessage(kafkaProducer, key, sourceJson.toString());
		}
		catch(Exception e){
			logger.log(Level.ERROR, "Unable to parse json");
		}
	}
	
	
	private JSONObject handleMessageGeneric(JSONObject json){
		if(json!=null && json.has("body")){
			try{
				String messageId = "unknown";
				if(json.has("id")){
					messageId = json.getString("id");
				}	
				
				// classes
				boolean processMore = false;
				ArrayList<String> classes =  nlClassifier.getClasses(json.getString("body"));
				if(classes != null){
					JSONArray classsesJson = new JSONArray();
					for(int i=0; i < classes.size() && i <1; i++){ // for now just add first class
						JSONObject classJson = new JSONObject();
						classJson.put("id", classes.get(i).toLowerCase());
						logger.log(Level.INFO, "Class: For message, "+messageId+" adding classes: " + classes.get(i).toLowerCase());
						classsesJson.put(classJson);
						if(classes.get(i).toLowerCase().equals("sport") || classes.get(i).toLowerCase().equals("event")){
							processMore = true; // only process more if its a relavant one
						}
					}
					json.put("ccc_meta_classes", classsesJson);
				}
				
				if(processMore){
					boolean english = true;
					
					if(json.has("twitter_lang")){
						if(!json.getString("twitter_lang").equalsIgnoreCase("en")){
							english = false;
						}
					}
					
					JSONObject combinedResults = this.alchemyCombined.tag(json.getString("body"));
					
					JSONArray topicsJSON = new JSONArray();
					json.put("ccc_meta_topics", topicsJSON);
					if(english){ // only use alchemy for topics if english
						if(combinedResults.has("concepts")){
							JSONArray topics = combinedResults.getJSONArray("concepts");
							if(topics != null && topics.length() > 0){
								String topicString = "";
								for(int i=0; i < topics.length(); i++){
									JSONObject topic = topics.getJSONObject(i);
									String topicName = topic.getString("name");
									topicsJSON.put(topic);
									topicString = topicString + "["+topicName+"]";
								}
								logger.log(Level.INFO, "Topic: for message, "+messageId+" adding topics: " + topicString);
							}
						}
						
					}
					
					JSONArray entities = combinedResults.getJSONArray("entities");
					if(entities != null && entities.length() > 0){
						String entityString = "";
						for(int i=0; i < entities.length(); i++){
							JSONObject entity = entities.getJSONObject(i);
							// if its not a topic, add it
							if(!hasTopic(topicsJSON, entity.getString("id"))){
								topicsJSON.put(entity);
								entityString = entityString + "["+entities.getJSONObject(i).getString("name")+"]";
							}
						}
						if(entityString.length() > 0){
							logger.log(Level.INFO, "Entity: for message, "+messageId+ " adding entities as topics: " +entityString );
						}
						json.put("ccc_meta_entities", entities);
					}
					
					double sentiment = combinedResults.getDouble("sentiment");
					logger.log(Level.INFO, "For message, "+messageId+" setting sentiment: " + sentiment);
					json.put("ccc_meta_sentiment",sentiment);
				}
			}
			catch(Exception e){
				e.printStackTrace();
			}
		}
		return json;
	}
	
	
	
	private JSONObject handleMessageTwitter(JSONObject jsonObject){
		String tweetId = "unknown";
		Date parsedDate = null;
		if(jsonObject.has("postedTime") && jsonObject.has("id")){
			try {
				SimpleDateFormat sdf = new SimpleDateFormat(Annotator.DATE_FORMAT_GNIP);
				parsedDate = sdf.parse(jsonObject.getString("postedTime"));
				tweetId = jsonObject.getString("id");
				if(parsedDate != null){
					logger.log(Level.INFO, "Processing message from Twitter: "+ tweetId+" at:" + parsedDate.toString());
				}
			} 
			catch (Exception e) {
			}	
		}
		
		// add in location detection
		try{
			if(jsonObject.has("actor")){
				JSONObject actorObject = jsonObject.getJSONObject("actor");
				if(actorObject.has("location")){
					JSONObject locationObject = actorObject.getJSONObject("location");
					if(locationObject.has("displayName")){
						String locationText = locationObject.getString("displayName");
						JSONObject detectedLocation = geotagScorer.geotagLocationText(locationText);
						if(detectedLocation.has("country")){
							logger.log(Level.INFO, "Detected a country:" + detectedLocation.getString("country").toLowerCase() + " from:"+locationText);
							jsonObject.put("ccc_meta_country",detectedLocation.getString("country").toLowerCase());
						}
					}
						 
					
				}
			}
		}
		catch(Exception e){
		}
		
		
		
		return jsonObject;
	}
	
	
	private JSONObject handleMessageFacebook(JSONObject jsonObject){
		String tweetId = "unknown";
		Date parsedDate = null;
		if(jsonObject.has("postedTime") && jsonObject.has("id")){
			try {
				SimpleDateFormat sdf = new SimpleDateFormat(Annotator.DATE_FORMAT_FACEBOOK);
				parsedDate = sdf.parse(jsonObject.getString("postedTime"));
				tweetId = jsonObject.getString("id");
				
				logger.log(Level.INFO, "Processing message from Facebook: "+ tweetId+" at:" + parsedDate);
				// convert to GNIP FORMAT
				SimpleDateFormat gnipSdf = new SimpleDateFormat(Annotator.DATE_FORMAT_GNIP);
				jsonObject.put("postedTime", gnipSdf.format(parsedDate));
			} 
			catch (Exception e) {
			}	
		}
		return jsonObject;
	}
	
	
	private JSONObject handleMessageYoutube(JSONObject jsonObject){
		String tweetId = "unknown";
		Date parsedDate = null;
		if(jsonObject.has("postedTime") && jsonObject.has("id")){
			try {
				SimpleDateFormat sdf = new SimpleDateFormat(Annotator.DATE_FORMAT_YOUTUBE);
				parsedDate = sdf.parse(jsonObject.getString("postedTime"));
				tweetId = jsonObject.getString("id");
				
				logger.log(Level.INFO, "Processing message from Youtube: "+ tweetId+" at:" + parsedDate);
				// convert to GNIP FORMAT
				SimpleDateFormat gnipSdf = new SimpleDateFormat(Annotator.DATE_FORMAT_GNIP);
				jsonObject.put("postedTime", gnipSdf.format(parsedDate));
			} 
			catch (Exception e) {
			}	
		}
		return jsonObject;
	}
	
	private void publishMessage(KafkaProducer<String, String> kafkaProducer, String key, String message){
		String topic = consumerProperties.getProperty("kafka.outgoing.topic");
		ProducerRecord<String,String> producerRecord = new ProducerRecord<String,String>(topic, key, message);

	    try{
	    	kafkaProducer.send(producerRecord);
	    }
	    catch(Exception e){
	        e.printStackTrace();   
	    }
	}
	
	public void shutdown(){
		this.running = false;
	}
	
	private static JSONArray getClassifiers(){
		JSONArray classifiers = new JSONArray();
		
		InputStream inputStream = null;
		InputStreamReader inputStreamReader = null;
		BufferedReader bufferedReader = null;
		try{
			inputStream =  Annotator.class.getResourceAsStream("/classifiers.json");
			if(inputStream != null){
				inputStreamReader = new InputStreamReader(inputStream);
				bufferedReader = new BufferedReader(inputStreamReader);
				String content = "";
				String line = bufferedReader.readLine();
				while(line != null){
					content = content + line;
					line = bufferedReader.readLine();
				}
				if(content.length() > 0){
					classifiers = new JSONArray(content);
				}
			}
			else{
				logger.log(Level.ERROR, "Can't load classifiers.json");
			}
		}
		catch(Exception e){
			e.printStackTrace();
		}
		finally{
			try{
				bufferedReader.close();
				inputStreamReader.close();
				inputStream.close();
			}
			catch(Exception e2){
				e2.printStackTrace();
			}
		}
		
		return classifiers;
	}
	
	private static JSONArray getKeywordClassifier(){
		JSONArray classifiers = new JSONArray();
		
		InputStream inputStream = null;
		InputStreamReader inputStreamReader = null;
		BufferedReader bufferedReader = null;
		try{
			inputStream =  Annotator.class.getResourceAsStream("/classifiers-keywords.json");
			if(inputStream != null){
				inputStreamReader = new InputStreamReader(inputStream);
				bufferedReader = new BufferedReader(inputStreamReader);
				String content = "";
				String line = bufferedReader.readLine();
				while(line != null){
					content = content + line;
					line = bufferedReader.readLine();
				}
				if(content.length() > 0){
					classifiers = new JSONArray(content);
				}
			}
			else{
				logger.log(Level.ERROR, "Can't load classifiers-keywords.json");
			}
		}
		catch(Exception e){
			e.printStackTrace();
		}
		finally{
			try{
				bufferedReader.close();
				inputStreamReader.close();
				inputStream.close();
			}
			catch(Exception e2){
				e2.printStackTrace();
			}
		}
		
		return classifiers;
	}
    
    
    
    
    
    
    
    
    
    
    private static JSONArray getAlchemyCombinedTaggers(){
		JSONArray combinedTagger = new JSONArray();
		
		InputStream inputStream = null;
		InputStreamReader inputStreamReader = null;
		BufferedReader bufferedReader = null;
		try{
			inputStream =  Annotator.class.getResourceAsStream("/alchemy-combined.json");
			if(inputStream != null){
				inputStreamReader = new InputStreamReader(inputStream);
				bufferedReader = new BufferedReader(inputStreamReader);
				String content = "";
				String line = bufferedReader.readLine();
				while(line != null){
					content = content + line;
					line = bufferedReader.readLine();
				}
				if(content.length() > 0){
					combinedTagger = new JSONArray(content);
				}
			}
			else{
				logger.log(Level.ERROR, "Can't load alchemy-combined.json");
			}
		}
		catch(Exception e){
			e.printStackTrace();
		}
		finally{
			try{
				bufferedReader.close();
				inputStreamReader.close();
				inputStream.close();
			}
			catch(Exception e2){
				e2.printStackTrace();
			}
		}
		
		return combinedTagger;
	}
    
    
    
    public static boolean hasTopic(JSONArray topics, String topicId){
    	if(topics != null && topicId != null && topicId.length() > 0){
    		for(int i=0; i <topics.length(); i++){
    			try{
	    			JSONObject topic = topics.getJSONObject(i);
	    			if(topic != null){
	    				if(topic.has("id") && topic.getString("id").equalsIgnoreCase(topicId)){
	    					return true;
	    				}
	    			}
    			}
    			catch(Exception e){
    				e.printStackTrace();
    			}
    		}
    	}
    	return false;
    }
    
	
	public static void main(String args[]){
		System.out.println("Starting up an annotator...");
		
		String resourceDirectory = null;
		if(args.length > 0){
			resourceDirectory = args[0];
		}
		
		Annotator annotator = new Annotator(resourceDirectory);
		annotator.start();
	}
	
}
