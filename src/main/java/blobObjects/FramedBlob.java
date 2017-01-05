package blobObjects;

import java.util.ArrayList;

import mpicbg.spim.segmentation.SnakeObject;
public class FramedBlob {

	
	public final int frame;
	public  SnakeObject Blobs;
	
	
	public FramedBlob( final int frame, SnakeObject Blobs ){
		
		this.frame = frame;
		this.Blobs = Blobs;
		
	}
	
	
}