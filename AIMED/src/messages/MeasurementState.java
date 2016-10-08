package messages;

/**
 * This class cotains the measurement states to control the GUI and to give feedback.
 * @author Marcel Müller
 *
 */
public enum MeasurementState {
	UNDEFINED,
	STARTING_MEASUREMENT,//The first message of a set of measurements.
	RUNNING, //Message between start and end.
	STOPPING_MEASUREMENT, //The last message in a complete measurement.
	CALCULATING,
	SAVING,
	ALL_FINISHED
}
