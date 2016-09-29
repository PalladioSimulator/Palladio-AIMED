package util;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;


public class Config {
	private final static String FILE_PATH = "resources/config.properties";
	private Properties props = new Properties();
	private static Config instance = null;
	
	
	private Config() {
		FileReader reader = null;
		try {
			File configFile = new File(FILE_PATH);
			reader = new FileReader(configFile);
		} catch(FileNotFoundException e) {
			e.printStackTrace();
		}
		try {
			props.load(reader);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
	public static synchronized Config getInstance() {
		if (instance == null) {
			instance = new Config();
		}
		return instance;
	}
	
	public String getProperty(String key) {
		return props.getProperty(key);
	}
	
	public String getProperty(String key, String defaultValue) {
		return props.getProperty(key, defaultValue);
	}
	
	public boolean containsKey(String key) {
		return props.containsKey(key);
	}
	
	public void setProperty(String key, String value) {
		props.setProperty(key, value);
	}
	
	
    public void saveConfig() {
    	synchronized (instance) {
    		FileWriter writer = null;
			try {
				File configFile = new File(FILE_PATH);
				writer = new FileWriter(configFile);
			} catch (IOException e) {
				e.printStackTrace();
			}
			try {
				props.store(writer, "AIMED Configuration");
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (writer != null) {
					try {
						writer.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
    }
}
