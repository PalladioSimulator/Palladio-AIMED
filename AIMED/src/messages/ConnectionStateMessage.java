package messages;

/**
 * This class notifies the oberserver about the current connection state.
 * @author Marcel Müller
 *
 */
public class ConnectionStateMessage extends AbstractMessage{
	public ConnectionStateMessage(String message) {
		super(message);
	}	
}
