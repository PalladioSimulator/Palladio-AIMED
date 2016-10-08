package ui;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.extension.IExtension;
import org.spotter.core.workload.AbstractWorkloadAdapter;

import aimed.AimedMainController;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import messages.ConnectionStateMessage;
import messages.MeasurementState;
import messages.MeasurementStateMessage;
import messages.ResultMessage;
import util.Config;

/**
 * This class cointains all direct functionality of the GUI.
 * @author Marcel Müller
 *
 */
public class GuiController implements Initializable, Observer {
	@FXML
	private Accordion accordion;

	@FXML
	private TitledPane pane1, pane2, pane3, pane4, pane5;

	@FXML
	private TextField aimHostTextField, aimPortTextField, rserveHostTextField, rservePortTextField, warmupDuration,
			measurementDuration, resourcePathTextField;

	@FXML
	private Label labelConnectState, labelMeasurementState;

	@FXML
	private Button aimConnectButton, rserveConnectButton, runMeasurementButton, resourceSelectButton,
			resourceLoadButton, resourceSelectAllButton, resourceDeselectAllButton;

	@FXML
	private WebView webViewResults;

	@FXML
	private ComboBox<String> selectAvailableAdapterBox;

	@FXML
	private VBox seffMethodsVBox, workloadAdapterConfigVBox;

	/**
	 * Contains the current connections state message.
	 */
	private StringProperty connectionState = new SimpleStringProperty("Please connect.");

	/**
	 * Contains the current measurement state message.
	 */
	private StringProperty measurementState = new SimpleStringProperty();

	private final static Config config = Config.getInstance();

	private AimedMainController aimed = AimedMainController.getInstance();

