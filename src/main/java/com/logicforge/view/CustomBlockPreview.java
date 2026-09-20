package com.logicforge.view;

import java.util.List;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.TextAlignment;

/** Live, non-interactive preview used by the custom-component creation dialog. */
public final class CustomBlockPreview extends Pane {

    private static final double WIDTH = 390;
    private static final double HEIGHT = 420;

    public CustomBlockPreview() {
        setPrefSize(WIDTH, HEIGHT);
        setMinSize(WIDTH, HEIGHT);
        setMaxSize(WIDTH, HEIGHT);
        setMouseTransparent(true);
        getStyleClass().add("block-creation-preview");
    }

    public void update(String name, String symbol, List<String> inputs, List<String> outputs) {
        getChildren().clear();
        List<String> safeInputs = inputs == null ? List.of() : List.copyOf(inputs);
        List<String> safeOutputs = outputs == null ? List.of() : List.copyOf(outputs);
        int maximumPins = Math.max(1, Math.max(safeInputs.size(), safeOutputs.size()));

        double bodyWidth = 210;
        double bodyHeight = Math.min(330, Math.max(120, 58 + maximumPins * 27.0));
        double bodyLeft = (WIDTH - bodyWidth) / 2.0;
        double bodyRight = bodyLeft + bodyWidth;
        double bodyTop = Math.max(34, (HEIGHT - bodyHeight) / 2.0 - 8);
        double bodyBottom = bodyTop + bodyHeight;
        double externalLeft = 30;
        double externalRight = WIDTH - 30;

        Rectangle body = new Rectangle(bodyLeft, bodyTop, bodyWidth, bodyHeight);
        body.setArcWidth(14);
        body.setArcHeight(14);
        body.getStyleClass().addAll("gate-shape", "block-body", "custom-component-body");

        Label title = label(symbol, bodyLeft + 8, (bodyTop + bodyBottom) / 2.0 - 15,
                bodyWidth - 16, 30, Pos.CENTER, "block-title", "custom-component-title");
        getChildren().addAll(body, title);

        double pinTop = bodyTop + 26;
        double pinBottom = bodyBottom - 26;
        for (int index = 0; index < safeInputs.size(); index++) {
            double y = distributed(index, safeInputs.size(), pinTop, pinBottom);
            addPin(externalLeft, bodyLeft, y, safeInputs.get(index), true);
        }
        for (int index = 0; index < safeOutputs.size(); index++) {
            double y = distributed(index, safeOutputs.size(), pinTop, pinBottom);
            addPin(bodyRight, externalRight, y, safeOutputs.get(index), false);
        }

        Label fullName = label(
                name == null || name.isBlank() ? "Custom Block" : name,
                20,
                HEIGHT - 32,
                WIDTH - 40,
                22,
                Pos.CENTER,
                "block-preview-name");
        getChildren().add(fullName);
    }

    private void addPin(double startX, double endX, double y, String text, boolean input) {
        Line line = new Line(startX, y, endX, y);
        line.getStyleClass().addAll(
                "terminal-line", input ? "input-terminal" : "output-terminal");
        Circle pin = new Circle(input ? startX : endX, y, 5);
        pin.getStyleClass().addAll(
                "pin-handle", input ? "input-pin" : "output-pin");

        Label name = label(
                text,
                input ? endX + 7 : startX - 79,
                y - 9,
                72,
                18,
                input ? Pos.CENTER_LEFT : Pos.CENTER_RIGHT,
                "pin-name");
        name.setTextAlignment(input ? TextAlignment.LEFT : TextAlignment.RIGHT);
        getChildren().addAll(line, pin, name);
    }

    private static Label label(
            String text,
            double x,
            double y,
            double width,
            double height,
            Pos alignment,
            String... styleClasses) {

        Label label = new Label(text == null ? "" : text);
        label.setLayoutX(x);
        label.setLayoutY(y);
        label.setPrefSize(width, height);
        label.setAlignment(alignment);
        label.getStyleClass().addAll(styleClasses);
        return label;
    }

    private static double distributed(int index, int count, double first, double last) {
        return count <= 1 ? (first + last) / 2.0 : first + index * ((last - first) / (count - 1.0));
    }
}
