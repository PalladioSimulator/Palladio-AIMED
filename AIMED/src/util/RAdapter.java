package util;

import java.io.File;
import java.util.MissingResourceException;
import java.util.Set;

import javax.measure.quantity.Duration;

import org.jscience.physics.amount.Amount;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

/**
 * This class handles communication with Rserve to calculate the doublePDF string.
 * @author Marcel Müller
 *
 */
public class RAdapter {
	RConnection connection = null;
	boolean dhistLoaded = false;
	public RAdapter() {
	}
	
	public boolean connect() throws RserveException {
		return connect("localhost", 6311);
	}
	
	public boolean connect(String host, int port) throws RserveException {
		if (connection != null) {
			connection.close();
		}
		connection = new RConnection(host, port);
		return connection.isConnected();
	}
	
	public void disconnect() {
		if (connection != null) {
			connection.close();	
		}
	}
	
	public boolean isConnected() {
		if (connection == null) {
			return false;
		}
		return connection.isConnected();
	}
	
	/**
	 * Loads the default file from resource folder.
	 */
	public void loadDefaultDhistSource() {
		File f = new File("resources/dhist.r");
		loadDhistSource(f.getAbsolutePath());
	}
	
	/**
	 * This function triggers Rserve to load the file that contains the functionality 
	 * to create a histogram and the doublePDF function.
	 * @param dhistFilePath The path of the file.
	 */
	public void loadDhistSource(String dhistFilePath) {
		dhistFilePath = dhistFilePath.replaceAll("\\\\", "/");
		try {
			connection.eval("source(file=\"" + dhistFilePath + "\")");
		} catch (RserveException e) {
			e.printStackTrace();
		}
		dhistLoaded = true;
	}
		
	public String doublePDF (Set<Amount<Duration>> resourceDemands) {
		if (!dhistLoaded) {
			throw new MissingResourceException("Resource to run doublePDF is not laoded!", "dhist.r", "");
		}
		String vector = "";
		String result = "";
		for (Amount<Duration> demand : resourceDemands) {
			long value = demand.getExactValue();
			if (value >= 0) {
				vector += String.valueOf(value) + ",";				
			}
		}
		if (vector.isEmpty()) {
			try {
				throw new Exception("No resource demands to calculate.");
			} catch (Exception e) {
				e.printStackTrace();
				return "";
			}
		}
		if (vector.endsWith(",")) {
			vector = vector.substring(0, vector.length() -1);
		}
		try {
			connection.eval("myvector <- c(" + vector + ")");
			connection.eval("result <- doublePDF(myvector)");
			result = connection.eval("result").asString();
			System.out.println(result);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}
	
	
	
	
}
