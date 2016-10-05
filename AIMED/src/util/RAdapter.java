package util;

import java.io.File;
import java.net.URL;
import java.util.MissingResourceException;
import java.util.Set;

import javax.measure.quantity.Duration;

import org.jscience.physics.amount.Amount;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

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
	
	public void loadDefaultDhistSource() {
		File f = new File("resources/dhist.r");
		loadDhistSource(f.getAbsolutePath());
	}
	
	public void loadDhistSource(String dhistFilePath) {
		dhistFilePath = dhistFilePath.replaceAll("\\\\", "/");
		try {
			connection.eval("source(file=\"" + dhistFilePath + "\")");
		} catch (RserveException e) {
			e.printStackTrace();
		}
		dhistLoaded = true;
	}
	
	public String doublePDF (String csvFilePath) {
		if (!dhistLoaded) {
			throw new MissingResourceException("Resource to run doublePDF is not laoded!", "dhist.r", "");
		}
		csvFilePath = csvFilePath.replaceAll("\\\\", "/");
		String result = "";
		try {
			connection.eval("mydata <- read.csv(\"" + csvFilePath + "\", header = FALSE, sep = \";\", dec = \",\")");
			connection.eval("myvector <- mydata$V1");
			connection.eval("result <- doublePDF(myvector)");
			result = connection.eval("result").asString();
			System.out.println(result);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}
	
	public String doublePDF (Set<Amount<Duration>> resourceDemands) {
		if (!dhistLoaded) {
			throw new MissingResourceException("Resource to run doublePDF is not laoded!", "dhist.r", "");
		}
		String vector = "";
		String result = "";
		for (Amount<Duration> demand : resourceDemands) {
			double test = demand.getExactValue();
			vector += String.valueOf(demand.getExactValue()) + ",";
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
