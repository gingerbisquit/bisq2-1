/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.common.utils;

import bisq.desktop.common.threading.UIThread;
import bisq.settings.DisplaySettings;
import javafx.animation.*;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Transitions {
    public final static int DEFAULT_DURATION = 600;
    public final static int CROSS_FADE_IN_DURATION = 1500;
    public final static int CROSS_FADE_OUT_DURATION = 1000;
    @Setter
    private static DisplaySettings displaySettings;

    private static Timeline removeEffectTimeLine;

    public static void fadeIn(Node node) {
        fadeIn(node, DEFAULT_DURATION);
    }

    public static void fadeIn(Node node, int duration) {
        FadeTransition fade = new FadeTransition(Duration.millis(getDuration(duration)), node);
        fade.setFromValue(0);
        fade.setToValue(1.0);
        fade.play();
    }

    public static FadeTransition fadeOut(Node node) {
        return fadeOut(node, DEFAULT_DURATION);
    }

    public static void fadeOutAndRemove(Node node) {
        fadeOutAndRemove(node, DEFAULT_DURATION);
    }

    public static void fadeOutAndRemove(Node node, int duration) {
        fadeOutAndRemove(node, duration, null);
    }

    public static void fadeOutAndRemove(Node node, int duration, EventHandler<ActionEvent> handler) {
        FadeTransition fade = fadeOut(node, getDuration(duration));
        fade.setInterpolator(Interpolator.EASE_IN);
        fade.setOnFinished(actionEvent -> {
            ((Pane) (node.getParent())).getChildren().remove(node);
            //Profiler.printMsgWithTime("fadeOutAndRemove");
            if (handler != null)
                handler.handle(actionEvent);
        });
    }

    public static void blur(Node node) {
        blur(node, DEFAULT_DURATION, -0.1, false, 15);
    }

    public static void blur(Node node, int duration, double brightness, boolean removeNode, double blurRadius) {
        if (removeEffectTimeLine != null)
            removeEffectTimeLine.stop();

        node.setMouseTransparent(true);
        GaussianBlur blur = new GaussianBlur(0.0);
        Timeline timeline = new Timeline();
        KeyValue kv1 = new KeyValue(blur.radiusProperty(), blurRadius);
        KeyFrame kf1 = new KeyFrame(Duration.millis(getDuration(duration)), kv1);
        ColorAdjust darken = new ColorAdjust();
        darken.setBrightness(0.0);
        blur.setInput(darken);
        KeyValue kv2 = new KeyValue(darken.brightnessProperty(), brightness);
        KeyFrame kf2 = new KeyFrame(Duration.millis(getDuration(duration)), kv2);
        timeline.getKeyFrames().addAll(kf1, kf2);
        node.setEffect(blur);
        if (removeNode) timeline.setOnFinished(actionEvent -> UIThread.runOnNextRenderFrame(() -> ((Pane) (node.getParent()))
                .getChildren().remove(node)));
        timeline.play();
    }

    public static void darken(Node node, int duration, boolean removeNode) {
        blur(node, duration, -0.2, removeNode, 0);
    }

    public static void removeEffect(Node node) {
        removeEffect(node, DEFAULT_DURATION);
    }

    private static void removeEffect(Node node, int duration) {
        if (node != null) {
            node.setMouseTransparent(false);
            removeEffectTimeLine = new Timeline();
            if (node.getEffect() instanceof GaussianBlur gaussianBlur) {
                KeyValue kv1 = new KeyValue(gaussianBlur.radiusProperty(), 0.0);
                KeyFrame kf1 = new KeyFrame(Duration.millis(getDuration(duration)), kv1);
                removeEffectTimeLine.getKeyFrames().add(kf1);

                ColorAdjust darken = (ColorAdjust) gaussianBlur.getInput();
                KeyValue kv2 = new KeyValue(darken.brightnessProperty(), 0.0);
                KeyFrame kf2 = new KeyFrame(Duration.millis(getDuration(duration)), kv2);
                removeEffectTimeLine.getKeyFrames().add(kf2);
                removeEffectTimeLine.setOnFinished(actionEvent -> {
                    node.setEffect(null);
                    removeEffectTimeLine = null;
                });
                removeEffectTimeLine.play();
            } else {
                node.setEffect(null);
                removeEffectTimeLine = null;
            }
        }
    }

    public static FadeTransition fadeOut(Node node, int duration) {
        FadeTransition fade = new FadeTransition(Duration.millis(getDuration(duration)), node);
        fade.setFromValue(node.getOpacity());
        fade.setToValue(0.0);
        fade.play();
        return fade;
    }

    private static int getDuration(int duration) {
        return displaySettings.isUseAnimations() ? duration : 1;
    }

    public static void crossFade(Node node1, Node node2) {
        fadeOut(node1, CROSS_FADE_OUT_DURATION)
                .setOnFinished(e -> Transitions.fadeIn(node2, CROSS_FADE_IN_DURATION));
    }
}
