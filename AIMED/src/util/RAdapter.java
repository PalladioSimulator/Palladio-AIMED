package util;

import java.io.File;
import java.util.Set;

import org.rosuda.JRI.Rengine;

public class RAdapter {
	Rengine engine;
	public RAdapter(String dhistFilePath) {
		if (dhistFilePath.isEmpty()) {
			dhistFilePath = System.getProperty("user.dir") + File.separator + "Rscript" + File.separator + "dhist.r";
		}
		engine = new Rengine(new String[] {"--no-save"}, false, null);
		engine.eval("source(file=" + dhistFilePath + ")");
	}
	
	public String doublePDF (String csvFilePath) {
		engine.eval("mydata = read.csv(" + csvFilePath + ", header = FALSE, sep = \";\", dec = \",\")");
		engine.eval("myvector <- mydata$V1");
		String result = engine.eval("doublePDF(myvector)").asString();
		
		return result;
	}
	
	
	
	
}
