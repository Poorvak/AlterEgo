package org.cl.nm417;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

import org.cl.nm417.data.Document;
import org.cl.nm417.data.Profile;
import org.cl.nm417.data.Unigram;
import org.cl.nm417.extraction.UserProfile;
import org.cl.nm417.google.GoogleRerank;
import org.cl.nm417.google.GoogleResult;
import org.cl.nm417.google.GoogleSearch;
import org.cl.nm417.xmlparser.DataParser;

public class AlterEgo {
	
	public static HashMap<String, Object> config = new HashMap<String, Object>();
	
	public static Profile generateProfile(HashMap<String, Object> params) {
		
		double start = new Date().getTime();
		config = params;
		
		Profile profile = processUser((String)config.get("user"));
		if ((Boolean)config.get("takeLog")){
			for (Unigram u: profile.getUnigrams()){
				u.setWeight(Math.log(u.getWeight() + 1));
			}
		}
		writeProfile(profile);
		writeFinalProfile(profile);
		double end = new Date().getTime();
		System.out.println("User profile generated in " + ((end - start) / 1000) + " seconds");
		return profile;
		
	}
	
	public static Profile generateProfile(HashMap<String, Object> params, DataParser data) {
		
		double start = new Date().getTime();
		config = params;
		
		Profile profile = processUserWithData((String)config.get("user"), data);
		if ((Boolean)config.get("takeLog")){
			for (Unigram u: profile.getUnigrams()){
				u.setWeight(Math.log(u.getWeight() + 1));
			}
		}
		writeFinalProfile(profile);
		double end = new Date().getTime();
		System.out.println("User profile generated in " + ((end - start) / 1000) + " seconds");
		return profile;
		
	}
	
	public static ArrayList<GoogleResult> SearchGoogle(String query, String user, String profilename, 
			boolean rerank, String method, boolean interleave, String interleaveMethod, 
			boolean lookatrank, boolean umatching, boolean visited, int visitedW){
		Profile profile = new Profile();
		profile.setUserId(user);
		profile.setUnigrams(readFinalUnigrams(user + "/" + profilename));
		profile.setURLs(GoogleRerank.getURLs(user));
		HashMap<String, Unigram> unigrams = new HashMap<String, Unigram>();
		for (Unigram u: profile.getUnigrams()){
			unigrams.put(u.getText().toLowerCase(), u);
		}
		ArrayList<GoogleResult> results = GoogleSearch.doGoogleSearch(query);
		if (rerank){
			if (method.equals("lm")){
				double totalWords = calculateTotalWords(profile);
				profile = calculateLMStatistics(profile, totalWords);
				results = GoogleRerank.applyLM(profile, unigrams, results, interleave, interleaveMethod, lookatrank, totalWords, visited, visitedW);
			} else if (method.equals("pclick")){
				results = GoogleRerank.pClick(query, profile, results, interleave, interleaveMethod, lookatrank, umatching, visited, visitedW);
			} else {
				results = GoogleRerank.findCommonalities(profile, results, interleave, interleaveMethod, lookatrank, umatching, visited, visitedW);
			}
		}
		return results;
	}
	
	public static ArrayList<GoogleResult> finalSearchGoogle(String query, Profile profile,
		String method, boolean interleave, String interleaveMethod, 
		boolean lookatrank, boolean visited, int visitedW, ArrayList<GoogleResult> fResults){
			ArrayList<GoogleResult> results = new ArrayList<GoogleResult>();
			for (GoogleResult res: fResults){
				res.setNewWeight(0);
				results.add(res);
			}
			results = GoogleRerank.doSort(results);
			HashMap<String, Unigram> unigrams = new HashMap<String, Unigram>();
			for (Unigram u: profile.getUnigrams()){
				unigrams.put(u.getText().toLowerCase(), u);
			}
			if (method.equals("lm")){
				double totalWords = calculateTotalWords(profile);
				profile = calculateLMStatistics(profile, totalWords);
				results = GoogleRerank.applyLM(profile, unigrams,results, interleave, interleaveMethod, lookatrank, totalWords, visited, visitedW);
			} else if (method.equals("pclick")){
				results = GoogleRerank.pClick(query, profile, results, interleave, interleaveMethod, lookatrank, false, visited, visitedW);
			} else if (method.equals("umatching")) {
				results = GoogleRerank.findCommonalities(profile, results, interleave, interleaveMethod, lookatrank, true, visited, visitedW);
			} else {
				results = GoogleRerank.findCommonalities(profile, results, interleave, interleaveMethod, lookatrank, false, visited, visitedW);
			}
			return results;
	}

