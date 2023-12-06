package sfu.compmedia.poisson.gui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Panel;
import java.awt.image.BufferedImage;

/**
 * 
 * Implements a simple customized GUI element that contains only one image. 
 * The image may not occupy the whole container area.
 *
 */
public class ImageContainer extends Panel {
	
	private static final long serialVersionUID = 3010855439868723048L;

	
	
	int width, height;
	BufferedImage img;
	
	public ImageContainer(int x, int y, int width, int height, BufferedImage img)
	{
		this.setBounds(x, y, width, height);
		this.setBackground(Main.colorBg);
		
		this.width = img.getWidth();
		this.height = img.getHeight();
		this.img = img;
	}
	
	public void changeImage(BufferedImage image)
	{	
		this.width = image.getWidth();
		this.height = image.getHeight();
		this.img = image;
	}
	
	public void clear()
	{	
		this.img = null;
	}
	
	public void paint(Graphics g)
	{
		if (img != null)
		{
			g.drawImage(img, 0, 0, img.getWidth(), img.getHeight(), this);
		}
		else
		{
			Font f1 = new Font("Verdana", Font.PLAIN, 11);  
		    g.setFont(f1);  
		    g.setColor(Main.colorText);
		    g.drawString("Please select an image by using the Browse button", 185, 275);
		}
	}
	
	public BufferedImage getImage()
	{
		return img;
	}
	
	public int getImgWidth()
	{
		return width;
	}
	
	public int getImgHeight()
	{
		return height;
	}
}
