package messages;

public abstract class AbstractMessage {
	private String message;
	
	public AbstractMessage(String message) {
		this.message = message;
	}
	
	public void setMessage(String message) {
		this.message = message;
	}
	
	public String getMessage() {
		return message;
	}
}
