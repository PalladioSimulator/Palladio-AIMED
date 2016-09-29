package util;

import java.io.File;
import java.util.Set;

import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

public class RAdapter {
	RConnection connection;
	public RAdapter() {
	}
	
	public boolean connect() {
		return connect("localhost", 6311);
	}
	
	public boolean connect(String host, int port) {
		try {
			connection = new RConnection(host, port);
			return connection.isConnected();
		} catch (RserveException e) {
			return false;
		}
	}
	
	public void disconnect() {
		connection.close();
	}
	
	public boolean isConnected() {
		return connection.isConnected();
	}
	
	public void loadSource() {
		loadSource(System.getProperty("user.dir") + File.separator + "Rscript" + File.separator + "dhist.r");
	}
	
	public void loadSource(String dhistFilePath) {
		dhistFilePath = dhistFilePath.replaceAll("\\\\", "/");
		try {
			connection.eval("source(file=\"" + dhistFilePath + "\")");
		} catch (RserveException e) {
			e.printStackTrace();
		}
	}
	
	public String doublePDF (String csvFilePath) {
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
	
	public String doublePDF (Set<Long> resourceDemands) {
		String vector = "";
		String result = "";
		for (Long demand : resourceDemands) {
			vector += String.valueOf(demand) + ",";
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
