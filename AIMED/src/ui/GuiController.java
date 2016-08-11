package ui;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;

import org.lpe.common.config.ConfigParameterDescription;
import org.lpe.common.extension.IExtension;
import org.lpe.common.util.LpeSupportedTypes;
import org.spotter.core.workload.AbstractWorkloadAdapter;

import aimed.AimedMainController;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Accordion;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import messages.ConnectionStateMessage;
import messages.MeasurementState;
import messages.MeasurementStateMessage;
import messages.ResultMessage;

public class GuiController implements Initializable, Observer {	
	@FXML
	private Accordion accordion;
	
	@FXML
	private TitledPane pane1, pane2, pane3, pane4, pane5;
	
	@FXML
	private TextField hostTextField, portTextField, warmupDuration, measurementDuration, xmiPathTextField;
	
	@FXML
	private TextArea methodPatterns;
	
	@FXML
	private Label labelConnectState, labelMeasurementState;
	
	@FXML
	private Button connectButton, runMeasurementButton, xmiSelectButton;
	
	@FXML
	private WebView webViewResults;
	
	@FXML
	private ComboBox<String> selectAvailableAdapterBox;
	
	@FXML
	private VBox workloadAdapterConfigVBox;
	
	private StringProperty connectionState = new SimpleStringProperty("Connect");
	private StringProperty measurementState = new SimpleStringProperty();
	
	private AimedMainController aimed = AimedMainController.getInstance();
	
