package com.san.nyc.manager;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import com.san.nyc.taxidata.ListBucketResult;

public class DownloadManager {

	private final String AWS_REPO_FILE_NAME = "/META-INF/aws_nyc_tlc.xml";
	//private final String AWS_REPO_FILE_NAME = "C:\\tmp\\workspace\\nyc-taxi-trip\\src\\main\\resources\\META-INF\\aws_nyc_tlc.xml";
	
	private final static String ONE_SPACE = " ";

	// get the key from ListBucketResult\Content
	private List<ListBucketResult.Contents> getAWSRepoFileFromLocal() {
		InputStream inputStream = null;
		ListBucketResult bucketResult;
		List<ListBucketResult.Contents> contentsList = new ArrayList<ListBucketResult.Contents>();
		try {
			//File file = new File(AWS_REPO_FILE_NAME);
			inputStream = getClass().getResourceAsStream(AWS_REPO_FILE_NAME); 
		    if (inputStream != null && inputStream.available() > 0){
			//if(file.exists()){
				JAXBContext jaxbContext = JAXBContext.newInstance(ListBucketResult.class);
				Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
				//bucketResult = (ListBucketResult) jaxbUnmarshaller.unmarshal(file);
				bucketResult = (ListBucketResult) jaxbUnmarshaller.unmarshal(inputStream);
				contentsList = bucketResult.getContents();
		    }
		    else{
		    	System.err.println("File Loading Issue : file does not exist. ");
		    	System.exit(0);
		    }
		} catch (JAXBException | IOException e ) {
			System.err.println("REPO File Issue : " + e.getMessage());
		}finally{
			if (inputStream != null) {
				try {
					inputStream.close();
				} catch (IOException e) {
					System.out.println(e.getMessage());
				}
			}
		}
		return contentsList;
	}
	// extract the eTag and file name
	// put the eTag in hashmap and key as value
	private Map<String,String> getKVStore(){
		Map<String, String> KVStore = new HashMap<String,String>();
		List<ListBucketResult.Contents> contentsList = getAWSRepoFileFromLocal();
		for(ListBucketResult.Contents contents : contentsList){
			if(contents.getSize() > 0){
				KVStore.put(contents.getETag().replaceAll("\"", " ").trim(), concatinateValue(contents.getKey()));				
			}
		}
		//System.err.println(KVStore);
		return KVStore;
	}
	// if key contains " " , replace it with +
	private String concatinateValue(String value){
		StringBuffer rtnVal = new StringBuffer("/");
		final int idx = value.indexOf(ONE_SPACE);
		if( idx > 0){
			rtnVal.append(value.substring(0, idx));
			rtnVal.append("+");
			rtnVal.append(value.substring(idx+1));
		}else{
			rtnVal.append(value);
		}
		return rtnVal.toString();
	}
	// span a thread for each of the key and value and submit to the executor service
	// download each of the file synchronously
	private void callTheService() throws InterruptedException, ExecutionException{
		final Map<String, String> KVStore = getKVStore();
		System.out.println(" Time to download " + KVStore.size() + " files");
		ExecutorService executorService = Executors.newFixedThreadPool(KVStore.size() % 10 );
		List<Future<String>> futureList = new ArrayList<Future<String>>();
		for(String mapKey : KVStore.keySet()){
			final Callable<String> callable = new FileDownloadUtil(KVStore.get(mapKey));
			Future<String> future = executorService.submit(callable);
			futureList.add(future);	
		}
		for (Future<String> submittedfuture : futureList) {
			System.out.println("Thread feedback at " + new Date() + " ######## " + submittedfuture.get());
		}
		executorService.shutdown();
	}
	public static void main(String[] args) throws InterruptedException, ExecutionException{
		new DownloadManager().callTheService();
	}

}
