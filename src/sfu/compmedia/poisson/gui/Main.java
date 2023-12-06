package sfu.compmedia.poisson.gui;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.JFileChooser;

import sfu.compmedia.poisson.ImageUtil;
import sfu.compmedia.poisson.PoissonImageEditor;
import sfu.compmedia.poisson.gui.lassotool.LassoMouseListener;
import sfu.compmedia.poisson.gui.lassotool.LassoToolActionListener;
import sfu.compmedia.poisson.gui.lassotool.Lasso;

public class Main extends Frame implements LassoToolActionListener {
	
	public static final Color colorBg = new Color(0.9f, 0.9f, 0.9f);
	public static final Color colorFg = new Color(0.6f, 0.6f, 0.6f);
	public static final Color colorText = new Color(0.18f, 0.18f, 0.18f);

	private static final long serialVersionUID = -428598892277248342L;
	
	private static final int windowW = 1366, windowH = 768;
	private static final int imgContainerW = 659, imgContainerH = 512;
	
	BufferedImage sourceImg;
	BufferedImage sourceImgMatte;
	BufferedImage targetImg, targetImgUndo;

	ImageContainer imgContainerA, imgContainerB;
	
	Label l1, l2;
	Label lAPlaceholder, lBPlaceholder;
	Label lHint;	
	Button btnABrowse, btnAClear;
	Button btnBBrowse, btnBClear, btnBReset, btnBSave;
	StatusBar statusBar;
	
	LassoMouseListener lassoMouseListener;
	Lasso lasso;
	
	PoissonImageEditor pie;
	
	boolean initialized;

	public Main()
	{
		this.setTitle("Poisson Image Editing");
		this.setVisible(true);
		
		try
		{
			init();
		}
		catch (Exception e)
		{
			System.out.println("init() failed");
			System.exit(1);
		}
		
		//Anonymous inner-class listener to terminate program
		this.addWindowListener(
			new WindowAdapter(){
				public void windowClosing(WindowEvent e){
					System.exit(0);
				}
			}
		);
	}
	
	public void paint(Graphics g)
	{
		this.setSize(windowW, windowH);
		this.setLayout(null);
		
		g.setColor(Color.BLACK);
	    Font f1 = new Font("Verdana", Font.PLAIN, 13);  
	    g.setFont(f1);    
	}
	
	public boolean isInitialized()
	{
		return initialized;
	}
	
	// entry point
	public static void main(String[] args)
	{	
	    Main gui = new Main();  //instantiate self
	    gui.repaint();
	}
	
	
	@Override
	public void pasteSelection(int x, int y, Polygon selection, BufferedImage selectionImg) {
			
		// Compensating for the difference between position of the selection mask and 
		// the position of the origin of the image (top-left)
		Rectangle maskBounds = selection.getBounds();
		int pasteX = (int)(x - maskBounds.x - maskBounds.getWidth() / 2) - lasso.getPadding();
		int pasteY = (int)(y - maskBounds.y - maskBounds.getHeight() / 2) - lasso.getPadding();
		
		setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
	
		/*
		 * Cutting away the mask if it goes beyond the bounds of the target image
		 * (without this, the algorithm will fail when pasting close to the edges)
		 */
		Rectangle containerArea = new Rectangle(0, 0, imgContainerB.getImgWidth(), imgContainerB.getImgHeight());
		Rectangle pastedArea = new Rectangle(pasteX, pasteY, (int)sourceImg.getWidth(), (int)sourceImg.getHeight());		
		Rectangle pastedAreaConstrained = containerArea.intersection(pastedArea);
		
		pastedAreaConstrained.setLocation(pastedAreaConstrained.x - pasteX, pastedAreaConstrained.y - pasteY);
		
		for (int i = 0; i < selection.xpoints.length; i++)
		{
			selection.xpoints[i] = Math.max(pastedAreaConstrained.x + 1, 
					Math.min(selection.xpoints[i], pastedAreaConstrained.x + pastedAreaConstrained.width - 1));
		}
		for (int i = 0; i < selection.ypoints.length; i++)
		{
			selection.ypoints[i] = Math.max(pastedAreaConstrained.y + 1, 
					Math.min(selection.ypoints[i], pastedAreaConstrained.y + pastedAreaConstrained.height - 1));
		}
		
		// The central part
		BufferedImage matte = PoissonImageEditor.CreateMatte(sourceImg, selection);
		BufferedImage composite = pie.Paste(sourceImg, matte, targetImg, pasteX, pasteY);
		
		targetImg = composite;
		imgContainerB.changeImage(targetImg);
		imgContainerB.repaint();	
		setCursor(null);		
	}
	
