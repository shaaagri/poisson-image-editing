package sfu.compmedia.poisson;

import java.awt.Polygon;
import java.awt.image.BufferedImage;

import org.ejml.data.*;
import org.ejml.interfaces.linsol.LinearSolver;
import org.ejml.sparse.csc.factory.LinearSolverFactory_DSCC;
import org.ejml.sparse.*;


/**
 * 
 * The heart of the project - the implementation of the Poisson blending technique,
 * as described by Pérez et al.
 *  
 */
public class PoissonImageEditor extends ImageUtil {
		
	/**
	 * Primary constructor
	 */
	public PoissonImageEditor()
	{	
		//
	}
	
	
	/**
	 * Computes image gradient - the numerical representation of the guidance 
	 * vector field (which guides the interpolation for the subject region)
	 * 
	 * @param fstarp
	 * @param fstarq
	 * @param gp
	 * @param gq
	 */
	private float vpq(float fstarp, float fstarq, float gp, float gq)
	{	
		float fstar_delta = fstarp - fstarq;
		float g_delta = gp - gq;
		
		// Equations (11) and (13) from the paper
		
		if (Math.abs(fstar_delta) > Math.abs(g_delta))
			return fstar_delta;
		else
			return g_delta;
	}
	
	/**
	 *
	 * Paste source image into the target one at the coordinates provided.
	 * 
	 * @param sourceImg
	 * @param sourceImgMatte
	 * @param targetImg
	 * @param pasteX
	 * @param pasteY
	 * @return
	 */
	public BufferedImage Paste(BufferedImage sourceImg, BufferedImage sourceImgMatte, BufferedImage targetImg, int pasteX, int pasteY)
	{
		BufferedImage result = ImageUtil.deepCopy(targetImg);
		
		final int w = sourceImg.getWidth();
		final int h = sourceImg.getHeight();
		final int px = pasteX;
		final int py = pasteY;
		
		
		/*
		 * Each pixel that belongs to the region of interpolation ("Omega" in the original paper)
		 * is involved in the computation and, thus, results in an unknown variable, with an index
		 * starting from 0. -1 means no variables correspond to the pixel. To determine them all
		 * we just need to enumerate all source image pixels that belong to its matte.
		 */
		int[][] vars = new int[w][h];
		boolean[][] isMattePixel = new boolean[w][h];  // saves us from calling MatteTest() all the time
		
		int i_var = 0;
		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{
				boolean inMatte = MatteTest(sourceImgMatte, x, y);
				vars[x][y] = inMatte ? i_var++ : -1;
				isMattePixel[x][y] = inMatte ? true : false;
			}
		}
		
		final int numVars = i_var;


		/* 
		 * Filling up the matrix A. We need to solve Ax=B in the end.
		 *
		 * Sparse matrix type is the way to go, because our matrix is going to have A LOT of zeroes.
		 */
		
		DMatrixSparseCSC A = new DMatrixSparseCSC(numVars, numVars);
		
