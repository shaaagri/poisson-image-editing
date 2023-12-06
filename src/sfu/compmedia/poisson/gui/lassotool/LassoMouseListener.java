package sfu.compmedia.poisson.gui.lassotool;

import java.awt.event.MouseEvent;
import javax.swing.event.MouseInputAdapter;


/**
 * Supports the lasso tool by registering the mouse input events.
 *
 */
public class LassoMouseListener extends MouseInputAdapter {

	Lasso lasso;
	
	public LassoMouseListener(Lasso lasso)
	{
		this.lasso = lasso;
	}
	
	public void mouseMoved(MouseEvent e) {      
    	lasso.processMouseEvent(e);
    }

    public void mousePressed(MouseEvent e) {      
    	lasso.processMouseEvent(e);
    }

    public void mouseDragged(MouseEvent e) {
    	lasso.processMouseEvent(e);
    }

    public void mouseReleased(MouseEvent e) {
    	lasso.processMouseEvent(e);
    }
}
