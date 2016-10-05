package messages;

public enum MeasurementState {
	UNDEFINED,
	STARTING_MEASUREMENT,//The first message of a set of measurements.
	RUNNING, //Message between start and end.
	STOPPING_MEASUREMENT, //The last message in a complete measurement.
	CALCULATING,
	SAVING,
	ALL_FINISHED
}
