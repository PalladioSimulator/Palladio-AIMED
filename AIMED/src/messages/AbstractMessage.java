package messages;

/**
 * This class is the base class of the messages pushed to an oberserver.
 * @author Marcel Müller
 *
 */
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
