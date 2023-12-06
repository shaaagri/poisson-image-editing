package sfu.compmedia.poisson.gui.lassotool;

import java.awt.Polygon;
import java.awt.image.BufferedImage;

/**
 * 
 * An interface to communicate action events to an observer
 *
 */
public interface LassoToolActionListener {
	void pasteSelection(int x, int y, Polygon selection, BufferedImage selectionImg);
}
