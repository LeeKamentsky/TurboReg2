/**
 * 
 */
package ch.epfl.bigwww.turboreg2;

import org.scijava.Cancelable;
import net.imagej.Dataset;
import net.imagej.display.DataView;
import net.imagej.display.DatasetView;
import net.imagej.display.ImageDisplay;
import net.imagej.display.OverlayService;
import org.scijava.module.ModuleException;
import org.scijava.util.RealRect;

import java.util.Stack;

import org.scijava.app.StatusService;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

/**
 * @author Philippe Thevenaz
 * @author Lee Kamentsky
 *
 * The TurboRegInterval acts as a common base class for
 * TurboRegImage and TurboRegMask, supplying some
 * code that's generally useful in bridging ImageJ 2
 * to the TurboReg infrastructure.
 */
class TurboRegInterval<PYRAMID_TYPE> {

	protected final Stack<PYRAMID_TYPE> pyramid = new Stack<PYRAMID_TYPE>();
	protected int width;
	protected int height;
	protected long xOffset;
	protected long yOffset;
	protected int pyramidDepth;
	private StatusService statusService;
	private int workload;
	private int progress;
	private Cancelable executionContext;
	protected float[] coefficient;
	protected boolean isTarget;
	/*********************************************************************
	 Minimal linear dimension of an image in the multiresolution pyramid.
	 ********************************************************************/
	public static final int MIN_SIZE = 12;

	/*********************************************************************
	Return the full-size image height.
	 ********************************************************************/
	public int getHeight() {
		return(height);
	} /* end getHeight */

	/*********************************************************************
	Return the depth of the image pyramid. A depth <code>1</code> means
	that one coarse resolution level is present in the stack. The
	full-size level is not placed on the stack.
	 ********************************************************************/
	public int getPyramidDepth() {
		return(pyramidDepth);
	} /* end getPyramidDepth */

	public boolean isTarget() {
		return isTarget;
	}
	/**
	 * The execution context is the parent process or command that
	 * is using this <code>TurboRegImage</code>. The parent may
	 * be canceled at any point and this will cause processing
	 * in the <code>TurboRegImage</code> to abort
	 * 
	 * @param executionContext parent that will control cancellation
	 */
	public void setExecutionContext(Cancelable executionContext) {
		this.executionContext = executionContext;
	}

	/**
	 * @return true if the parent execution context (if any) has
	 *         been canceled.
	 */
	protected boolean isCanceled() {
		if (executionContext == null) return false;
		return executionContext.isCanceled();
	}

	/*********************************************************************
	Return the full-size image width.
	 ********************************************************************/
	public int getWidth() {
		return(width);
	} /* end getWidth */

	/**
	 * @return the X offset of the bounding rectangle of the region of interest
	 */
	public long getXOffset() {
		return xOffset;
	}
	
	/**
	 * @return the Y offset of the bounding rectangle of the region of interest
	 */
	public long getYOffset() {
		return yOffset;
	}
	
	public long clipX(long x) {
		return (x < xOffset)? xOffset: (x>xOffset+width)? xOffset+width: x;
	}
	public long clipY(long y) {
		return (y < yOffset)? yOffset: (y>yOffset+height)? yOffset+height: y;
	}
	public double clipX(double x) {
		return (x < xOffset)? xOffset: (x>xOffset+width)? xOffset+width: x;
	}
	public double clipY(double y) {
		return (y < yOffset)? yOffset: (y>yOffset+height)? yOffset+height: y;
	}
	/**
	 * Use this status bar when displaying progress
	 * 
	 * @param statusBar
	 */
	public void setStatusService(StatusService statusService) {
		this.statusService = statusService;
	}

	/**
	 * Add a workload for the status bar
	 * 
	 * @param message display this message indicating what's being done
	 * @param workload number of units
	 */
	protected void addWorkload(String message, int workload) {
		this.workload = workload;
		this.progress = 0;
		if (statusService != null) {
			statusService.showStatus(0, workload, message);
		}
	}

	/**
	 * Record a unit of work for current workload
	 */
	protected void workloadStep() {
		progress++;
		if (statusService != null) {
			statusService.showProgress(progress, workload);
		}
	}

	/**
	 * Report that a workload is finished
	 */
	protected void workloadDone() {
		if (statusService != null) {
			statusService.showProgress(workload, workload);
		}
	}

	/*********************************************************************
	Sets the depth up to which the pyramids should be computed.
	@see turboRegImage#getImage()
	 ********************************************************************/
	void setPyramidDepth(final int pyramidDepth) {
		this.pyramidDepth = pyramidDepth;
	} /* end setPyramidDepth */

