package com.ibm.cscc.tutorial;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.SecureRandom;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

public class MessageHub {
	
	private static final Logger logger = Logger.getLogger(MessageHub.class);
	public static final String JAAS_CONFIG_PROPERTY = "java.security.auth.login.config";
	protected static String userDir = null;
	protected static String resourceDir = null;
	protected static boolean isDistribution = false;
	
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
	

	public static String getRandomId() {
		SecureRandom random = new SecureRandom();
		return new BigInteger(130, random).toString(32);
	}
    
}
