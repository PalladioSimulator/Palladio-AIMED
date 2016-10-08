package messages;

/**
 * This class holds the measurement state and the message to inform the observer.
 * @author Marcel Müller
 *
 */
public class MeasurementStateMessage extends AbstractMessage {
	private MeasurementState state = MeasurementState.UNDEFINED;
	
	public MeasurementStateMessage(MeasurementState state, String message) {
		super(message);
		this.state = state;
	}
	
	public MeasurementState getMeasurementState() {
		return this.state;
	}	
}
