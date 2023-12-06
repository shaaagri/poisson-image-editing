package sfu.compmedia.poisson;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * A collection of basic and common image processing utilities.
 * 
 * Thanks to Pegah for providing the lab workshops code which this 
 * unit is based on :-)
 * 
 */
public class ImageUtil {
	
	public static int getRed(int rgb)
	{
		return (rgb & 0x00FF0000) >> 16;
	}
	
	public static int getGreen(int rgb)
	{
		return (rgb & 0x0000FF00) >> 8;
	}
	
	public static int getBlue(int rgb)
	{
		return rgb & 0x000000FF;
	}
	
	public static int getAlpha(int rgb)
	{
		return (rgb & 0xFF000000) >> 24;
	}
	
	public static int getRGB(int red, int green, int blue)
	{
		return (0xFF << 24) | (red << 16) | (green << 8) | blue;
	}
	
	public static int[] getRGBArray(int rgb)
	{
		int[] result = new int[3];
		result[0] = getRed(rgb);
		result[1] = getGreen(rgb);
		result[2] = getBlue(rgb);
		
		return result;
	}
	
	public static float[] getRGBArrayNormalized(int rgb)
	{
		float[] result = new float[3];
		result[0] = getNormalized(getRed(rgb));
		result[1] = getNormalized(getGreen(rgb));
		result[2] = getNormalized(getBlue(rgb));
		
		return result;
	}
	
	public static float getNormalized(int value)
	{
		return value / 255f;
	}
	
	public static int getByte(float value)
	{
		return (int)(value * 255f);
	}
	
	public static int clip(int v) {
		v = v > 255 ? 255 : v;
		v = v < 0 ? 0 : v;
		return v;
	}
	
	/**
	 * A general interface for applying single-source operators
	 * 
	 * @param img
	 * @param op
	 * @return result
	 */
	public static BufferedImage singleOp(BufferedImage img, ImageOperator op, Object arg1) {
		
		BufferedImage result = new BufferedImage(img.getWidth(), img.getHeight(), img.getType());		
		int rgb;
		int rgb_out;
		
		for (int y = 0; y < img.getHeight(); y++)
		{
			for (int x = 0; x < img.getWidth(); x++)
			{
				rgb = img.getRGB(x, y);
				
				switch (op)
				{
				case ClearToWhite:
					rgb_out = getRGB(0xFF, 0xFF, 0xFF);
					result.setRGB(x, y, rgb_out);
					break;
					
				case ChangeOpacity:
					rgb_out = rgb;
					
					if (getAlpha(rgb) != 0)
					{
						int alpha = (int)((float)(arg1) * 255f);
						rgb_out = (alpha << 24) | (rgb & 0x00FFFFFF);	
					}
					
					result.setRGB(x, y, rgb_out);	
					break;
					
				default:
					break;
				}
			}
		}
		
		return result;
	}
	
	/**
	 * Clones BufferedImage content into a new BufferedImage
	 * 
	 * (Citation: https://stackoverflow.com/questions/3514158/how-do-you-clone-a-bufferedimage)
	 * 
	 * @param bi
	 * @return
	 */
	public static BufferedImage deepCopy(BufferedImage bi) {
		ColorModel cm = bi.getColorModel();
		boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
		WritableRaster raster = bi.copyData(null);
		 
		return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
	}
	
	/*
	 * Saves an image to a file
	 */
	public static boolean dump(BufferedImage img, String filename)
	{
		File file = new File(filename);
		
		try {
			ImageIO.write(img, "png", file);
		} 
		catch (IOException e) {
			//e.printStackTrace(System.err);
			return false;
		}
		
		return true;
	}
}
