package com.ibm.cscc.tutorial;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Date;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;


public class Producer extends MessageHub{
	
	private static final Logger logger = Logger.getLogger(Producer.class);
	
	public static String PROCESS_ID = null;
	
	private boolean running = true;
	protected Properties properties = null;
	protected KafkaProducer<String, String> kafkaProducer = null;
	
	
	public Producer(){
		PROCESS_ID = MessageHub.getRandomId();
		MessageHub.resourceDir = resourceDir;
		this.init();
	}
	
	
	private void init(){
		
		userDir = System.getProperty("user.dir");
		if(userDir.startsWith("/vagrant")){
			logger.log(Level.WARN, "Producer is running in Vagrant");
		}
		
		String propertiesFile = "/kafka.properties";
		resourceDir = userDir + File.separator + "resources";
	
		this.initLog4J();
		
		logger.log(Level.INFO, "Using properties:" + propertiesFile);
		
		properties = new Properties();
		
		// load default properties from file if given
		InputStream propertiesInputStream  =  Producer.class.getResourceAsStream(propertiesFile);
		try {
			properties.load(propertiesInputStream);
			propertiesInputStream.close();
		} 
		catch (IOException e1) {
			e1.printStackTrace();
		}
		
		String userId = System.getenv("MH_USER");
		String password = System.getenv("MH_PASSWORD");
		String clientId = System.getenv("MH_CLIENTID");
		
		properties.setProperty("client.id", clientId);
		
    	// Set JAAS configuration property
        Producer.updateJaasConfiguration(userId, password);
        if(System.getProperty(MessageHub.JAAS_CONFIG_PROPERTY) == null) {
            System.setProperty(MessageHub.JAAS_CONFIG_PROPERTY, resourceDir + File.separator + "jaas.conf");
        }
        
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
	

	private KafkaProducer<String,String> createKafkaProducer(){
		if(properties.getProperty("cscc.topic") == null || properties.getProperty("cscc.topic").length() < 1){
        	logger.log(Level.ERROR,"cscc.topic property not specified");
        }
		KafkaProducer<String, String> kafkaProducer = new KafkaProducer<String, String>(properties);
		return kafkaProducer;
	}


	public void start() {
		String topic = properties.getProperty("cscc.topic");
	
		while(running){
			try {
				Date now = new Date();
				String message = now.toString();
				ProducerRecord<String,String> producerRecord = new ProducerRecord<String,String>(topic, message);
				kafkaProducer.send(producerRecord);
				logger.log(Level.INFO,"Added: " + message);
				Thread.sleep(1000);
			} 
			catch (final Exception e) {
				logger.log(Level.ERROR, "Producer has failed with exception: " + e);
			}
		}
		
		logger.log(Level.ERROR, "Shutting down Kafka Consumer");
		kafkaProducer.close();
	}
	
	
	public void shutdown(){
		this.running = false;
	}
	
	
	public static void main(String args[]){
		System.out.println("Starting up an producer...");
		Producer producer = new Producer();
		producer.start();
	}
	
}