	private static double calculateTotalWords(Profile profile){
		double totalWords = 0;
		for (Unigram u: profile.getUnigrams()){
			totalWords += u.getWeight();
		}
		return totalWords;
	}
	
	private static Profile calculateLMStatistics(Profile profile, double totalWords) {
		Profile newProf = new Profile();
		newProf.setURLs(profile.getURLs());
		newProf.setUserId(profile.getUserId());
		newProf.setDocuments(profile.getDocuments());
		newProf.setUnigrams(new ArrayList<Unigram>());
		for (Unigram u: profile.getUnigrams()){
			Unigram newU = new Unigram();
			newU.setWeight((1 + u.getWeight()) / totalWords);
			newU.setText(u.getText());
			newProf.getUnigrams().add(newU);
		}
		return newProf;
	}

	public static ArrayList<Unigram> readFinalUnigrams(String path){
		ArrayList<Unigram> unigrams = new ArrayList<Unigram>();
		DataInputStream in = null;
		try{
		    // Open the file that is the first 
		    // command line parameter
			FileInputStream fstream = new FileInputStream("/Users/nicolaas/Desktop/AlterEgo/dataprocessing/data/profiles/" + path);
		    // Get the object of DataInputStream
		    in = new DataInputStream(fstream);
		    BufferedReader br = new BufferedReader(new InputStreamReader(in));
		    String strLine;
		    //Read File Line By Line
		    while ((strLine = br.readLine()) != null)   {
		      // Print the content on the console
		      if (!strLine.equals("")){
		    	  Unigram u = new Unigram();
		    	  u.setWeight(Double.parseDouble(strLine.split("=>")[0].trim()));
		    	  u.setText(strLine.split("=>")[1].trim());
		    	  unigrams.add(u);
		      }
		    }
		}catch (Exception e){//Catch exception if any
		}finally {
			 //Close the input stream
			if (in != null){
			    try {
					in.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					System.err.println("Error: " + e.getMessage());
				}
			}
		}
		return unigrams;
	}
	
	private static void writeProfile(Profile profile){
		
		try {
		    FileWriter fstream = new FileWriter("/Users/nicolaas/Desktop/AlterEgo/dataprocessing/data/profiles/" + profile.getUserId() + ".txt");
		    BufferedWriter out = new BufferedWriter(fstream);
		    for (Unigram u: profile.getUnigrams()){
		    	out.write(u.getWeight() + " => " + u.getText() + "\n");
		    }
		    out.close();
		} catch (Exception e){
			System.err.println("Error: " + e.getMessage());
		}
		
	}
	
