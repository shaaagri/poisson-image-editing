package sfu.compmedia.poisson.gui;

import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Panel;


/**
 * 
 * Implements a text bar at the very bottom of the window that is used to show
 * helpful tips to a novice user.
 *
 */
public class StatusBar extends Panel {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 6647993836523029929L;

	Frame parent;
	
	int width, height;
	String text = "";
	
	public StatusBar(int height, Frame parent)
	{
		this.parent = parent;
		this.width = parent.getWidth();
		this.height = height;
		
		this.setBounds(0, parent.getHeight() - height, width, height);
		this.setBackground(Main.colorBg);
	}
	
	public void setText(String value)
	{
		this.text = value;
	}
	
	public void paint(Graphics g)
	{
		g.setColor(Main.colorFg);
		g.drawLine(0, 0, width, 0);
		
		Font f1 = new Font("Verdana", Font.PLAIN, 11);  
	    g.setFont(f1);  
	    g.setColor(Main.colorText);
	    g.drawString(text, 14, 16);
	}
}