	/*********************************************************************
	Return the pyramid as a <code>Stack</code> object. 
	
	The organization
	of the stack depends on whether the pyramid is a <code>TurboRegImage</code>
	or <code>TurboRegMask</code> pyramid.
	<br>
	<em>TurboRegImage organization</em><br>
	The <code>TurboRegImage</code>
	object corresponds to the target or the source image, and on the
	transformation (ML* = {<code>TRANSLATION</code>,<code>RIGID_BODY</code>,
	<code>SCALED_ROTATION</code>, <code>AFFINE</code>} vs.
	ML = {<code>BILINEAR<code>}). A single pyramid level consists of
	<p>
	<table border="1">
	<tr><th><code>isTarget</code></th>
	<th>ML*</th>
	<th>ML</th></tr>
	<tr><td>true</td>
	<td>width<br>height<br>B-spline coefficients</td>
	<td>width<br>height<br>samples</td></tr>
	<tr><td>false</td>
	<td>width<br>height<br>samples<br>horizontal gradients<br>
	vertical gradients</td>
	<td>width<br>height<br>B-spline coefficients</td></tr>
	</table>
	<br>
	<em>TurboRegMask organization</em><br>
	A single pyramid
	level consists of
	<p>
	<table border="1">
	<tr><th><code>isTarget</code></th>
	<th>ML*</th>
	<th>ML</th></tr>
	<tr><td>true</td>
	<td>mask samples</td>
	<td>mask samples</td></tr>
	<tr><td>false</td>
	<td>mask samples</td>
	<td>mask samples</td></tr>
	</table>
	 ********************************************************************/
	public Stack<PYRAMID_TYPE> getPyramid() {
		return(pyramid);
	} /* end getPyramid */

	/**
	 * Compute the number of pyramid planes to compute
	 * given the source and target image dimensions
	 *  
	 * @return
	 * @throws ModuleException 
	 */
	static int getPyramidDepth(ImageDisplay source, ImageDisplay target, OverlayService overlayService)
			throws ModuleException {
		long sw, sh, tw, th;
		if (overlayService != null) {
			final RealRect rSource = overlayService.getSelectionBounds(source);
			final RealRect rTarget = overlayService.getSelectionBounds(target);
			sw = (long)rSource.width;
			sh = (long)rSource.height;
			tw = (long)rTarget.width;
			th = (long)rTarget.height;
		} else {
			sw = source.dimension(0);
			sh = source.dimension(1);
			tw = target.dimension(0);
			th = target.dimension(1);
		}
		int pyramidDepth = 1;
		while (((2 * TurboRegInterval.MIN_SIZE) <= sw)
			&& ((2 * TurboRegInterval.MIN_SIZE) <= sh)
			&& ((2 * TurboRegInterval.MIN_SIZE) <= tw)
			&& ((2 * TurboRegInterval.MIN_SIZE) <= th)) {
			sw /= 2;
			sh /= 2;
			tw /= 2;
			th /= 2;
			pyramidDepth++;
		}
		return(pyramidDepth);
		} /* end getPyramidDepth */

	/**
	 * Extract the xy plane selected by the user in the image display
	 * 
	 * @param display an image display
	 * @return the selected plane in the image display as a RandomAccesibleInterval
	 * @throws ModuleException
	 */
	static RandomAccessibleInterval<? extends RealType<?>> getImageDisplayPlane(
			ImageDisplay display,
			OverlayService overlayService)
			throws ModuleException {
		DataView view = display.getActiveView();
		if ((view == null) || !(view instanceof DatasetView)) {
			throw new ModuleException("The display doesn't have an active dataset (image)");
		}
		DatasetView dsView = (DatasetView)view;
		Dataset ds = dsView.getData();
		RandomAccessibleInterval<? extends RealType<?>> data = ds.getImgPlus();
		return getPlane(display, data, overlayService);
	}
	
	/**
	 * Extract the currently-selected plane out of any <code>RandomAccessibleInterval</code>
	 * associated with this display.
	 * 
	 * @param <T> the data type of the <code>RandomAccessibleInterval</code>
	 * @param display the display, with a plane selected in its DatasetView
	 * @param data the RandomAccessibleInterval to be projected
	 * @param overlayService the overlay service (which we use to do some overlay math)
	 * @return the projected RandomAccessibleInterval (a 2-d plane)
	 * @throws ModuleException if no active view
	 */
	static RandomAccessibleInterval<? extends RealType<?>> 
		getPlane(ImageDisplay display, 
				RandomAccessibleInterval<? extends RealType<?>> data,
				OverlayService overlayService) throws ModuleException {
		DataView view = display.getActiveView();
		if ((view == null) || !(view instanceof DatasetView)) {
			throw new ModuleException("The display doesn't have an active dataset (image)");
		}
		DatasetView dsView = (DatasetView)view;
		/*
		 * First, get the currently selected plane from the view by
		 * creating one hyperslice per dimension > 2.
		 */
		for (int i=dsView.numDimensions()-1; i>=2; i--) {
			data = Views.hyperSlice(data, i, dsView.getLongPosition(i));
		}
		/*
		 * Then constrain the plane to the bounds.
		 */
		if (overlayService != null) {
			RealRect bounds = overlayService.getSelectionBounds(display);
			final long [] min = new long [] { 
					(long)Math.ceil(bounds.x), (long)Math.ceil(bounds.y) };
			final long [] max = new long [] { 
					(long)Math.floor(bounds.x + bounds.width), 
					(long)Math.floor(bounds.y + bounds.height) };
			return Views.interval(data, min, max);
		}
		return data;
	}
}