		int i_row = 0;
		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{				
				if (isMattePixel[x][y])
				{
					// This corresponds to the equation (7) in the original paper
					// The value is 4 because |N_p| equals 4 (each pixel has four direct neighbours)
					A.unsafe_set(i_row, vars[x][y], 4);
					
					// Neighbouring pixels also determine some values - filling the corresponding spots
					// in the matrix, going clockwise
					if (y > 0 && isMattePixel[x][y-1])
						A.unsafe_set(i_row, vars[x][y-1], -1);
					if (x < w-1 && isMattePixel[x+1][y])
						A.unsafe_set(i_row, vars[x+1][y], -1);
					if (y < h-1 && isMattePixel[x][y+1])
						A.unsafe_set(i_row, vars[x][y+1], -1);
					if (x > 0 && isMattePixel[x-1][y])
						A.unsafe_set(i_row, vars[x-1][y], -1);
					
					i_row++;
				}
			}
		}
	
		DMatrixRMaj[] B = new DMatrixRMaj[3];
		
		float[] g = new float[3];  // function representing the source image (fg)
		float[] fstar = new float[3];  // function representing the target image (bg)
		
		for (int i = 0; i < 3; i++)
		{
			/*
			 * We've got to compute three different B vectors for each RGB channel.
			 * Then we solve Ax=B for x three times, correspondingly.
			 */
			
			B[i] = new DMatrixRMaj(numVars, 1);
			
			i_row = 0;
			for (int y = 0; y < h; y++)
			{
				for (int x = 0; x < w; x++)
				{				
					if (isMattePixel[x][y])
					{	
						g[i] = getRGBArrayNormalized(sourceImg.getRGB(x, y))[i];
						fstar[i] = getRGBArrayNormalized(result.getRGB(x+px, y+py))[i];
						
						// >>> TODO: add edges check to avoid crash when pasteX,pasteY are unfit
						
						// Computing the final gradient, summing up the corresponding gradients per each pixel's neighbour
						// This is our right-hand side in the equation (7)
						float grad = 
								vpq(fstar[i], getRGBArrayNormalized(result.getRGB(x+px, y+py-1))[i], 
										g[i], getRGBArrayNormalized(sourceImg.getRGB(x, y-1))[i]) +
								vpq(fstar[i], getRGBArrayNormalized(result.getRGB(x+1+px, y+py))[i],
										g[i], getRGBArrayNormalized(sourceImg.getRGB(x+1, y))[i]) +
								vpq(fstar[i], getRGBArrayNormalized(result.getRGB(x+px, y+1+py))[i],
										g[i], getRGBArrayNormalized(sourceImg.getRGB(x, y+1))[i]) +
								vpq(fstar[i], getRGBArrayNormalized(result.getRGB(x-1+px, y+py))[i],
										g[i], getRGBArrayNormalized(sourceImg.getRGB(x-1, y))[i]);
						
						// Finally, we add here some values that didn't end up in the matrix A, because they
						// are not unknowns. And they are not unknowns because they reside on the boundary 
						// of the region where known target image values begin - the boundary condition that
						// the paper is talking about.
						
						if (!isMattePixel[x][y-1])
							grad += getRGBArrayNormalized(result.getRGB(x+px, y+py-1))[i];
						if (!isMattePixel[x+1][y])
							grad += getRGBArrayNormalized(result.getRGB(x+px+1, y+py))[i];
						if (!isMattePixel[x][y+1])
							grad += getRGBArrayNormalized(result.getRGB(x+px, y+py+1))[i];
						if (!isMattePixel[x-1][y])
							grad += getRGBArrayNormalized(result.getRGB(x+px-1, y+py))[i];
							
						B[i].unsafe_set(i_row, 0, grad);
						
						i_row++;
					}
				}
			}
		}
		
		DMatrixRMaj[] solution = new DMatrixRMaj[3];
		solution[0] = new DMatrixRMaj(numVars);
		solution[1] = new DMatrixRMaj(numVars);
		solution[2] = new DMatrixRMaj(numVars);
		
		LinearSolver<DMatrixSparseCSC, DMatrixRMaj> solver = LinearSolverFactory_DSCC.cholesky(FillReducing.NONE);
		solver.setA(A);
		solver.solve(B[0], solution[0]);
		solver.solve(B[1], solution[1]);
		solver.solve(B[2], solution[2]);
			
		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{	
				if (isMattePixel[x][y])
				{
					int varIndex = vars[x][y];
					
					int r_val = clip(getByte((float)solution[0].unsafe_get(varIndex, 0)));
					int g_val = clip(getByte((float)solution[1].unsafe_get(varIndex, 0)));
					int b_val = clip(getByte((float)solution[2].unsafe_get(varIndex, 0)));
					
					int rgb = getRGB(r_val, g_val, b_val);
					result.setRGB(x + pasteX, y + pasteY, rgb);
				}
			}
		}
		
		return result;
	}
	
	/**
	 * Returns true if an image pixel at (x, y) belongs to its matte (mask)
	 * 
	 * @param x
	 * @param y
	 * @return result
	 */
	public boolean MatteTest(BufferedImage matte, int x, int y)
	{
		return (getRed(matte.getRGB(x, y)) == 255);
	}
	
	/**
	 * Creates an internal matte out of a Polygon object. 
	 * 
	 * Shapes are handy but used exclusively by Java AWT, whereas an image as a matte form is universal
	 * and can be loaded from disk or external software, which makes our code much more flexible.
	 * 
	 * @param maskShape
	 * @param srcImage (should have same dimensions as maskShape's bounds)
	 * @return
	 */
	public static BufferedImage CreateMatte(BufferedImage srcImage, Polygon maskShape)
	{
		int w = srcImage.getWidth();
		int h = srcImage.getHeight();
		
		BufferedImage result = new BufferedImage(w, h, srcImage.getType());
		
		int rgb_bg = 0xFF000000;
		int rgb_matte = 0xFFFF0000;
		int rgb;
		
		for (int y = 0; y < h; y++)
		{
			for (int x = 0; x < w; x++)
			{
				boolean inMask = maskShape.contains(x, y);
				rgb = inMask ? rgb_matte : rgb_bg;
				
				result.setRGB(x, y, rgb);
			}
		}
		
		return result;
	}
}
