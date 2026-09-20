package com.logicforge.view;

import com.logicforge.model.BreadboardCircuit.PlacedIc;
import com.logicforge.model.BreadboardLayout;
import com.logicforge.model.BreadboardLayout.IcPlacement;
import com.logicforge.model.IcDefinition;

import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;

/** Compact DIP package whose lead centres land exactly on breadboard sockets. */
public final class BreadboardIcView extends Pane {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass UNPOWERED = PseudoClass.getPseudoClass("unpowered");

    private final PlacedIc placedIc;
    private IcPlacement placement;

    public BreadboardIcView(PlacedIc placedIc, BreadboardLayout layout) {
        this.placedIc = placedIc;
        getStyleClass().add("breadboard-ic");
        setPickOnBounds(true);
        updatePlacement(layout);
        buildSymbol();
        updatePowerVisual();
    }

    public int getIcId() {
        return placedIc.id();
    }

    public PlacedIc getPlacedIc() {
        return placedIc;
    }

    public IcPlacement getPlacement() {
        return placement;
    }

    public void updatePlacement(BreadboardLayout layout) {
        placement = layout.placement(
                placedIc.moduleIndex(),
                placedIc.firstRow(),
                placedIc.definition().packageType().pinCount());
        setPrefSize(placement.width(), placement.height());
        setMinSize(placement.width(), placement.height());
        setMaxSize(placement.width(), placement.height());
        relocate(placement.x(), placement.y());
    }

    public void setSelectedVisual(boolean selected) {
        pseudoClassStateChanged(SELECTED, selected);
    }

    public void updatePowerVisual() {
        pseudoClassStateChanged(UNPOWERED, !placedIc.isPowered());
    }

    private void buildSymbol() {
        getChildren().clear();
        IcDefinition definition = placedIc.definition();
        double width = placement.width();
        double height = placement.height();
        double bodyInset = 13;

        Rectangle body = new Rectangle(
                bodyInset, 7, width - bodyInset * 2, height - 14);
        body.setArcWidth(12);
        body.setArcHeight(12);
        body.getStyleClass().add("breadboard-ic-body");
        getChildren().add(body);

        Circle notch = new Circle(width / 2.0, 8, 7);
        notch.getStyleClass().add("breadboard-ic-notch");
        getChildren().add(notch);

        Label partNumber = new Label(definition.partNumber());
        partNumber.setAlignment(Pos.CENTER);
        partNumber.setPrefWidth(Math.max(100, height - 45));
        partNumber.setRotate(90);
        partNumber.relocate(
                width / 2.0 - partNumber.getPrefWidth() / 2.0,
                height / 2.0 - 11);
        partNumber.getStyleClass().add("breadboard-ic-part-number");
        getChildren().add(partNumber);

        Label powerBadge = new Label("POWER");
        powerBadge.getStyleClass().add("breadboard-ic-power-badge");
        powerBadge.relocate(width / 2.0 - 20, height - 27);
        getChildren().add(powerBadge);

        int pinCount = definition.packageType().pinCount();
        int pinsPerSide = pinCount / 2;
        for (IcDefinition.IcPin pin : definition.pins()) {
            boolean left = pin.number() <= pinsPerSide;
            int row = left
                    ? pin.number() - 1
                    : pinCount - pin.number();
            double y = BreadboardLayout.IC_TOP_PADDING
                    + row * BreadboardLayout.HOLE_PITCH;
            double x = left ? 0 : width;
            double bodyX = left ? bodyInset : width - bodyInset;

            Line lead = new Line(x, y, bodyX, y);
            lead.getStyleClass().add("breadboard-ic-lead");

            Circle pinCircle = new Circle(x, y, BreadboardLayout.HOLE_RADIUS);
            pinCircle.getStyleClass().add("breadboard-ic-pin");
            Tooltip.install(pinCircle, new Tooltip(
                    definition.partNumber() + " pin " + pin.number() + " • " + pin.name()));

            Label pinNumber = new Label(Integer.toString(pin.number()));
            pinNumber.getStyleClass().add("breadboard-ic-pin-number");
            pinNumber.relocate(left ? bodyInset + 2 : width - bodyInset - 13, y - 7);
            getChildren().addAll(lead, pinCircle, pinNumber);
        }

        Tooltip.install(this, new Tooltip(
                definition.description()
                        + "\nDrag the package to move it; every lead occupies one socket."));
    }
}
