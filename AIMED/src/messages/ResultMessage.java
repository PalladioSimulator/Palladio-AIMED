package messages;

import java.util.List;

public class ResultMessage {	
	List<String> results;
	
	public ResultMessage(List<String> results) {
		this.results = results;
	}
	
	public List<String> getResuls() {
		return results;
	}

}
