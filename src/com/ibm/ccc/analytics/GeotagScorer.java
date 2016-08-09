package com.ibm.ccc.analytics;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;


public class GeotagScorer extends AbstractTextScorer {
	
	public HashMap<Integer, JSONObject> geonames = null;
	public HashMap<String, JSONObject> countries = null;
	public HashMap<String, JSONObject> countriesNames = null;
	
	public HashMap<String, Integer> geonamesIndex = null;
	public HashMap<String, Integer> geonamesCityCountryIndex = null;
	public HashMap<String, Integer> geonamesCityStateCountryIndex = null;
	
	public HashMap<String, String> locationsBefore = null;
	
	public static final Logger logger = Logger.getLogger(GeotagScorer.class);
	
	public GeotagScorer(){
		
		
		this.loadCountries();
		this.loadGeonames();
		this.loadLocationsBefore();

	}
	
	
	public int score(String text) {
		return this.geotagText(text).length();
	}
	
	public JSONObject scoreDetailed(String text, String userLocationLat, String userLocationLon){
		JSONObject locationScore = new JSONObject();
		try {
			int scoreAt = 0;
			int scoreFrom = 0;
			int scoreTo = 0;
			
			locationScore.put("score", 0);
			locationScore.put("score_at", scoreAt);
			locationScore.put("score_from", scoreFrom);
			locationScore.put("score_to", scoreTo);
			
			JSONArray locations = this.geotagText(text);
			if(locations != null && locations.length() > 0){
				for(int i=0; i < locations.length(); i++){
					JSONObject location = locations.getJSONObject(i);
					if(location!=null && location.has("direction")){
						if(location.getString("direction").toLowerCase().trim().equals("at")){
							scoreAt++;
						}
						else if(location.getString("direction").toLowerCase().trim().equals("from")){
							scoreFrom++;
						}
						else if (location.getString("direction").toLowerCase().trim().equals("to")){
							scoreTo++;
						}
					}
					
				}
			}
			
			
			if(userLocationLat != null && userLocationLon != null){
				
			}
			
			locationScore.put("score", locations.length());
			locationScore.put("score_at", scoreAt);
			locationScore.put("score_from", scoreFrom);
			locationScore.put("score_to", scoreTo);
		} 
		catch (JSONException e) {
			e.printStackTrace();
		}
		
		return locationScore;
	}
	
	
	
	public JSONArray geotagText(String text){
			
		JSONArray locations = new JSONArray();
		String words[] = this.splitText(text);
		
		for(int i=0; i < words.length; i++){
			if(this.locationsBefore.containsKey(words[i].toLowerCase().trim())){
				if(i+1 < words.length){
					JSONObject location = this.getLocation(words[i+1]);
					if(location != null){
						try {
							location.put("direction", this.locationsBefore.get(words[i].toLowerCase().trim()).toLowerCase().trim());
							locations.put(location);
						} 
						catch (JSONException e) {
							e.printStackTrace();
						}
						
					}
				}
			}
			
		}
		

		
		return locations;
	}
	
	
	public JSONObject geotagLocationText(String text){
		if(text == null || text.trim().length() < 1){
			return null;
		}
		
		text = text.trim();
		
		JSONObject location = null;
		
		try{
			//try and match just countries
			// TODO currently no way of matching this to a lat/lon
			// so ignore anything that is a country
			if(this.countriesNames.containsKey(text.toLowerCase())){
				return null;
			}
			
			//try and match the whole thing
			location = this.getLocation(text);
			if(location!=null){
				return location;
			}
			
			String texts[] = text.split(",");
			if(texts.length > 2){
				// match for city, state, country
				location = this.getLocation(texts[0], texts[1], texts[2]);
				if(location != null){
					return location;
				}
			}
			else if(texts.length > 1){
				// match for city, country
				location = this.getLocation(texts[0], texts[1]);
				if(location != null){
					return location;
				}
			}
		}
		catch(Exception e){
			e.printStackTrace();
		}
		
		
		
		return location;
	}
	
	
	public JSONObject geotagTextDetailed(String text){
		return null;
	}
	
	private synchronized void loadCountries(){
		/*
		COUNTRY FIELDS
		0 (A): country code      : ISO-3166 2-letter country code, 2 characters
		4 (E): country name
		5 (F): capital city
		 */
		
		this.countries = new HashMap<String, JSONObject>();
		this.countriesNames = new HashMap<String, JSONObject>();
		
		try{
	       	String line = "";
	       	BufferedReader reader = new BufferedReader(new InputStreamReader( GeotagScorer.class.getResourceAsStream("/countries.txt")));
	       	line = reader.readLine();
	       	while(line!=null){
	       		String country[] = line.split("\t");
	       		JSONObject countryObject = new JSONObject();
	       		countryObject.put("name", country[4].trim());
	       		countryObject.put("code", country[0].trim());
	       		countryObject.put("capital", country[5].trim());
	       		this.countries.put(country[0], countryObject);
	       		this.countriesNames.put(country[4].trim().toLowerCase(), countryObject);
	       		line = reader.readLine();
	       	}
	       	
	       	reader.close();
		}
		catch(Exception e){
			e.printStackTrace();
		}
	}
	
