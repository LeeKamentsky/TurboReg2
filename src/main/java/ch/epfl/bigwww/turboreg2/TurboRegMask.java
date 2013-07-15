/**
 * 
 */
package ch.epfl.bigwww.turboreg2;

import imagej.data.display.ImageDisplay;
import imagej.data.display.OverlayService;
import imagej.data.overlay.Overlay;
import imagej.plugin.PluginException;
import imagej.util.RealRect;

import java.util.ArrayList;
import java.util.List;

import net.imglib2.Cursor;
import net.imglib2.FinalInterval;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.FloatArray;
import net.imglib2.roi.IterableRegionOfInterest;
import net.imglib2.roi.RegionOfInterest;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

/**
 * @author Phillipe Thevenaz
 * @author Lee Kamentsky
 *
 */
/*********************************************************************
This class is responsible for the mask preprocessing that takes
place concurrently with user-interface events. It contains methods
to compute the mask pyramids.
 ********************************************************************/
class TurboRegMask
extends TurboRegInterval<float []>
implements
Runnable

{ /* begin class turboRegMask */

	/*....................................................................
	Private variables
	....................................................................*/
	private float[] mask;

	/*....................................................................
	Public methods
....................................................................*/

	/*********************************************************************
Set to <code>true</code> every pixel of the full-size mask.
	 ********************************************************************/
	public void clearMask (
	) {
		int k = 0;
		addWorkload("Clearing mask", height);
		for (int y = 0; (y < height); y++) {
			for (int x = 0; (x < width); x++) {
				mask[k++] = 1.0F;
			}
			workloadStep();
		}
		workloadDone();
	} /* end clearMask */

	/*********************************************************************
Return the full-size mask array.
	 ********************************************************************/
	public float[] getMask (
	) {
		return(mask);
	} /* end getMask */

	/*********************************************************************
	Start the mask precomputations, which are interruptible.
	 ********************************************************************/
	public void run (
	) {
		buildPyramid();
	} /* end run */

	/*********************************************************************
	Converts the pixel array of the incoming <code>ImageDisplay</code>
	object into a local <code>boolean</code> array.
	@param imp <code>ImagePlus</code> object to preprocess.
	 * @throws PluginException 
	 ********************************************************************/
	public void setData (
			final ImageDisplay display,
			OverlayService overlayService
	) throws PluginException {
		RealRect bounds = overlayService.getSelectionBounds(display);
		width = (int) bounds.width + 1;
		height = (int) bounds.height + 1;
		xOffset = (long) bounds.x;
		yOffset = (long) bounds.y;
		mask = new float [width * height];
		List<Overlay> overlays = overlayService.getOverlays(display, true);
		List<RegionOfInterest> rois = new ArrayList<RegionOfInterest>();
		for (Overlay overlay:overlays) {
			final RegionOfInterest roi = overlay.getRegionOfInterest();
			if (roi != null) rois.add(roi);
		}
		if (rois.size() == 0) {
			clearMask();
			return;
		}
		int k = 0;
		addWorkload("Computing mask", rois.size());
		/*
		 * Note - this depends heavily on the raster order of the ArrayImg
		 */
		ArrayImg<FloatType, FloatArray> imask = ArrayImgs.floats(mask, width, height);
		RandomAccessibleInterval<FloatType> raMask = Views.translate(imask, xOffset, yOffset);
		for (RegionOfInterest roi:rois) {
			if (roi instanceof IterableRegionOfInterest) {
				final IterableRegionOfInterest iroi = (IterableRegionOfInterest)roi;
				final IterableInterval<FloatType> ii = iroi.getIterableIntervalOverROI(raMask);
				Cursor<FloatType> c = ii.cursor();
				while(c.hasNext()) {
					c.next().set((float)1.0);
				}
			} else {
				Cursor<FloatType> c = Views.iterable(raMask).cursor();
				double [] position = new double[2];
				while(c.hasNext()) {
					FloatType t = c.next();
					c.localize(position);
					if (roi.contains(position)) {
						t.set((float)1.0);
					}
				}
			}
			workloadStep();
		}
		workloadDone();
	} /* end turboRegMask */

	/*....................................................................
	Private methods
....................................................................*/

	/*------------------------------------------------------------------*/
	private void buildPyramid (
	) {
		int fullWidth;
		int fullHeight;
		float[] fullMask = mask;
		int halfWidth = width;
		int halfHeight = height;
		for (int depth = 1; ((depth < pyramidDepth) && (!isCanceled()));
		depth++) {
			fullWidth = halfWidth;
			fullHeight = halfHeight;
			halfWidth /= 2;
			halfHeight /= 2;
			final float[] halfMask = getHalfMask2D(fullMask, fullWidth, fullHeight);
			pyramid.push(halfMask);
			fullMask = halfMask;
		}
	} /* end buildPyramid */

	/*------------------------------------------------------------------*/
	private float[] getHalfMask2D (
			final float[] fullMask,
			final int fullWidth,
			final int fullHeight
	) {
		final int halfWidth = fullWidth / 2;
		final int halfHeight = fullHeight / 2;
		final boolean oddWidth = ((2 * halfWidth) != fullWidth);
		int workload = 2 * halfHeight;
		addWorkload("Reducing mask", workload);
		final float[] halfMask = new float[halfWidth * halfHeight];
		int k = 0;
		for (int y = 0; ((y < halfHeight) && (!isCanceled())); y++) {
			for (int x = 0; (x < halfWidth); x++) {
				halfMask[k++] = 0.0F;
			}
			workloadStep();
			workload--;
		}
		k = 0;
		int n = 0;
		for (int y = 0; ((y < (halfHeight - 1)) && (!isCanceled())); y++) {
			for (int x = 0; (x < (halfWidth - 1)); x++) {
				halfMask[k] += Math.abs(fullMask[n++]);
				halfMask[k] += Math.abs(fullMask[n]);
				halfMask[++k] += Math.abs(fullMask[n++]);
			}
			halfMask[k] += Math.abs(fullMask[n++]);
			halfMask[k++] += Math.abs(fullMask[n++]);
			if (oddWidth) {
				n++;
			}
			for (int x = 0; (x < (halfWidth - 1)); x++) {
				halfMask[k - halfWidth] += Math.abs(fullMask[n]);
				halfMask[k] += Math.abs(fullMask[n++]);
				halfMask[k - halfWidth] += Math.abs(fullMask[n]);
				halfMask[k - halfWidth + 1] += Math.abs(fullMask[n]);
				halfMask[k] += Math.abs(fullMask[n]);
				halfMask[++k] += Math.abs(fullMask[n++]);
			}
			halfMask[k - halfWidth] += Math.abs(fullMask[n]);
			halfMask[k] += Math.abs(fullMask[n++]);
			halfMask[k - halfWidth] += Math.abs(fullMask[n]);
			halfMask[k++] += Math.abs(fullMask[n++]);
			if (oddWidth) {
				n++;
			}
			k -= halfWidth;
			workloadStep();
			workload--;
		}
		for (int x = 0; (x < (halfWidth - 1)); x++) {
			halfMask[k] += Math.abs(fullMask[n++]);
			halfMask[k] += Math.abs(fullMask[n]);
			halfMask[++k] += Math.abs(fullMask[n++]);
		}
		halfMask[k] += Math.abs(fullMask[n++]);
		halfMask[k++] += Math.abs(fullMask[n++]);
		if (oddWidth) {
			n++;
		}
		k -= halfWidth;
		for (int x = 0; (x < (halfWidth - 1)); x++) {
			halfMask[k] += Math.abs(fullMask[n++]);
			halfMask[k] += Math.abs(fullMask[n]);
			halfMask[++k] += Math.abs(fullMask[n++]);
		}
		halfMask[k] += Math.abs(fullMask[n++]);
		halfMask[k] += Math.abs(fullMask[n]);
		workloadStep();
		workload--;
		workloadDone();
		return(halfMask);
	} /* end getHalfMask2D */

}