	private static void writeFinalProfile(Profile profile){
		
		try {
			String extention = "";
			
			// Weighting
			String weighting = (String)config.get("weighting");
			if (weighting.equals("tf")){
				extention += "_t";
			} else if (weighting.equals("tfidf")){
				extention += "_ti";
			} else if (weighting.equals("bm25")){
				extention += "_b";
			}
			
			// Title
			boolean useRelative = (Boolean)config.get("useRelativeW");
			boolean title = (Boolean)config.get("title");
			if (title && useRelative){
				extention += "_r";
			} else if (title){
				extention += "_y";
			} else {
				extention += "_n";
			}
			
			// Meta description
			boolean md = (Boolean)config.get("metadescription");
			if (md && useRelative){
				extention += "_r";
			} else if (md){
				extention += "_y";
			} else {
				extention += "_n";
			}
			
			// Meta keywords
			boolean mk = (Boolean)config.get("metakeywords");
			if (mk && useRelative){
				extention += "_r";
			} else if (mk){
				extention += "_y";
			} else {
				extention += "_n";
			}
			
			// Plain text
			boolean pt = (Boolean)config.get("plaintext");
			if (pt && useRelative){
				extention += "_r";
			} else if (pt){
				extention += "_y";
			} else {
				extention += "_n";
			}
			
			// Terms
			boolean t = (Boolean)config.get("terms");
			if (t && useRelative){
				extention += "_r";
			} else if (t){
				extention += "_y";
			} else {
				extention += "_n";
			}
			
			// C&C Parsed
			boolean cc = (Boolean)config.get("ccparse");
			if (cc && useRelative){
				extention += "_r";
			} else if (cc){
				extention += "_y";
			} else {
				extention += "_n";
			}
			
			// Filtering
			boolean allPos = (Boolean)config.get("posAll");
			boolean nGram = (Boolean)config.get("googlengram");
			boolean posNoun = (Boolean)config.get("posNoun");
			if (nGram){
				extention += "_g";
			} else if (posNoun){
				extention += "_wn";
			} else if (allPos){
				extention += "_n";
			}
			
			// Exclude duplicate pages
			boolean excludeDuplicate = (Boolean)config.get("excludeDuplicate");
			if (excludeDuplicate){
				extention += "_y";
			} else {
				extention += "_n";
			}
			
		    FileWriter fstream = new FileWriter("/Users/nicolaas/Desktop/AlterEgo/dataprocessing/data/profiles/" + profile.getUserId() + "/" + profile.getUserId() + extention + ".txt");
		    BufferedWriter out = new BufferedWriter(fstream);
		    for (Unigram u: profile.getUnigrams()){
		    	out.write(u.getWeight() + " => " + u.getText() + "\n");
		    }
		    out.close();
		} catch (Exception e){
			System.err.println("Error: " + e.getMessage());
		}
		
	}
	
	public static DataParser getDataParser(String userid){
		System.out.println(userid);
		DataParser data = new DataParser(userid);
		return data;
	}
	
	private static Profile processUserWithData(String userid, DataParser data){
		
		Profile profile = new Profile();
		profile.setUserId(userid);
		profile.setUnigrams(UserProfile.extractUnigramProfile(data, (Boolean)config.get("plaintext"), 
				(Boolean)config.get("metakeywords"), (Boolean)config.get("metadescription"), 
				(Boolean)config.get("title"), (Boolean)config.get("terms"), (Boolean)config.get("ccparse"),
				(Integer)config.get("plaintextW"), (Integer)config.get("metakeywordsW"), 
				(Integer)config.get("metadescriptionW"), (Integer)config.get("titleW"), (Integer)config.get("termsW"), 
				(Integer)config.get("ccparseW")));
		if (((String)config.get("weighting")).equals("bm25")){
			profile.setUnigrams(UserProfile.extractBM25Profile(data, profile, (Boolean)config.get("plaintext"), 
					(Boolean)config.get("metakeywords"), (Boolean)config.get("metadescription"), 
					(Boolean)config.get("title"), (Boolean)config.get("terms"), (Boolean)config.get("ccparse")));
		}
		writeURLs(userid, data);
		writePClickData(userid, data);
		profile.setDocuments(data.getDocuments().size());
		data = null;
		return profile;
		
	}
	
	private static Profile processUser(String userid){
		
		DataParser data = new DataParser(userid);
		Profile profile = new Profile();
		profile.setUserId(userid);
		profile.setUnigrams(UserProfile.extractUnigramProfile(data, (Boolean)config.get("plaintext"), 
				(Boolean)config.get("metakeywords"), (Boolean)config.get("metadescription"), 
				(Boolean)config.get("title"), (Boolean)config.get("terms"), (Boolean)config.get("ccparse"),
				(Integer)config.get("plaintextW"), (Integer)config.get("metakeywordsW"), 
				(Integer)config.get("metadescriptionW"), (Integer)config.get("titleW"), (Integer)config.get("termsW"), 
				(Integer)config.get("ccparseW")));
		if (((String)config.get("weighting")).equals("bm25")){
			profile.setUnigrams(UserProfile.extractBM25Profile(data, profile, (Boolean)config.get("plaintext"), 
					(Boolean)config.get("metakeywords"), (Boolean)config.get("metadescription"), 
					(Boolean)config.get("title"), (Boolean)config.get("terms"), (Boolean)config.get("ccparse")));
		}
		writeURLs(userid, data);
		writePClickData(userid, data);
		profile.setDocuments(data.getDocuments().size());
		data = null;
		return profile;
		
	}
	