	/**
	 * Initializes the GUI and adds event listeners to all required GUI
	 * elements.
	 */
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		aimed.addObserver(this);
		loadConfig();
		addConfigListener();
		accordion.setExpandedPane(pane1);
		aimConnectButton.setOnMouseClicked(me -> {
			onAimConnectButtonClicked();
		});
		rserveConnectButton.setOnMouseClicked(me -> {
			onRserveConnectButtonClicked();
		});
		labelConnectState.textProperty().bind(connectionState);
		runMeasurementButton.setDisable(true);
		runMeasurementButton.setOnMouseClicked(me -> {
			aimed.setWorkloadAdapter(getSelectedWorkloadAdapter(), getWorkloadAdapterProperties());
			aimed.startMeasurement(getWarmupDuration(), getMeasurementDuration(), getSelectedMethods());
		});
		labelMeasurementState.textProperty().bind(measurementState);
		selectAvailableAdapterBox.valueProperty().addListener((arg0, arg1, arg2) -> {
			onLoadWorkloadAdapterClicked();
		});
		resourceSelectButton.setOnMouseClicked(me -> {
			onResourceSelectButtonClicked();
		});
		resourceLoadButton.setOnMouseClicked(me -> {
			onResourceLoadButtonClicked();
		});
		// TODO: Remove Event.fireEvent
		if (!resourcePathTextField.getText().trim().isEmpty()) {
			Event.fireEvent(resourceLoadButton, new MouseEvent(MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
					MouseButton.PRIMARY, 1, true, true, true, true, true, true, true, true, true, true, null));
		}
		resourceSelectAllButton.setOnMouseClicked(me -> {
			onSelectAllMethods(true);
		});
		resourceDeselectAllButton.setOnMouseClicked(me -> {
			onSelectAllMethods(false);
		});
		loadWorkloadAdapters();
	}

	/**
	 * Loads the config to set the preconfigured parameters.
	 */
	private void loadConfig() {
		if (config.containsKey("aim.host")) {
			aimHostTextField.setText(config.getProperty("aim.host"));
		}
		if (config.containsKey("aim.port")) {
			aimPortTextField.setText(config.getProperty("aim.port"));
		}
		if (config.containsKey("warmup.duration")) {
			warmupDuration.setText(config.getProperty("warmup.duration"));
		}
		if (config.containsKey("measurement.duration")) {
			measurementDuration.setText(config.getProperty("measurement.duration"));
		}
		if (config.containsKey("sourcecodedecorator.path")) {
			resourcePathTextField.setText(config.getProperty("sourcecodedecorator.path"));
		}
	}

	/**
	 * Changes the config, if values in the GUI has been changed.
	 */
	private void addConfigListener() {
		aimHostTextField.textProperty().addListener((observable, oldValue, newValue) -> {
			config.setProperty("aim.host", newValue);
		});

		aimPortTextField.textProperty().addListener((observable, oldValue, newValue) -> {
			config.setProperty("aim.port", newValue);
		});

		warmupDuration.textProperty().addListener((observable, oldValue, newValue) -> {
			config.setProperty("warmup.duration", newValue);
		});

		measurementDuration.textProperty().addListener((observable, oldValue, newValue) -> {
			config.setProperty("measurement.duration", newValue);
		});

		resourcePathTextField.textProperty().addListener((observable, oldValue, newValue) -> {
			config.setProperty("sourcecodedecorator.path", newValue);
		});
	}

	/**
	 * Loads the workload adapters into a selection box.
	 */
	private void loadWorkloadAdapters() {
		List<IExtension> extensions = aimed.getAvailableWorkloadAdapter();
		ObservableList<String> workloadAdapter = FXCollections.observableArrayList();
		for (IExtension ext : extensions) {
			workloadAdapter.add(ext.getDisplayLabel() + " - " + ext.getName());
		}
		selectAvailableAdapterBox.setItems(workloadAdapter);
		// TODO remove this line - Its for default selection of JMeter Adapter.
		selectAvailableAdapterBox.getSelectionModel().select(0);
	}

	/**
	 * When the selection in the workload adapter box changes, the properties of
	 * the new workload adapter are loaded.
	 */
	private void onLoadWorkloadAdapterClicked() {
		workloadAdapterConfigVBox.getChildren().clear();
		IExtension extension = aimed.getAvailableWorkloadAdapter()
				.get(selectAvailableAdapterBox.getSelectionModel().getSelectedIndex());
		Set<ConfigParameterDescription> configDesc = extension.getConfigParameters();
		TextField text;
		for (ConfigParameterDescription cpd : configDesc) {
			text = new TextField();
			text.setMaxWidth(1.7976931348623157E308);
			text.setPromptText(cpd.getName() + " as " + cpd.getType());
			text.setText(cpd.getDefaultValue());
			// TODO: remove lines
			if (cpd.getName().contains("workload.jmeter.home")) {
				text.setText("C:/Users/Cel/Eclipse/apache-jmeter-2.13");
			}
			if (cpd.getName().contains("workload.jmeter.scenarioFile")) {
				// text.setText("C:/Users/Cel/Studium/Bachelor/Vorbereitung/userVariable.jmx");
				text.setText("C:/Users/Cel/Studium/Bachelor/Vorbereitung/CloudStoreNoSearch.jmx");
			}
			if (cpd.getName().contains("workload.jmeter.logFileFlag")) {
				text.setText("true");
			}
			// TODO: until here
			text.setTooltip(new Tooltip(cpd.getName() + " as " + cpd.getType()));
			workloadAdapterConfigVBox.getChildren().add(text);
		}
	}

	/**
	 * @return Returns the current selected workload Adapter.
	 */
	private AbstractWorkloadAdapter getSelectedWorkloadAdapter() {
		AbstractWorkloadAdapter adapter = aimed.getAvailableWorkloadAdapter()
				.get(selectAvailableAdapterBox.getSelectionModel().getSelectedIndex()).createExtensionArtifact();
		return adapter;
	}

	/**
	 * @return Returns the properties of the workload adapter.
	 */
	private Properties getWorkloadAdapterProperties() {
		Properties result = new Properties();
		ObservableList<Node> nodes = workloadAdapterConfigVBox.getChildren();
		IExtension extension = aimed.getAvailableWorkloadAdapter()
				.get(selectAvailableAdapterBox.getSelectionModel().getSelectedIndex());
		Set<ConfigParameterDescription> configDesc = extension.getConfigParameters();
		TextField text;
		for (ConfigParameterDescription cpd : configDesc) {
			for (Node node : nodes) {
				if (node.getClass().isAssignableFrom(TextField.class)) {
					text = (TextField) node;
					if (text.getPromptText().contains(cpd.getName())) {
						result.put(cpd.getName(), text.getText());
					}
				}
			}
		}
		return result;
	}

	@Override
	public void update(Observable arg0, Object arg1) {
		if (arg0 == aimed) {
			if (arg1 instanceof MeasurementStateMessage) {
				onMeasurementStateMessage((MeasurementStateMessage) arg1);
			}
			if (arg1 instanceof ConnectionStateMessage) {
				onConnectionStateMessage((ConnectionStateMessage) arg1);
			}
			if (arg1 instanceof ResultMessage) {
				onResultMessage((ResultMessage) arg1);
			}
		}
	}

	/**
	 * This function handles the functionality to connect to AIM.
	 */
	private void onAimConnectButtonClicked() {
		if (!aimed.isConnectedToAIM()) {
			String host = aimHostTextField.getText().trim();
			String port = aimPortTextField.getText().trim();
			setConnectStateText("Connecting to AIM ...");
			setAIMHostPortDisable(true);
			aimed.connectToAIM(host, port);
			if (!aimed.isConnectedToAIM()) {
				setConnectStateText(String.format("Can't connect to AIM on %s:%s.", host, port));
				setAIMHostPortDisable(false);
			} else {
				setConnectStateText("Connected to AIM.");
				checkStartMeasurementButtonDisable();
				setAIMConnectButtonText("Disconnect from AIM");
			}
		} else {
			aimed.disconnectFromAIM();
			setAIMConnectButtonText("Connect to AIM");
			setAIMHostPortDisable(false);
			setConnectStateText("Disconnected from AIM.");
			checkStartMeasurementButtonDisable();
		}
	}

	/**
	 * This function handles the functionality to connect to the Rserve server.
	 */
	private void onRserveConnectButtonClicked() {
		if (!aimed.isConnectedToRserve()) {
			String host = rserveHostTextField.getText().trim();
			String port = rservePortTextField.getText().trim();
			setConnectStateText("Connecting to Rserve ...");
			setRserveHostPortDisable(true);
			aimed.connectToRserve(host, port);
			if (!aimed.isConnectedToRserve()) {
				setConnectStateText(String.format("Can't connect to Rserve on %s:%s.", host, port));
				setRserveHostPortDisable(false);
			} else {
				setConnectStateText("Connected to Rserve.");
				checkStartMeasurementButtonDisable();
				setRserveConnectButtonText("Disconnect from Rserve");
			}
		} else {
			aimed.disconnectFromRserve();
			setRserveConnectButtonText("Connect to Rserve");
			setRserveHostPortDisable(false);
			setConnectStateText("Disconnected from Rserve.");
			checkStartMeasurementButtonDisable();
		}
	}

	private void setConnectStateText(String text) {
		if (text.isEmpty() || text == null) {
			return;
		}
		connectionState.set(text);
	}

	private void setAIMHostPortDisable(boolean disabled) {
		aimHostTextField.setDisable(disabled);
		aimPortTextField.setDisable(disabled);
	}

	private void setRserveHostPortDisable(boolean disabled) {
		rserveHostTextField.setDisable(disabled);
		rservePortTextField.setDisable(disabled);
	}

	private void setConnectButtonDisable(boolean disabled) {
		aimConnectButton.setDisable(disabled);
		rserveConnectButton.setDisable(disabled);
	}

	private void setAIMConnectButtonText(String text) {
		Platform.runLater((Runnable) () -> {
			aimConnectButton.setText(text);
		});
	}

	private void setRserveConnectButtonText(String text) {
		Platform.runLater((Runnable) () -> {
			rserveConnectButton.setText(text);
		});
	}

	/**
	 * This function enables to start the measurements only if AIMED is
	 * connected to Rserve and AIM.
	 */
	private void checkStartMeasurementButtonDisable() {
		if (aimed.isConnectedToAIM() && aimed.isConnectedToRserve()) {
			runMeasurementButton.setDisable(false);
		} else {
			runMeasurementButton.setDisable(true);
		}
	}

	private void setConfigDisable(boolean disabled) {
		warmupDuration.setDisable(disabled);
		measurementDuration.setDisable(disabled);
	}

	private void setMeasurementStateLabelText(String text) {
		if (text.isEmpty() || text == null) {
			return;
		}
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				measurementState.set(text);
			}
		});
	}

	private int getWarmupDuration() {
		return Integer.parseInt(warmupDuration.getText().trim());
	}

	private int getMeasurementDuration() {
		return Integer.parseInt(measurementDuration.getText().trim());
	}

	private void setResult(List<String> resultLines) {
		String text = "";
		text += "<ul>";
		for (String result : resultLines) {
			text += "<li>" + result + "</li>";
		}
		text += "</ul>";
		final String resultText = text;
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				webViewResults.getEngine().loadContent(resultText);
			}
		});
	}

	private void onResultMessage(ResultMessage result) {
		setResult(result.getResuls());
	}

	private void onConnectionStateMessage(ConnectionStateMessage message) {
		setConnectStateText(message.getMessage());
	}

	private void onMeasurementStateMessage(MeasurementStateMessage message) {
		if (message.getMeasurementState() == MeasurementState.STARTING_MEASUREMENT) {
			setSelectMethodsDisable(true);
			setSelectWorkloadDisable(true);
			setConfigDisable(true);
			runMeasurementButton.setDisable(true);
			setConnectButtonDisable(true);
		}
		if (message.getMeasurementState() == MeasurementState.STOPPING_MEASUREMENT) {
			setSelectMethodsDisable(false);
			setSelectWorkloadDisable(false);
			setConfigDisable(false);
			runMeasurementButton.setDisable(false);
			setConnectButtonDisable(false);
		}
		setMeasurementStateLabelText(message.getMessage());
	}

	private void setSelectWorkloadDisable(boolean disable) {
		selectAvailableAdapterBox.setDisable(disable);
		workloadAdapterConfigVBox.setDisable(disable);
	}

	private void setSelectMethodsDisable(boolean disable) {
		resourcePathTextField.setDisable(disable);
		resourceSelectButton.setDisable(disable);
		resourceLoadButton.setDisable(disable);
		seffMethodsVBox.setDisable(disable);
		resourceSelectAllButton.setDisable(disable);
		resourceDeselectAllButton.setDisable(disable);
	}

	/**
	 * Writes the path of the resource to the text field.
	 */
	private void onResourceSelectButtonClicked() {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Select Sourcecode Decorator file of your project.");
		FileChooser.ExtensionFilter exFilter = new FileChooser.ExtensionFilter("Sourcecode decorator files",
				"(*.sourcecodedecorator)", "*.sourcecodedecorator");
		fileChooser.getExtensionFilters().add(exFilter);
		File selectedFile = fileChooser.showOpenDialog(resourceSelectButton.getScene().getWindow());
		if (selectedFile != null) {
			resourcePathTextField.setText(selectedFile.getPath());
		}
	}

	/**
	 * Loads the resource, which path is in the text field.
	 */
	private void onResourceLoadButtonClicked() {
		seffMethodsVBox.getChildren().clear();
		aimed.loadResources(resourcePathTextField.getText().trim());
		List<String> methods = aimed.getSeffMethodNames();
		CheckBox checkBox;
		for (String method : methods) {
			checkBox = new CheckBox();
			checkBox.setText(method);
			// TODO: Remove if construct
			if (method.contains("doX") || method.contains("getBook")) {
				checkBox.setSelected(true);
			}
			seffMethodsVBox.getChildren().add(checkBox);
		}
	}

	private void onSelectAllMethods(boolean selectAll) {
		ObservableList<Node> list = seffMethodsVBox.getChildren();
		CheckBox box;
		for (Node node : list) {
			if (node instanceof CheckBox) {
				box = (CheckBox) node;
				box.setSelected(selectAll);
			}
		}
	}

	private List<String> getSelectedMethods() {
		List<String> result = new ArrayList<>();
		ObservableList<Node> list = seffMethodsVBox.getChildren();
		CheckBox box;
		for (Node node : list) {
			if (node instanceof CheckBox) {
				box = (CheckBox) node;
				if (box.isSelected()) {
					result.add(box.getText());
				}
			}
		}
		return result;
	}
}