	private synchronized void loadGeonames(){
		/*
		GEONAMES FIELDS
		0 (A): 	geonameid         : integer id of record in geonames database
		1 (B): 	name              : name of geographical point (utf8) varchar(200)
		2 (C):	asciiname         : name of geographical point in plain ascii characters, varchar(200)
		3 (D):	alternatenames    : alternatenames, comma separated varchar(5000)
		4 (E):	latitude          : latitude in decimal degrees (wgs84)
		5 (F):	longitude         : longitude in decimal degrees (wgs84)
		6 (G):	feature class     : see http://www.geonames.org/export/codes.html, char(1)
		7 (H):	feature code      : see http://www.geonames.org/export/codes.html, varchar(10)
		8 (I):	country code      : ISO-3166 2-letter country code, 2 characters
		9 (J):	cc2               : alternate country codes, comma separated, ISO-3166 2-letter country code, 60 characters
		10 (K):	admin1 code       : fipscode (subject to change to iso code), see exceptions below, see file admin1Codes.txt for display names of this code; varchar(20)
		11 (L):	admin2 code       : code for the second administrative division, a county in the US, see file admin2Codes.txt; varchar(80) 
		12 (M):	admin3 code       : code for third level administrative division, varchar(20)
		13 (N):	admin4 code       : code for fourth level administrative division, varchar(20)
		14 (O):	population        : bigint (8 byte int) 
		15 (P):	elevation         : in meters, integer
		16 (Q):	dem               : digital elevation model, srtm3 or gtopo30, average elevation of 3''x3'' (ca 90mx90m) or 30''x30'' (ca 900mx900m) area in meters, integer. srtm processed by cgiar/ciat.
		17 (R):	timezone          : the timezone id (see file timeZone.txt) varchar(40)
		18 (S):	modification date : date of last modification in yyyy-MM-dd format
		 */
		
		System.out.println("Loading geonames cities....");
		
		this.geonames = new HashMap<Integer, JSONObject>();
		this.geonamesIndex = new HashMap<String, Integer>();
		this.geonamesCityCountryIndex = new HashMap<String, Integer>();
		this.geonamesCityStateCountryIndex = new HashMap<String, Integer>();
	
	    
	    try{
	       	String line = "";
	       	BufferedReader reader = new BufferedReader(new InputStreamReader( GeotagScorer.class.getResourceAsStream("/cities5000.txt")));
	       	line = reader.readLine();
	       	while(line!=null){
	       		String geoname[] = line.split("\t");
	       		if(geoname != null && geoname.length == 19){
	       			Integer id = new Integer(Integer.parseInt(geoname[0]));
	       			String name = geoname[1];
	       			String[] alternativeNames = geoname[3].split(",");
	       			String latitude = geoname[4];
	       			String longitude = geoname[5];
	       			String country = geoname[8];
	       			String state = geoname[10];
	       			int population = Integer.parseInt(geoname[14]);
	       			String timezone = geoname[17];
	       			String continent = null;
	       			if(timezone != null && timezone.indexOf("/")>0){
	       				continent = timezone.split("/")[0].trim().toLowerCase();
	       			}
	       			
	    			JSONObject location = new JSONObject();
	    			location.put("name", name);
	    			location.put("latitude", Double.parseDouble(latitude));
	    			location.put("longitude", Double.parseDouble(longitude));
	    			location.put("country", country);
	    			if(state != null){
	    				location.put("state", state);
	    			}
	    			location.put("population", population);
	    			if(continent != null){
	    				location.put("continent", continent);
	    			}
	    			
	    			this.addLocation(id, alternativeNames, location);
	       		}
	       				
	       		line = reader.readLine();
	       	}
	       	
	       	reader.close();
	       	System.out.println("Loaded " + this.geonames.size() + " locations");
	    }
	    catch(Exception e){
	    	e.printStackTrace();
	    }
	}
	
	
	private void loadLocationsBefore(){
		this.locationsBefore = new HashMap<String, String>();
		
		 try{
			 String line = "";
		     BufferedReader reader = new BufferedReader(new InputStreamReader( GeotagScorer.class.getResourceAsStream("/locations_before.txt")));
		     line = reader.readLine();
		     while(line!=null){
		    	 if(line.length() > 0){
		    		 String splits[] = line.split("\t");
		    		 if(splits != null && splits.length > 1){
		    			 this.locationsBefore.put(splits[0].trim().toLowerCase(), splits[1].trim().toLowerCase());
		    		 }
		    	 }
		    	 line = reader.readLine();
		     }
		     reader.close();
		 }
		 catch(Exception e){
			 e.printStackTrace();
		 }
	}
	
	
	private void addLocation(Integer id, String alternateNames[], JSONObject location){
		
		// add location
		this.geonames.put(id, location);
		
		// create alternate names index
		ArrayList<String> allNames =  new ArrayList<String>(Arrays.asList(alternateNames));
		if(location.has("name")){
			try{
				allNames.add(location.getString("name"));
			}
			catch(Exception e){
				e.printStackTrace();
			}
		}
		
		Iterator<String> namesIterator = allNames.iterator();
		while(namesIterator.hasNext()){
			String name = namesIterator.next();
			name = name.toLowerCase().trim();
				
			if(this.geonamesIndex.containsKey(name)){
				// replace if the existing location has a smaller population
				try{
					JSONObject existingLocation = this.geonames.get(this.geonamesIndex.get(name));
					
					if(existingLocation.has("population") && location.has("population")){
						if(location.getInt("population") > existingLocation.getInt("population")){
							this.geonamesIndex.put(name, id);
						}
					}
				}
				catch(Exception e){
					e.printStackTrace();
				}
			}
			else{
				this.geonamesIndex.put(name, id);
			}
		}
		
		// create city/countries index
		if(location.has("country")){
			try{
				JSONObject countryObject = this.countries.get(location.getString("country"));
				if(countryObject != null){
					String cityCountyKey = this.generateCityCountryKey(location.getString("name"), countryObject.getString("name"));
					if(cityCountyKey != null){
						this.geonamesCityCountryIndex.put(cityCountyKey, id);
					}
				}
			}
			catch(Exception e){
				e.printStackTrace();
			}
		}
		
		// create city/state/country index
		if(location.has("country") && location.has("state")){
			try{
				JSONObject countryObject = this.countries.get(location.getString("country"));
				if(countryObject != null){
					String cityStateCountyKey = this.generateCityStateCountryKey(location.getString("name"), location.getString("state"), countryObject.getString("name"));
					if(cityStateCountyKey != null){
						this.geonamesCityStateCountryIndex.put(cityStateCountyKey, id);
					}
				}
			}
			catch(Exception e){
				e.printStackTrace();
			}
		}
		
		
	}
	
