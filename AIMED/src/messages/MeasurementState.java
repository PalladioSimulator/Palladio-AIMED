package messages;

public enum MeasurementState {
	UNDEFINED,
	STARTING,//The first message of a set of measurements.
	RUNNING, //Message between start and end.
	STOPPING, //The last message in a complete measurement.
	CALCULATING
}
