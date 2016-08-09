package com.ibm.ccc.analytics;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;


public class SentimentScorer extends AbstractTextScorer {

	public HashMap<String, Integer> sentimentWords = null;
	
	public SentimentScorer(){
		this.loadSentimentWords();
	}

	public int score(String text) {
		if(text == null || text.length() < 1){
			return 0;
		}
    	
    	int totalSentiment = 0;
    	int averageSentiment = 0;
    	int sentimentWordCount = 0;
    	
    	String words[] = text.split(" ");
    	for(int i=0; i < words.length; i++){
    		String word = words[i];
    		if(word != null){
    			word = word.replace(".", " ");
        		word = word.replace("?", " ");
        		word = word.replace("!", " ");
        		word = word.replace(":", " ");
        		word = word.trim().toLowerCase();
        		Integer value = sentimentWords.get(word);
        		if(value != null){
        			totalSentiment = totalSentiment + value.intValue();
        			sentimentWordCount++;
        		}
    		}
    		
    	}
    	
    	if(sentimentWordCount < 1){
    		averageSentiment = totalSentiment;
    	}
    	else{
    		averageSentiment = totalSentiment / sentimentWordCount;
    	}
    	
    	return Math.round(averageSentiment);
	}
	
	
	private void loadSentimentWords(){
	    System.out.println("loading sentiment words...");
	    this.sentimentWords = new HashMap<String, Integer>();
	    try{
	       	String line = "";
	       	BufferedReader reader = new BufferedReader(new InputStreamReader( SentimentScorer.class.getResourceAsStream("AFINN-96.txt")));
	       	line = reader.readLine();
	       	while(line!=null){
	       		String split[] = line.split("\t");
	       		this.sentimentWords.put(split[0].trim(), new Integer(split[1].trim()));
	       		line = reader.readLine();
	       	}
	       	reader.close();
	    }
	    catch(Exception e){
	    	e.printStackTrace();
	    }
	 }

}