	private String generateCityCountryKey(String city, String country){
		String cityCountryKey = null;
		if(city != null && city.length() > 0 && country != null && country.length() > 0){
			cityCountryKey = city.trim().toLowerCase() + "/" + country.trim().toLowerCase();
		}
		return cityCountryKey;
	}
	
	private String generateCityStateCountryKey(String city, String state, String country){
		String cityStateCountryKey = null;
		if(city != null && city.length() > 0 && state != null & state.length() > 0 && country != null && country.length() > 0){
			cityStateCountryKey = city.trim().toLowerCase() + "/" + state.trim().toLowerCase() + "/" + country.trim().toLowerCase();
		}
		return cityStateCountryKey;
	}
	
	
	private JSONObject getLocation(String name){
		JSONObject location = null;
		name = name.toLowerCase().trim();
		
		if(this.geonamesIndex.containsKey(name)){
			location = this.geonames.get(this.geonamesIndex.get(name));
		}
		
		
		return location;
	}
	
	private JSONObject getLocation(String city, String country){
		JSONObject location = null;
		String key = this.generateCityCountryKey(city, country);
		if(key != null){
			Integer id = this.geonamesCityCountryIndex.get(key);
			if(id != null){
				location = this.geonames.get(id);
			}
		}
		
		
		return location;
	}
	
	private JSONObject getLocation(String city, String state, String country){
		JSONObject location = null;
		String key = this.generateCityStateCountryKey(city, state, country);
		if(key != null){
			Integer id = this.geonamesCityStateCountryIndex.get(key);
			if(id != null){
				location = this.geonames.get(id);
			}
		}
		
		
		return location;
	}

	@Override
	protected void finalize() throws Throwable {
		super.finalize();
	}
	
	public String getCountryNameEn(String countryCode){
		if(this.countries != null){
			JSONObject countryObject = this.countries.get(countryCode);
			if(countryObject != null){
				try{
					return countryObject.getString("name");
				}
				catch(Exception e){
					e.printStackTrace();
				}
			}
		}
		
		return null;
		
	}

	
}
