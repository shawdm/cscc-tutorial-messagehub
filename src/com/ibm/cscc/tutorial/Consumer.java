package com.ibm.cscc.tutorial;

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
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;


public class Consumer extends MessageHub{
	
	private static final Logger logger = Logger.getLogger(Consumer.class);
	public static String PROCESS_ID = null;

	boolean running = true;
	private Properties properties = null;

	
	protected KafkaConsumer<String, String> kafkaConsumer = null;
	
	public Consumer(){
		PROCESS_ID = MessageHub.getRandomId();
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
		String groupId = System.getenv("MH_CONSUMERGROUPID");
		String clientId = System.getenv("MH_CLIENTID");
		
		properties.setProperty("client.id", clientId);
		properties.setProperty("group.id", groupId);
		
    	// Set JAAS configuration property
        Producer.updateJaasConfiguration(userId, password);
        if(System.getProperty(MessageHub.JAAS_CONFIG_PROPERTY) == null) {
            System.setProperty(MessageHub.JAAS_CONFIG_PROPERTY, resourceDir + File.separator + "jaas.conf");
        }
        
		kafkaConsumer = this.createKafkaConsumer();
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
		KafkaConsumer<String,String> kafkaConsumer = new KafkaConsumer<String, String>(properties);
        if(properties.getProperty("cscc.topic") == null || properties.getProperty("cscc.topic").length() < 1){
        	logger.log(Level.ERROR,"cscc.topic property not specified");
        }
        ArrayList<String> topicList = new ArrayList<String>();
        topicList.add(properties.getProperty("cscc.topic"));
        
        kafkaConsumer.subscribe(topicList);
        return kafkaConsumer;
	}
	
	
	
    public void start(){
    	long count = 0;
    	
    	while(running){
			try {
				// Poll on the Kafka consumer every second.
				
				Iterator<ConsumerRecord<String, String>> it = kafkaConsumer.poll(10000).iterator();
				kafkaConsumer.commitSync();
		        while (it.hasNext() ) {
		        	count++;
		        	ConsumerRecord<String, String> record = it.next();
		        	logger.log(Level.INFO, "Value: " + record.value() + " Count:"+count);
		        }

			} 
			catch (final Exception e) {
				logger.log(Level.ERROR, "Consumer has failed with exception: " + e);
			}
		}
		
		logger.log(Level.ERROR, "Shutting down Kafka Consumer");
		kafkaConsumer.close();
    }
    
    public void shutdown(){
		this.running = false;
		System.out.println("Shutting down");
	}
    
    public void attachShutDownHook(){
    	Runtime.getRuntime().addShutdownHook(new Thread() {
	    	public void run() {
	    		shutdown();
	    	}
    	});
    }
	
	public static void main(String args[]){
		System.out.println("Starting up a Consumer...");
		Consumer consumer = new Consumer();
		consumer.attachShutDownHook();
		consumer.start();
	}
	
	
}
