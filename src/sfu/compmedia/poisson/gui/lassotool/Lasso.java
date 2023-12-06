package sfu.compmedia.poisson.gui.lassotool;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

import sfu.compmedia.poisson.ImageOperator;
import sfu.compmedia.poisson.ImageUtil;
import sfu.compmedia.poisson.gui.ImageContainer;
import sfu.compmedia.poisson.gui.StatusBar;


/**
 * 
 * Implements the lasso tool functionality to encircle a particular part
 * of a source image (left) before dragging it to the target one (right).
 *
 */
public class Lasso {
	
	final static Color colorLasso = new Color(1.0f, 0.35f, 0.0f, 1.0f);
	final static Color colorMask = new Color(1.0f, 0.35f, 0.0f, 0.5f);
	
	final static float minExpandDistance = 25f;
	final static float magnetDistance = 14f;
	
	// Can restrict the selection to never reach the edges of the image
	// (Poisson blending may not work well with masks reaching to a boundary 
	// of the image - lil padding can help to mitigate that)
	final static int padding = 0; // px
	
	LassoToolState state = LassoToolState.INACTIVE;
	ArrayList<LassoToolActionListener> listeners;
	
	Frame parentFrame;
	Panel subjectContainer, targetContainer;
	StatusBar statusBar;
	
	ArrayList<Panel> panels;
	
	Point startPos, lastPos;
	ArrayList<Point> selectionPath;
	
	ArrayList<Polygon> masks;
	BufferedImage selection;
	BufferedImage selectionTransparent;
	
	Cursor transferringCursor, transferringCursorHover;
	
	
	/**
	 * A flag that is used to determine when the shape will accept auto-closure
	 * (all based on distance between the start point and the current one - it
	 * should go beyond minExpandDistance)
	 */
	boolean canClose;
	
	/**
	 * Constructor
	 * 
	 * @param parent Parent Frame object
	 * @param registeredPanels Panel objects to hook to
	 * @param targetContainer The container of the composite image
	 */
	public Lasso(Frame parent, ArrayList<Panel> registeredPanels, Panel targetContainer, StatusBar statusBar)
	{
		this.parentFrame = parent;
		this.panels = registeredPanels;
		this.targetContainer = targetContainer;
		this.statusBar = statusBar;
		selectionPath = new ArrayList<Point>();	
		
		
		// For each "domain of definition" we define a shape (Polygon) behind the scenes where our 
		// hand-drawn mask is going to be encoded
		//
		// In this case our DOD the whole area of an ImageContainer, but if the image is
		// smaller we won't use it all. We are always constrained by the image size, not panel's
		masks = new ArrayList<Polygon>();
		for (Panel p : panels)
		{
			Polygon poly = new Polygon();
			masks.add(poly);
		}
		
		listeners = new ArrayList<LassoToolActionListener>();
		showHint(LassoHint.HINT_1);
	}
	
	public void addEventListener(LassoToolActionListener listener) {
        listeners.add(listener);
    }
	
	public int getPadding()
	{
		return padding;
	}
	