	@Override
	public void initialize(URL location, ResourceBundle resources) {
		aimed.addObserver(this);
		accordion.setExpandedPane(pane1);
		connectButton.setOnMouseClicked(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent arg0) {
				onConnectButtonClicked();				
			}
		});
		labelConnectState.textProperty().bind(connectionState);
		runMeasurementButton.setDisable(true);
		runMeasurementButton.setOnMouseClicked(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent arg0) {
				aimed.setWorkloadAdapter(getSelectedWorkloadAdapter(), getWorkloadAdapterProperties());
				aimed.startMeasurement(
						getWarmupDuration(), getMeasurementDuration(), getMethodPatterns(), getKDMFile());
			}		
		});
		selectAvailableAdapterBox.valueProperty().addListener(new ChangeListener<String>() {
			@Override
			public void changed(ObservableValue<? extends String> arg0, String arg1, String arg2) {
				onLoadWorkloadAdapterClicked();
			}
		});
		labelMeasurementState.textProperty().bind(measurementState);
		xmiSelectButton.setOnMouseClicked(new EventHandler<MouseEvent>() {
			@Override
			public void handle(MouseEvent arg0) {
				onXmiSelectButtonClicked();
				
			}
		});
		loadWorkloadAdapters();
	}
	
	private void loadWorkloadAdapters() {
		List<IExtension> extensions = aimed.getAvailableWorkloadAdapter();
		ObservableList<String> workloadAdapter = FXCollections.observableArrayList();
		for (IExtension ext : extensions) {
			workloadAdapter.add(ext.getDisplayLabel() + " - " + ext.getName());
		}
		selectAvailableAdapterBox.setItems(workloadAdapter);
		//TODO remove this line - Its for default selection of JMeter Adapter.
		selectAvailableAdapterBox.getSelectionModel().select(2);
	}
	
	private void onLoadWorkloadAdapterClicked() {
		workloadAdapterConfigVBox.getChildren().clear();
		IExtension extension = aimed.getAvailableWorkloadAdapter()
				.get(selectAvailableAdapterBox.getSelectionModel().getSelectedIndex());
		Set<ConfigParameterDescription> configDesc = extension.getConfigParameters();
		TextField text;
		for (ConfigParameterDescription cpd : configDesc) {
			text = new TextField();
			text.setPromptText(cpd.getName() + " as " + cpd.getType());
			text.setText(cpd.getDefaultValue());
			//TODO: remove lines
			if (cpd.getName().contains("workload.jmeter.home")) {
				text.setText("C:/Users/Cel/Eclipse/apache-jmeter-2.13");
			}
			if (cpd.getName().contains("workload.jmeter.scenarioFile")) {
				text.setText("C:/Users/Cel/Studium/Bachelor/Vorbereitung/userVariable.jmx");
			}
			if (cpd.getName().contains("workload.jmeter.logFileFlag")) {
				text.setText("true");
			}
			//TODO: until here
			text.setTooltip(new Tooltip(cpd.getName() + " as " + cpd.getType()));
			workloadAdapterConfigVBox.getChildren().add(text);
		}
	}
	
	private AbstractWorkloadAdapter getSelectedWorkloadAdapter() {
		AbstractWorkloadAdapter adapter = aimed.getAvailableWorkloadAdapter()
				.get(selectAvailableAdapterBox.getSelectionModel().getSelectedIndex())
				.createExtensionArtifact();
		return adapter;		
	}
	
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
			if (arg1 instanceof MeasurementStateMessage){
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

	private void onConnectButtonClicked() {
		if (!aimed.isConnected()) {
			String host = hostTextField.getText();
			String port = portTextField.getText();
			setConnectStateText("Connecting...");
			setHostPortDisable(true);
			aimed.connectToMainagent(host, port);
			if (!aimed.isConnected()) {
				setConnectStateText(String.format("Can't connect to %s:%s.", host, port));
				setHostPortDisable(false);
			} else {
				setConnectStateText("Connected.");
				setStartMeasurementButtonDisable(false);
				setConnectButtonText("Disconnect");
			}
		} else {
			aimed.disconnectFromMainagent();
			setConnectButtonText("Connect");
			setHostPortDisable(false);
			setConnectStateText("Disconnected");
			setStartMeasurementButtonDisable(true);
		}
	}
	
	private void setConnectStateText(String text) {
		if (text.isEmpty() || text == null) {
			return;
		}
		connectionState.set(text);
	}
	
	private void setHostPortDisable(boolean disabled) {
		hostTextField.setDisable(disabled);
		portTextField.setDisable(disabled);
	}
	
	private void setConnectButtonDisable(boolean disabled) {
		connectButton.setDisable(disabled);
	}
	
	private void setConnectButtonText(String text) {
		Platform.runLater(new Runnable() {
			@Override
			public void run() {
				connectButton.setText(text);
			}
		});
	}
	
	private void setStartMeasurementButtonDisable(boolean disabled) {
		runMeasurementButton.setDisable(disabled);
	}
	
	private void setStartMeasurementButtonText(String text) {
		if(text.isEmpty() || text == null) {
			return;
		}
		runMeasurementButton.setText(text);
	}
	
	private void setMethodPatternsDisable(boolean disabled) {
		methodPatterns.setDisable(disabled);
	}
	
	private void setConfigDisable(boolean disabled) {
		warmupDuration.setDisable(disabled);
		measurementDuration.setDisable(disabled);
		xmiPathTextField.setDisable(disabled);
		xmiSelectButton.setDisable(disabled);
	}
	
	private void setMeasurementStateLabelText(String text) {
		if(text.isEmpty() || text == null) {
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
		return Integer.parseInt(warmupDuration.getText());
	}
	
	private int getMeasurementDuration() {
		return Integer.parseInt(measurementDuration.getText());
	}
	
	private List<String> getMethodPatterns() {
		return Arrays.asList(methodPatterns.getText().split("\n"));		
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
		if (message.getMeasurementState() == MeasurementState.STARTING) {
			setMethodPatternsDisable(true);
			setSelectWorkloadDisable(true);
			setConfigDisable(true);
			setStartMeasurementButtonDisable(true);
			setConnectButtonDisable(true);
		}
		if (message.getMeasurementState() == MeasurementState.STOPPING) {
			setMethodPatternsDisable(false);
			setSelectWorkloadDisable(false);
			setConfigDisable(false);
			setStartMeasurementButtonDisable(false);
			setConnectButtonDisable(false);
		}
		setMeasurementStateLabelText(message.getMessage());
	}
	
	private void setSelectWorkloadDisable(boolean disable) {
		selectAvailableAdapterBox.setDisable(disable);
		workloadAdapterConfigVBox.setDisable(disable);		
	}

	private void onXmiSelectButtonClicked() {
		FileChooser fileChooser = new FileChooser();
		fileChooser.setTitle("Select xmi java model of KDM");
		File selectedFile = fileChooser.showOpenDialog(xmiSelectButton.getScene().getWindow());
		if (selectedFile != null) {
			xmiPathTextField.setText(selectedFile.getPath());
		}	
	}
	
	private File getKDMFile() {
		if (xmiPathTextField.getText() == "") {
			return null;
		} else {
			return new File(xmiPathTextField.getText());
		}
	}
}
