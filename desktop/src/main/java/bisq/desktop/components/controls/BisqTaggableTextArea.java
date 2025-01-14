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

package bisq.desktop.components.controls;

import javafx.geometry.Insets;
import org.fxmisc.richtext.StyleClassedTextArea;

public class BisqTaggableTextArea extends StyleClassedTextArea {
    public BisqTaggableTextArea() {
        setWrapText(true);
        setBackground(null);
        setEditable(false);
        setStyle("-fx-fill: -fx-dark-text-color");
        setPadding(new Insets(0, 0, 5, 0));
    }

    public void setText(String text) {
        clear();
        replaceText(0, 0, text);
    }
}