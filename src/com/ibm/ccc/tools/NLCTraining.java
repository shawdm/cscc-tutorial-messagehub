package com.ibm.ccc.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.json.JSONObject;

public class NLCTraining {
	
	public static String[] ALLOWED_TAGS = {
	    "event", "ignore", "sport", "celebrity", "maleplayer", "femaleplayer"
	};
	
	public NLCTraining(){
		
	}
	
	public void processTrainingData(String importDirectory, String exportDirectory){
		ArrayList<ArrayList<String>> cleanedRecords = this.processDirectory(new File(importDirectory));
		System.out.println("number of cleaned records: " + cleanedRecords.size());
		File exportDir = new File(exportDirectory);
		if(!exportDir.exists()){
			exportDir.mkdirs();
		}
		
		FileWriter outputWriter = null;
		CSVPrinter csvPrinter = null;
		try{
			File outputFile = new File(exportDirectory + "/cleaned.csv");
			outputWriter = new FileWriter(outputFile);
			csvPrinter = new CSVPrinter(outputWriter, CSVFormat.DEFAULT);
			csvPrinter.printRecords(cleanedRecords);
		}
		catch(Exception e){
			e.printStackTrace();
		}
		finally{
			try{
			csvPrinter.flush();
			csvPrinter.close();
			outputWriter.close();
			}
			catch(Exception e){
			}
		}
		


	}
	

	private ArrayList<ArrayList<String>> processDirectory(File directory){
			ArrayList<ArrayList<String>> cleanedRecords = new ArrayList<ArrayList<String>>();
			
			if(directory.isDirectory()){
				File testFiles[] = directory.listFiles();
				for(int i=0; i < testFiles.length; i++){
					File testFile = testFiles[i];
					if(testFile.isDirectory()){
						cleanedRecords.addAll(this.processDirectory(testFile));
					}
					else if(testFile.getName().endsWith("csv")){
						cleanedRecords.addAll(this.processFile(testFile));
					}
				}
			}
			return cleanedRecords;
		}
		
		private ArrayList<ArrayList<String>> processFile(File file){
			
			ArrayList<ArrayList<String>> cleanedRecords = new ArrayList<ArrayList<String>>();
			
			if(file.getName().endsWith("csv")){
				FileInputStream fileInputStream = null;
				InputStreamReader inputStreamReader = null;
				
				try{
					fileInputStream = null;
					System.out.println("Processing File: " + file.toString());
					fileInputStream =  new FileInputStream(file);
					 
					inputStreamReader = new InputStreamReader(fileInputStream);
					 
					Iterable<CSVRecord> records = CSVFormat.DEFAULT.parse(inputStreamReader);
					for (CSVRecord record : records) {
						ArrayList<String> cleanedRecord = this.cleanRecord(record);
						cleanedRecords.add(cleanedRecord);
					}
				}
				catch(Exception e){
					e.printStackTrace();
				}
				finally{
					try{
						inputStreamReader.close();
						fileInputStream.close();
					}
					catch(Exception e2){}
				}
			}
			return cleanedRecords;
		}
		
		
		private ArrayList<String> cleanRecord(CSVRecord record){
			ArrayList<String> cleanRecord = new ArrayList<String>();
			 if(record.size() > 0){
			     String content = record.get(0);
			     cleanRecord.add(content);
			    
			     for(int i=1; i < record.size(); i++){
			    	 String tags = record.get(i);
			    	 String tagsArray[] = tags.split(",");
			    	 for(int j=0; j < tagsArray.length; j++){
			    		 String tag = tagsArray[j];
			    		 if(tag != null && tag.length() > 0 && tag.trim().length() > 0){
			    			 tag = tag.toLowerCase().trim();
			    			 if(tag.equals("wimbledon")){
			    				 tag = "event";
			    			 }
			    			 
			    			 if(allowedTag(tag)){
			    				 cleanRecord.add(tag);
			    			 }
			    			
			    		 }
			    	 }
			    	 
			     }
			     
			     if(cleanRecord.size() < 2){
			    	 cleanRecord.add("ignore");
			     }
			     
			     
			 }
			 return cleanRecord;
		}
		
		private boolean allowedTag(String tag){
			for (String c : ALLOWED_TAGS) {

				if(c.equals(tag)){
					return true;
				}
			      
			}
			return false;
		}
		
		public static void main(String args[]){
			System.out.println("Statying up NLC-Training");
			NLCTraining nlcTraining = new NLCTraining();
			nlcTraining.processTrainingData("/Users/shawdm/Documents/Projects/Wimbledon2016/data/training/interns/results", "/Users/shawdm/Documents/Projects/Wimbledon2016/data/training/interns/results-cleaned");
		}
	
}
