package com.san.nyc.manager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Callable;

public class FileDownloadUtil implements Callable<String> {

	private String fileName;
	// Max size of download buffer.
	private final int MAX_BUFFER_SIZE = 1024;
	// will be appended to the hashmap value
	private final String FIXED_URL = "https://s3.amazonaws.com/nyc-tlc";
	// These are the status names.
	public final String STATUSES[] = { "Downloading", "Paused", "Complete", "Cancelled", "Error" };

	// These are the status codes.
	public final int DOWNLOADING = 0;
	public final int PAUSED = 1;
	public final int COMPLETE = 2;
	public final int CANCELLED = 3;
	public final int ERROR = 4;

	private URL url; // download URL
	private int size; // size of download in bytes
	private int status; // current status of download
	
	public FileDownloadUtil(String fileName) {
		this.fileName = fileName;
	}

	public String call() throws Exception {
		// form the url
		StringBuffer buffer = new StringBuffer(FIXED_URL);
		buffer.append(this.fileName);
		setUrl(new URL(buffer.toString()));
		downLoadFile();
		buffer = null;
		return "fileName" +  this.fileName + " with status : "+ STATUSES[getStatus()];
	}

	public URL getUrl() {
		return this.url;
	}

	public void setUrl(URL url) {
		this.url = url;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public int getStatus() {
		return status;
	}

	public void setStatus(int status) {
		this.status = status;
		error("Watch it fileName :" + this.fileName + " with value " + STATUSES[status]);
	}

	// Mark this download as having an error.
	private void error(String value) {
		setStatus(ERROR);
	}

	// Notify observers that this download's status has changed.
	private void stateChanged(String value) {
		
	}

	// Get file name portion of URL
	private String getFileName() {
		String fileName = this.getUrl().getFile();
		return "C:\\tmp\\aws\\" + fileName.substring(fileName.lastIndexOf('/') + 1);
	}

	private void downLoadFile() {
		OutputStream outputStream = null;
		InputStream inputStream = null;
		try {
			// Open connection to URL.
			HttpURLConnection connection = (HttpURLConnection) getUrl().openConnection();
			connection.connect();
			// Make sure response code is in the 200 range.
			if (connection.getResponseCode() / 100 != 2) {
				error("Connection error");
				setStatus(CANCELLED);
			}
			// Check for valid content length.
			long contentLength = connection.getContentLengthLong();
			if (contentLength < 1) {
				error("content length 0");
				setStatus(CANCELLED);
			}
			setStatus(DOWNLOADING);
			inputStream = connection.getInputStream();
			outputStream = new FileOutputStream(new File(getFileName()));
			int read = 0;
			byte[] bytes = new byte[MAX_BUFFER_SIZE];
			while ((read = inputStream.read(bytes)) != -1) {
				outputStream.write(bytes, 0, read);
			}
		} catch (Exception e) {
			error("Exception " + e.getMessage());
			status = ERROR;
			stateChanged(STATUSES[status]);
		} finally {
			if (inputStream != null) {
				try {
					inputStream.close();
					setStatus(COMPLETE);
				} catch (IOException e) {
					error(e.getMessage());
					setStatus(ERROR);
				}
			}
			if (outputStream != null) {
				try {
					outputStream.close();
				} catch (IOException e) {
					error(e.getMessage());
					setStatus(ERROR);
				}
			}
		}
	}
}