	/**
	 * Handles the mouse clicks and position changes that control the tool.
	 * 
	 * @param e Mouse event data
	 */
	public void processMouseEvent(MouseEvent e)
	{			
		if (state == LassoToolState.INACTIVE)
		{
			// Making sure that the mouse event came from one of the image containers
			// that were registered for the lasso usage
			for (Panel p : panels)
			{
				if (e.getComponent() instanceof Panel && p == (Panel)e.getComponent())
				{
					subjectContainer = p;
					break;						
				}
			}	
		}
		
		if (subjectContainer == null) return;
		
		if (e.getID() == MouseEvent.MOUSE_PRESSED)
		{
			if (state == LassoToolState.INACTIVE && ((ImageContainer)subjectContainer).getImage() != null 
					&& e.getButton() == MouseEvent.BUTTON1)  // accepting only left clicks here
			{
				// bugfix: preventing drawing to be sometimes triggered when we click at the right
				if (e.getComponent() != subjectContainer) return; 
				
				state = LassoToolState.DRAWING;
				
				selectionPath.clear();
				lastPos = null;
				canClose = false;
				
				showHint(LassoHint.HINT_2);	
			}
			else if (state == LassoToolState.TRANSFERRING_THE_SELECTED)
			{
				// If we are clicked outside the target container, discard the 
				// selection and, consequently, the whole operation
				//
				// If we clicked the Right button (anywhere), cancel all as well
				//
				// Also cancel it if no image is loaded into the target container
				if (e.getComponent() != targetContainer || e.getButton() == MouseEvent.BUTTON3 
						|| ((ImageContainer)targetContainer).getImage() == null)
				{
					parentFrame.setCursor(null);
					subjectContainer = null;
					state = LassoToolState.INACTIVE;
					showHint(LassoHint.HINT_1);	
					
					return;
				}	
				
				int maskIndex = panels.indexOf(subjectContainer);
				Polygon mask = masks.get(maskIndex);
				
				for (LassoToolActionListener listener : listeners)
				{
					listener.pasteSelection(e.getX(), e.getY(), mask, selection);
					
					state = LassoToolState.INACTIVE;
					showHint(LassoHint.HINT_5);	
				}
			}
		}
		
		else if (state == LassoToolState.DRAWING && e.getID() == MouseEvent.MOUSE_DRAGGED)
		{
			//TODO: cancel drawing if mouse went beyond the subjectcontainer
			
			processMouseDragged(e, subjectContainer);	
		}	
		
		else if ((state == LassoToolState.DRAWING || state == LassoToolState.LOCKED_IN) && 
				e.getID() == MouseEvent.MOUSE_RELEASED)
		{
			subjectContainer.repaint();
			
			if (state == LassoToolState.DRAWING)
			{
				// If we are still in the "Drawing" state, the mouse has been released 
				// before the path was auto-closed. Discarding the operation
				state = LassoToolState.INACTIVE;
				subjectContainer = null;
				showHint(LassoHint.HINT_1);	
				
				return;
			}
			
			state = LassoToolState.TRANSFERRING_THE_SELECTED;
			showHint(LassoHint.HINT_4);
			
			int maskIndex = panels.indexOf(subjectContainer);
			Polygon mask = masks.get(maskIndex);
			
			selection = createSelection(((ImageContainer)subjectContainer).getImage(), mask);
			selectionTransparent = ImageUtil.singleOp(selection, ImageOperator.ChangeOpacity, 0.5f);
			
			//ImageUtil.dump(selection, "testfile.png");
			
			
			/*
			 * For the final stage we want to transfer the selected portion of the source image to the target
			 * We simply change, temporarily, the cursor image
			 */
			
			// The hotspot defines how the cursor image is centered
			// The values chosen are such that feel the most natural during the interaction
			Point hotSpot = new Point(selection.getWidth() / 2, selection.getHeight() / 2);
			transferringCursor = Toolkit.getDefaultToolkit().createCustomCursor(selectionTransparent, hotSpot, "transferringCursor");
			transferringCursorHover = Toolkit.getDefaultToolkit().createCustomCursor(selection, hotSpot, "transferringCursorHover");
			
			parentFrame.setCursor(transferringCursor);
		}
		
		if (e.getID() == MouseEvent.MOUSE_MOVED)
		{
			if (state == LassoToolState.TRANSFERRING_THE_SELECTED)
			{
				// Apply the hover effect to let the user know they can now drop the selection 
				// onto the target image (otherwise it's semi-transparent)
				if (e.getComponent() == targetContainer && ((ImageContainer)targetContainer).getImage() != null)
					parentFrame.setCursor(transferringCursorHover);
				else
				{
					parentFrame.setCursor(transferringCursor);
				}					
			}
		}
	}
	
