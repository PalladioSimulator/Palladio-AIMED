package aimed;
	
import java.util.Arrays;
import java.util.List;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.fxml.FXMLLoader;

import util.Config;

public class Main extends Application {
	@Override
	public void start(Stage primaryStage) {
		try {
			BorderPane root = (BorderPane)FXMLLoader.load(getClass().getResource("/ui/aimed.fxml"));
			Scene scene = new Scene(root,400,400);
			scene.getStylesheets().add(getClass().getResource("/ui/application.css").toExternalForm());
			primaryStage.setScene(scene);
			primaryStage.setTitle("AIMED - AIM for estimating demands");
			primaryStage.show();
			primaryStage.setOnCloseRequest(we -> {
				Config.getInstance().saveConfig();
				AimedMainController.getInstance().disconnectFromAIM();
				AimedMainController.getInstance().disconnectFromRserve();
			});
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	public static void main(String[] args) {
		List<String> arguments = Arrays.asList(args);
		if (!arguments.contains("-noGui")) {
			launch(args);
		}
	}
}