	private void init() throws Exception
	{
		try
		{
			sourceImg = ImageIO.read(new File("sampleImages/kitten.png"));
			//sourceImgMatte = ImageIO.read(new File("kittenmask.png"));
			targetImg = ImageIO.read(new File("sampleImages/library.png"));
			targetImgUndo = ImageUtil.deepCopy(targetImg);
		}
		catch (IOException e)
		{
			System.out.println(String.format("Unable to load the default set of images (%s)", e.getMessage()));
			throw e;
		}
		
		try
		{		
			/*
			 * Labels
			 */
			
			l1 = new Label("Source Image");   
		    l1.setBounds(298, 43, 92, 16);
		    l1.setAlignment(Label.CENTER);
		    this.add(l1);
		    
		    l2 = new Label("Target Image");
		    l2.setBounds(972, 43, 92, 16);
		    l2.setAlignment(Label.CENTER);
		    this.add(l2);
		    
		    
		    /*
			 * Buttons
			 */
		    
		    btnABrowse = new Button("Browse...");
		    btnABrowse.setBounds(13, 601, 130, 40);
		    btnABrowse.addActionListener(new ActionListener() {    
		    	public void actionPerformed (ActionEvent e) {    
	        		String filename = showFileDialog("Choose an image file", FileDialog.LOAD);
	        		
	        		try
	        		{
	        			sourceImg = ImageIO.read(new File(filename));
	        			imgContainerA.changeImage(sourceImg);
	        			imgContainerA.repaint();
	        		}
	        		catch (IOException ee)
	        		{
	        			System.out.println(String.format("Unable to load the image (%s): (%s)", filename, ee.getMessage()));
	        		}
		        }    
		    }); 
		    
		    this.add(btnABrowse);
		    	    
		    btnAClear = new Button("Clear");
		    btnAClear.setBounds(150, 601, 130, 40);
		    btnAClear.addActionListener(new ActionListener() {  
		    	
		    	public void actionPerformed (ActionEvent e) { 
		    		sourceImg = null;	    		
	        		imgContainerA.clear();
	        		imgContainerA.repaint();
		        }   
		    	
		    });
		    
		    this.add(btnAClear);
		    
		    btnBBrowse = new Button("Browse...");
		    btnBBrowse.setBounds(688, 601, 130, 40);
		    btnBBrowse.addActionListener(new ActionListener() {    
		    	public void actionPerformed (ActionEvent e) {    
	        		String filename = showFileDialog("Choose an image file", FileDialog.LOAD);
	        		
	        		try
	        		{
	        			targetImg = ImageIO.read(new File(filename));
	        			targetImgUndo = ImageUtil.deepCopy(targetImg);
	        			
	        			imgContainerB.changeImage(targetImg);
	        			imgContainerB.repaint();
	        		}
	        		catch (IOException ee)
	        		{
	        			System.out.println(String.format("Unable to load the image (%s): (%s)", filename, ee.getMessage()));
	        		}
		        }    
		    }); 
		    
		    this.add(btnBBrowse);
		    
		    btnBClear = new Button("Clear");
		    btnBClear.setBounds(825, 601, 130, 40);
		    btnBClear.addActionListener(new ActionListener() {  
		    	
		    	public void actionPerformed (ActionEvent e) {   
		    		targetImg = null;
		    		targetImgUndo = null;	    		
	        		imgContainerB.clear();
	        		imgContainerB.repaint();
		        }    
		    	
		    });
		    this.add(btnBClear);
		    
		    btnBReset = new Button("Reset");
		    btnBReset.setBounds(962, 601, 130, 40);
		    btnBReset.addActionListener(new ActionListener() { 
		    	
		    	public void actionPerformed (ActionEvent e) {   
		    		if (targetImgUndo != null)
		    		{
		    			targetImg = ImageUtil.deepCopy(targetImgUndo);	    		
			    		imgContainerB.changeImage(targetImg);
		        		imgContainerB.repaint();
		    		}	    		
		        }  
		    	
		    });
		    this.add(btnBReset);
		    		    
		    btnBSave = new Button("Save");
		    btnBSave.setBounds(1223, 601, 130, 40);
		    btnBSave.addActionListener(new ActionListener() { 
		    	
		    	public void actionPerformed (ActionEvent e) {   
		    		String filename = showFileDialog("Choose a directory to save the image in", FileDialog.SAVE);
		    		ImageUtil.dump(targetImg, filename);
		    		
		    		// TODO: increment Untitled Composite
		    	}  
		    });
		    this.add(btnBSave);
		    
			
			// Stores references to image containers that permit application of the lasso tool
			//
			// It's always a good idea to make things as decoupled and abstract as possible,
			// to avoid the "spaghetti code", even within a student project
			ArrayList<Panel> imgContainers = new ArrayList<Panel>();
			
			
			/*
			 * Image Containers
			 */
			
			imgContainerA = new ImageContainer(16, 80, imgContainerW, imgContainerH, sourceImg);
			this.add(imgContainerA);
			imgContainers.add(imgContainerA);
			
			imgContainerB = new ImageContainer(691, 80, imgContainerW, imgContainerH, targetImg);
			this.add(imgContainerB);
			
			
			/*
			 * Miscellaneous
			 */
			
			statusBar = new StatusBar(24, this);
		    this.add(statusBar);
		    
		    
		    /*
			 * Core
			 */
		   
			lasso = new Lasso(this, imgContainers, imgContainerB, statusBar);
			lasso.addEventListener(this);
			
			lassoMouseListener = new LassoMouseListener(lasso);
	    
			this.addMouseListener(lassoMouseListener);
			this.addMouseMotionListener(lassoMouseListener);
		    imgContainerA.addMouseListener(lassoMouseListener);
		    imgContainerA.addMouseMotionListener(lassoMouseListener);		
		    imgContainerB.addMouseListener(lassoMouseListener);
		    imgContainerB.addMouseMotionListener(lassoMouseListener);  	
		    
		    pie = new PoissonImageEditor(); 		    
		}
		catch (Exception e)
		{
			printFatalError(e);
			throw e;
		}
		
		initialized = true;
	}
	
	/*
	 * Supports the opening images from disk and the saving of the composites
	 * 
	 * Citation: https://stackoverflow.com/questions/7211107/how-to-use-filedialog
	 */
	private String showFileDialog(String prompt, int mode)
	{
		FileDialog fd = new FileDialog(this, prompt, mode);
		
		String directory = System.getProperty("user.dir") + 
				(mode == FileDialog.LOAD ? "/sampleImages/" : (mode == FileDialog.SAVE ? "/results/" : ""));	
	    fd.setDirectory(directory);
	    
	    if (mode == FileDialog.SAVE) fd.setFile("Untitled Composite.png");
	    
	    fd.setFilenameFilter(new FilenameFilter() {
	        @Override
	        public boolean accept(File dir, String name) {
	            return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".bmp");
	        }
	    });
	    fd.setVisible(true);
	    
	    return fd.getDirectory() + fd.getFile();
	}
	
	private void printFatalError(Exception e)
	{
		System.err.print("Fatal error: ");
		e.printStackTrace(System.err);
		System.err.println();
	}
}