	private void processMouseDragged(MouseEvent e, Panel parent)
	{
		if (state != LassoToolState.DRAWING || panels.indexOf(parent) == -1)
			return;

		Graphics g = parent.getGraphics();
		g.setPaintMode();
		g.setColor(colorLasso);
		
		if (lastPos == null)
		{
			startPos = new Point(e.getX(), e.getY());
			lastPos = new Point(e.getX(), e.getY());	
			selectionPath.add(startPos);
			
			g.drawLine(lastPos.x, lastPos.y, e.getX(), e.getY());
		}
		else
		{
			g.drawLine(lastPos.x, lastPos.y, e.getX(), e.getY());
			
			selectionPath.add(new Point(e.getX(), e.getY()));
			lastPos.setLocation(e.getX(), e.getY());
			
			// Calculate the distance between current pos and the starting pos (in 2D)
			float slDistance = (float)startPos.distance(lastPos);
			
			// Test the the mouse cursor has gone far enough
			// (otherwise the auto-closure may be triggered prematurely)
			if (!canClose && slDistance > minExpandDistance)
			{
				canClose = true;
			}
			
			// Test whether the mouse cursor is near to where we began drawing
			// If it is, automatically close the path
			if (canClose && slDistance < magnetDistance)
			{
				g.drawLine(lastPos.x, lastPos.y, startPos.x, startPos.y);
				
				int n = selectionPath.size();
				int[] xPoints = new int[n];
				int[] yPoints = new int[n];
				
				ImageContainer ic = (ImageContainer)parent;
				int imgW = ic.getImgWidth();
				int imgH = ic.getImgHeight();
		
				for (int i = 0; i < n; i++)
				{
					xPoints[i] = selectionPath.get(i).x;
					yPoints[i] = selectionPath.get(i).y;
					
					// Limiting the selection area to the image at hand
					xPoints[i] = Math.min(Math.max(padding, xPoints[i]), imgW - padding);
					yPoints[i] = Math.min(Math.max(padding, yPoints[i]), imgH - padding);
				}
				
				Polygon maskShape = new Polygon(xPoints, yPoints, n);				
				Rectangle2D imageBounds = new Rectangle(0, 0, imgW, imgH);
				
				if (!maskShape.intersects(imageBounds))
				{
					// Quit if the area we have selected is outside the image
					state = LassoToolState.INACTIVE;
					parent.repaint();
					subjectContainer = null;
					showHint(LassoHint.HINT_1);	
					
					return;
				}
				
				// Show the mask on top of the image with some transparency
				g.setColor(colorMask);
				g.fillPolygon(xPoints, yPoints, n);
				
				int maskIndex = panels.indexOf(parent);
				masks.set(maskIndex, maskShape);
				showHint(LassoHint.HINT_3);	
				
				// Create the copy of the same mask with no transparency to use
				// as the actual (offscreen) matte image
				
				// upd: ended up using just the Polygon (see "maskShape" above),
				
				/*Graphics2D g2 = masks.get(maskIndex).createGraphics();
				g2.setBackground(Color.BLACK);
				g2.clearRect(0, 0, parent.getWidth(), parent.getHeight());
				g2.setColor(Color.RED);
				g2.fillPolygon(xPoints, yPoints, n);
				g2.dispose();*/
				
				state = LassoToolState.LOCKED_IN;
			}
		}
	}
	
	/**
	 * Outputs a masked image using the provided mask shape
	 * 
	 * @param srcImg
	 * @param mask
	 * @return
	 */
	private BufferedImage createSelection(BufferedImage srcImg, Polygon mask)
	{
		Rectangle maskBounds = mask.getBounds();
		
		BufferedImage result = new BufferedImage(maskBounds.width + 10, maskBounds.height + 10, BufferedImage.TYPE_INT_ARGB);		
		int rgb;
		int rgb_out;
		int maskX = (int)mask.getBounds().getX();
		int maskY = (int)mask.getBounds().getY();
		
		for (int y = 0; y < srcImg.getHeight(); y++)
		{
			for (int x = 0; x < srcImg.getWidth(); x++)
			{
				boolean isPixelInMask = mask.contains(x, y);
				
				rgb = srcImg.getRGB(x, y);
				rgb_out = isPixelInMask ? rgb : 0xFFFFFF00;
				
				if (isPixelInMask)
					result.setRGB(x - maskX, y - maskY, rgb_out);				
			}
		}
		
		return result;
	}
	
	private void showHint(LassoHint hintId)
	{
		String[] hints = {
				"Draw a selection mask on top of the left image using the mouse", 
				"Encircle the desired region and return to the starting point to initiate an automatic closure",
				"Release the mouse to create the cutout",
				"Move the portion to the image at the right and drop at desired location. Right-click to cancel",
				"Pasting completed!"
		};
		
		statusBar.setText(hints[hintId.ordinal()]);
		statusBar.repaint();
	}
}