	private static void writeURLs(String userid, DataParser data) {
		 HashMap<String, Integer> url = new HashMap<String, Integer>();
		 try {
			 FileWriter fstream = new FileWriter("/Users/nicolaas/Desktop/AlterEgo/dataprocessing/data/profiles/" + userid + ".url.txt");
			 BufferedWriter out = new BufferedWriter(fstream);
			 for (Document d: data.getDocuments()){
				 if (!url.containsKey(d.getUrl())){
					 url.put(d.getUrl(), 1);
				 } else {
					 url.put(d.getUrl(), url.get(d.getUrl()) + 1);
				 }
			 }
			 for (String s: url.keySet()){
				out.write(s + " => " + url.get(s) + "\n");
			 }
			 out.close();
		 } catch (Exception e){
			 System.err.println("Error: " + e.getMessage());
		 }
	}

	private static void writePClickData(String userid, DataParser data){
		HashMap<String, ArrayList<String>> searches = new HashMap<String, ArrayList<String>>();
		boolean trackNext = false;
    	String prevSearch = "";
    	try {
    		FileWriter fstream = new FileWriter("/Users/nicolaas/Desktop/AlterEgo/dataprocessing/data/profiles/" + userid + ".pclick.txt");
			BufferedWriter out = new BufferedWriter(fstream);
	    	for (Document d: data.getDocuments()){
	    		String url = d.getUrl().toLowerCase();
	    		if (url.contains("www.google") && url.contains("q=")){
	    			String search = url.substring(url.indexOf("q=") + 2);
	    			int index = search.indexOf("&");
	    			if (index == -1){
	    				search = URLDecoder.decode(search.replaceAll("[+]", " "),"utf-8");
	    			} else {
	    				search = URLDecoder.decode(search.substring(0, index).replaceAll("[+]", " "),"utf-8");
	    			}
	    			prevSearch = search;
	    			trackNext = true;
	    			if (!searches.containsKey(search)){
	    				searches.put(search, new ArrayList<String>());
	    			} 
	    		} else if (trackNext){
	    			trackNext = false;
	    			ArrayList<String> arl = searches.get(prevSearch);
	    			arl.add(d.getUrl());
	    			searches.put(prevSearch, arl);
	    		}
	    	}
	    	
	    	for (String s: searches.keySet()){
	    		if (searches.get(s).size() > 0){
		    		out.write(s + "\n");
		    		for (String url: searches.get(s)){
		    			out.write("=> " + url + "\n");
		    		}
	    		}
	    	}
	    	
	    	out.close();
	    	
    	} catch (Exception ex){
    		ex.printStackTrace();
    	}
	}
	
	public static ArrayList<Profile> processSet(String set){
		
		ArrayList<Profile> profiles = new ArrayList<Profile>();
		String toProcess[] = new String[0];
		if (set.equals("development")){
			toProcess = new String[]{"usr_3484406"};
		} else if (set.equals("train")){
			toProcess = new String[]{"usr_2434413", "usr_2543662", "usr_2554801", "usr_261821", "usr_2917559", 
					"usr_406338", "usr_4362753", "usr_4965278", "usr_5945189", "usr_6145747", "usr_6318797", 
			 		"usr_6422669", "usr_6989158", "usr_7318689", "usr_7600860", "usr_8002037", "usr_9533453"};
		} else if (set.equals("test")){
			toProcess = new String[]{"usr_2296654", "usr_2555137", "usr_263177", "usr_3762438", "usr_4130066", 
					"usr_4593969", "usr_5117040", "usr_5136203", "usr_597873", "usr_6150712", "usr_6326553", 
					"usr_6460086", "usr_6921645", "usr_7865799", "usr_8613294", "usr_9918599"};
		}
		for (String s: toProcess){
			Profile profile = processUser(s);
			profiles.add(profile);
		}
		
		return profiles;
		
	}
	
}
