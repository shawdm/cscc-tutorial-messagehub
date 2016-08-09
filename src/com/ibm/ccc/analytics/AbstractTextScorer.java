package com.ibm.ccc.analytics;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;


public abstract class AbstractTextScorer {
	
	protected ArrayList<String> noiseWords = null;

	
	public AbstractTextScorer(){
		this.loadNoiseWords();
	}

	public abstract int score(String text);
	
	protected void loadNoiseWords(){
		 this.noiseWords = new ArrayList<String>();
		 
		 try{
			 String line = "";
			 BufferedReader reader = new BufferedReader(new InputStreamReader( GeotagScorer.class.getResourceAsStream("/location_noise.txt")));
			 line = reader.readLine();
		     while(line!=null){
		    	 if(line.length() > 0){
		    		 this.noiseWords.add(line.trim().toLowerCase());
		    	 }
		    	 line = reader.readLine();
		     }
		     reader.close();
		 }
		 catch(Exception e){
			 e.printStackTrace();
		 }
		       	
		 System.out.println("Loaded " + this.noiseWords.size() + " noise words.");
	}
	
	protected String[] splitText(String text){
		String words [] = null;
		if(text == null || text.length() < 1){
			return new String[0];
		}
		
		text = text.replace(".", " ");
		text = text.replace("?", " ");
		text = text.replace("!", " ");
		text = text.replace(":", " ");
		text = text.replace(";", " ");
		text = text.replace(",", " ");
		words = text.split(" ");
		
		for(int i=0; i < words.length; i++){
			words[i].trim();
		}
		
		return words;
	}
	
	protected String[] removeNoiseWords(String[] words){
		ArrayList<String> filteredWords = new ArrayList<String>();
		
		for(int i=0; i < words.length; i++){
			String word = words[i];
			if(!this.noiseWords.contains(word.toLowerCase().trim())){
				filteredWords.add(word);
			}
			
		}
		
		String []stringArray = new String[filteredWords.size()];
		filteredWords.toArray(stringArray);
		return stringArray;
	}
	
}
