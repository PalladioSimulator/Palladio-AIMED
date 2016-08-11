package messages;

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
