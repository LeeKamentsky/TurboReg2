package ch.epfl.bigwww.turboreg2;

import java.util.HashMap;
import java.util.Set;

import imagej.data.table.DoubleColumn;
import imagej.data.table.ResultsTable;
import imagej.util.ColorRGB;
import imagej.util.Colors;
import net.imglib2.Point;
import net.imglib2.RealPoint;

/*********************************************************************
 * @author Philippe Thevenaz
 * @author Lee Kamentsky
 * 
 * This class implements the graphic interactions when dealing with
 * landmarks.
 * 
 * TODO: Implement ImageJ 2.0 drawing
 ********************************************************************/
class TurboRegPointHandler
{ /* begin class turboRegPointHandler */

	/*....................................................................
	Public variables
	....................................................................*/

	/**
	 * The name of the X coordinate table column for the source image
	 */
	public static final String SOURCE_X = "sourceX";
	/**
	 * The name of the Y coordinate table column for the source image
	 */
	public static final String SOURCE_Y = "sourceY";
	/**
	 * The name of the X coordinate table column for the target image
	 */
	public static final String TARGET_X = "targetX";
	/**
	 * The name of the Y coordinate table column for the target image
	 */
	public static final String TARGET_Y = "targetY";
	/*********************************************************************
	The magnifying tool is set in eleventh position to be coherent with
	ImageJ.
	 ********************************************************************/
	public static final int MAGNIFIER = 11;

	/*********************************************************************
	The moving tool is set in second position to be coherent with the
	<code>PointPicker_</code> plugin.
	 ********************************************************************/
	public static final int MOVE_CROSS = 1;

	/*********************************************************************
	The number of points we are willing to deal with is at most
	<code>4</code>.
	@see turboRegDialog#transformation
	 ********************************************************************/
	public static final int NUM_POINTS = 4;

	/*....................................................................
	Private variables
	....................................................................*/

	/*********************************************************************
	The drawn landmarks fit in a 11x11 matrix.
	 ********************************************************************/
	private static final int CROSS_HALFSIZE = 5;

	/*********************************************************************
	The golden ratio mathematical constant determines where to put the
	initial landmarks.
	 ********************************************************************/
	private static final double GOLDEN_RATIO = 0.5 * (Math.sqrt(5.0) - 1.0);

	private final ColorRGB[] spectrum = new ColorRGB[NUM_POINTS];
	private double[][] precisionPoint;
	private TransformationType transformation;
	final private TurboRegImage interval;
	private int currentPoint = 0;
	private boolean interactive = true;
	private boolean started = false;
	final private double xOffset;
	final private double yOffset;

	/*....................................................................
	Public methods
	....................................................................*/
	public TurboRegPointHandler(TransformationType transformation, TurboRegImage interval) {
		this.transformation = transformation;
		precisionPoint = new double [pointCount(transformation)][2];
		this.interval = interval;
		xOffset = 0;
		yOffset = 0;
	}
/*	*//*********************************************************************
	Draw the landmarks. Outline the current point if the window has focus.
	@param g Graphics environment.
	 ********************************************************************//*
	public void draw (
			final Graphics g
	) {
		if (started) {
			final double mag = ic.getMagnification();
			final int dx = (int)(mag / 2.0);
			final int dy = (int)(mag / 2.0);
			Point p;
			if (transformation == turboRegDialog.RIGID_BODY) {
				if (currentPoint == 0) {
					for (int k = 1; (k < transformation); k++) {
						p = point[k];
						g.setColor(spectrum[k]);
						g.fillRect(ic.screenX(p.x) - 2 + dx,
								ic.screenY(p.y) - 2 + dy, 5, 5);
					}
					drawHorizon(g);
					p = point[0];
					g.setColor(spectrum[0]);
					if (WindowManager.getCurrentImage() == imp) {
						g.drawLine(ic.screenX(p.x - CROSS_HALFSIZE - 1) + dx,
								ic.screenY(p.y - 1) + dy,
								ic.screenX(p.x - 1) + dx,
								ic.screenY(p.y - 1) + dy);
						g.drawLine(ic.screenX(p.x - 1) + dx,
								ic.screenY(p.y - 1) + dy,
								ic.screenX(p.x - 1) + dx,
								ic.screenY(p.y - CROSS_HALFSIZE - 1) + dy);
						g.drawLine(ic.screenX(p.x - 1) + dx,
								ic.screenY(p.y - CROSS_HALFSIZE - 1) + dy,
								ic.screenX(p.x + 1) + dx,
								ic.screenY(p.y - CROSS_HALFSIZE - 1) + dy);
						g.drawLine(ic.screenX(p.x + 1) + dx,
								ic.screenY(p.y - CROSS_HALFSIZE - 1) + dy,
								ic.screenX(p.x + 1) + dx,
								ic.screenY(p.y - 1) + dy);
						g.drawLine(ic.screenX(p.x + 1) + dx,
								ic.screenY(p.y - 1) + dy,
								ic.screenX(p.x + CROSS_HALFSIZE + 1) + dx,
								ic.screenY(p.y - 1) + dy);
						g.drawLine(ic.screenX(p.x + CROSS_HALFSIZE + 1) + dx,
								ic.screenY(p.y - 1) + dy,
								ic.screenX(p.x + CROSS_HALFSIZE + 1) + dx,
								ic.screenY(p.y + 1) + dy);
						g.drawLine(ic.screenX(p.x + CROSS_HALFSIZE + 1) + dx,
								ic.screenY(p.y + 1) + dy,
								ic.screenX(p.x + 1) + dx,
								ic.screenY(p.y + 1) + dy);
						g.drawLine(ic.screenX(p.x + 1) + dx,
								ic.screenY(p.y + 1) + dy,
								ic.screenX(p.x + 1) + dx,
								ic.screenY(p.y + CROSS_HALFSIZE + 1) + dy);
						g.drawLine(ic.screenX(p.x + 1) + dx,
								ic.screenY(p.y + CROSS_HALFSIZE + 1) + dy,
								ic.screenX(p.x - 1) + dx,
								ic.screenY(p.y + CROSS_HALFSIZE + 1) + dy);
						g.drawLine(ic.screenX(p.x - 1) + dx,
								ic.screenY(p.y + CROSS_HALFSIZE + 1) + dy,
								ic.screenX(p.x - 1) + dx,
								ic.screenY(p.y + 1) + dy);
						g.drawLine(ic.screenX(p.x - 1) + dx,
								ic.screenY(p.y + 1) + dy,
								ic.screenX(p.x - CROSS_HALFSIZE - 1) + dx,
								ic.screenY(p.y + 1) + dy);
						g.drawLine(ic.screenX(p.x - CROSS_HALFSIZE - 1) + dx,
								ic.screenY(p.y + 1) + dy,
								ic.screenX(p.x - CROSS_HALFSIZE - 1) + dx,
								ic.screenY(p.y - 1) + dy);
						if (1.0 < ic.getMagnification()) {
							g.drawLine(ic.screenX(p.x - CROSS_HALFSIZE) + dx,
									ic.screenY(p.y) + dy,
									ic.screenX(p.x + CROSS_HALFSIZE) + dx,
									ic.screenY(p.y) + dy);
							g.drawLine(ic.screenX(p.x) + dx,
									ic.screenY(p.y - CROSS_HALFSIZE) + dy,
									ic.screenX(p.x) + dx,
									ic.screenY(p.y + CROSS_HALFSIZE) + dy);
						}
					}
					else {
						g.drawLine(ic.screenX(p.x - CROSS_HALFSIZE + 1) + dx,
								ic.screenY(p.y - CROSS_HALFSIZE + 1) + dy,
								ic.screenX(p.x + CROSS_HALFSIZE - 1) + dx,
								ic.screenY(p.y + CROSS_HALFSIZE - 1) + dy);
						g.drawLine(ic.screenX(p.x - CROSS_HALFSIZE + 1) + dx,
								ic.screenY(p.y + CROSS_HALFSIZE - 1) + dy,
								ic.screenX(p.x + CROSS_HALFSIZE - 1) + dx,
								ic.screenY(p.y - CROSS_HALFSIZE + 1) + dy);
					}
				}
				else {
					p = point[0];
					g.setColor(spectrum[0]);
					g.drawLine(ic.screenX(p.x - CROSS_HALFSIZE) + dx,
							ic.screenY(p.y) + dy,
							ic.screenX(p.x + CROSS_HALFSIZE) + dx,
							ic.screenY(p.y) + dy);
					g.drawLine(ic.screenX(p.x) + dx,
							ic.screenY(p.y - CROSS_HALFSIZE) + dy,
							ic.screenX(p.x) + dx,
							ic.screenY(p.y + CROSS_HALFSIZE) + dy);
					drawHorizon(g);
					if (WindowManager.getCurrentImage() == imp) {
						drawArcs(g);
						for (int k = 1; (k < transformation); k++) {
							p = point[k];
							g.setColor(spectrum[k]);
							if (k == currentPoint) {
								g.drawRect(ic.screenX(p.x) - 3 + dx,
										ic.screenY(p.y) - 3 + dy, 6, 6);
							}
							else {
								g.fillRect(ic.screenX(p.x) - 2 + dx,
										ic.screenY(p.y) - 2 + dy, 5, 5);
							}
						}
					}
					else {
						for (int k = 1; (k < transformation); k++) {
							p = point[k];
							g.setColor(spectrum[k]);
							if (k == currentPoint) {
								g.drawRect(ic.screenX(p.x) - 2 + dx,
										ic.screenY(p.y) - 2 + dy, 5, 5);
							}
							else {
								g.fillRect(ic.screenX(p.x) - 2 + dx,
										ic.screenY(p.y) - 2 + dy, 5, 5);
							}
						}
					}
				}
			}
			else {
				for (int k = 0; (k < (transformation / 2)); k++) {
					p = point[k];
					g.setColor(spectrum[k]);
					if (k == currentPoint) {
						if (WindowManager.getCurrentImage() == imp) {
							g.drawLine(ic.screenX(p.x - CROSS_HALFSIZE - 1) + dx,
									ic.screenY(p.y - 1) + dy,
									ic.screenX(p.x - 1) + dx,
									ic.screenY(p.y - 1) + dy);
							g.drawLine(ic.screenX(p.x - 1) + dx,
									ic.screenY(p.y - 1) + dy,
									ic.screenX(p.x - 1) + dx,
									ic.screenY(p.y - CROSS_HALFSIZE - 1) + dy);
							g.drawLine(ic.screenX(p.x - 1) + dx,
									ic.screenY(p.y - CROSS_HALFSIZE - 1) + dy,
									ic.screenX(p.x + 1) + dx,
									ic.screenY(p.y - CROSS_HALFSIZE - 1) + dy);
							g.drawLine(ic.screenX(p.x + 1) + dx,
									ic.screenY(p.y - CROSS_HALFSIZE - 1) + dy,
									ic.screenX(p.x + 1) + dx,
									ic.screenY(p.y - 1) + dy);
							g.drawLine(ic.screenX(p.x + 1) + dx,
									ic.screenY(p.y - 1) + dy,
									ic.screenX(p.x + CROSS_HALFSIZE + 1) + dx,
									ic.screenY(p.y - 1) + dy);
							g.drawLine(ic.screenX(p.x + CROSS_HALFSIZE + 1) + dx,
									ic.screenY(p.y - 1) + dy,
									ic.screenX(p.x + CROSS_HALFSIZE + 1) + dx,
									ic.screenY(p.y + 1) + dy);
							g.drawLine(ic.screenX(p.x + CROSS_HALFSIZE + 1) + dx,
									ic.screenY(p.y + 1) + dy,
									ic.screenX(p.x + 1) + dx,
									ic.screenY(p.y + 1) + dy);
							g.drawLine(ic.screenX(p.x + 1) + dx,
									ic.screenY(p.y + 1) + dy,
									ic.screenX(p.x + 1) + dx,
									ic.screenY(p.y + CROSS_HALFSIZE + 1) + dy);
							g.drawLine(ic.screenX(p.x + 1) + dx,
									ic.screenY(p.y + CROSS_HALFSIZE + 1) + dy,
									ic.screenX(p.x - 1) + dx,
									ic.screenY(p.y + CROSS_HALFSIZE + 1) + dy);
							g.drawLine(ic.screenX(p.x - 1) + dx,
									ic.screenY(p.y + CROSS_HALFSIZE + 1) + dy,
									ic.screenX(p.x - 1) + dx,
									ic.screenY(p.y + 1) + dy);
							g.drawLine(ic.screenX(p.x - 1) + dx,
									ic.screenY(p.y + 1) + dy,
									ic.screenX(p.x - CROSS_HALFSIZE - 1) + dx,
									ic.screenY(p.y + 1) + dy);
							g.drawLine(ic.screenX(p.x - CROSS_HALFSIZE - 1) + dx,
									ic.screenY(p.y + 1) + dy,
									ic.screenX(p.x - CROSS_HALFSIZE - 1) + dx,
									ic.screenY(p.y - 1) + dy);
							if (1.0 < ic.getMagnification()) {
								g.drawLine(ic.screenX(p.x - CROSS_HALFSIZE) + dx,
										ic.screenY(p.y) + dy,
										ic.screenX(p.x + CROSS_HALFSIZE) + dx,
										ic.screenY(p.y) + dy);
								g.drawLine(ic.screenX(p.x) + dx,
										ic.screenY(p.y - CROSS_HALFSIZE) + dy,
										ic.screenX(p.x) + dx,
										ic.screenY(p.y + CROSS_HALFSIZE) + dy);
							}
						}
						else {
							g.drawLine(ic.screenX(p.x - CROSS_HALFSIZE + 1) + dx,
									ic.screenY(p.y - CROSS_HALFSIZE + 1) + dy,
									ic.screenX(p.x + CROSS_HALFSIZE - 1) + dx,
									ic.screenY(p.y + CROSS_HALFSIZE - 1) + dy);
							g.drawLine(ic.screenX(p.x - CROSS_HALFSIZE + 1) + dx,
									ic.screenY(p.y + CROSS_HALFSIZE - 1) + dy,
									ic.screenX(p.x + CROSS_HALFSIZE - 1) + dx,
									ic.screenY(p.y - CROSS_HALFSIZE + 1) + dy);
						}
					}
					else {
						g.drawLine(ic.screenX(p.x - CROSS_HALFSIZE) + dx,
								ic.screenY(p.y) + dy,
								ic.screenX(p.x + CROSS_HALFSIZE) + dx,
								ic.screenY(p.y) + dy);
						g.drawLine(ic.screenX(p.x) + dx,
								ic.screenY(p.y - CROSS_HALFSIZE) + dy,
								ic.screenX(p.x) + dx,
								ic.screenY(p.y + CROSS_HALFSIZE) + dy);
					}
				}
			}
			if (updateFullWindow) {
				updateFullWindow = false;
				imp.draw();
			}
		}
	}  end draw 
*/
/*	*//*********************************************************************
Set the current point as that which is closest to (x, y).
@param x Horizontal coordinate in canvas units.
@param y Vertical coordinate in canvas units.
	 ********************************************************************//*
	public int findClosest (
			int x,
			int y
	) {
		x = ic.offScreenX(x);
		y = ic.offScreenY(y);
		int closest = 0;
		Point p = point[closest];
		double distance = (double)(x - p.x) * (double)(x - p.x)
		+ (double)(y - p.y) * (double)(y - p.y);
		double candidate;
		if (transformation == turboRegDialog.RIGID_BODY) {
			for (int k = 1; (k < transformation); k++) {
				p = point[k];
				candidate = (double)(x - p.x) * (double)(x - p.x)
				+ (double)(y - p.y) * (double)(y - p.y);
				if (candidate < distance) {
					distance = candidate;
					closest = k;
				}
			}
		}
		else {
			for (int k = 1; (k < (transformation / 2)); k++) {
				p = point[k];
				candidate = (double)(x - p.x) * (double)(x - p.x)
				+ (double)(y - p.y) * (double)(y - p.y);
				if (candidate < distance) {
					distance = candidate;
					closest = k;
				}
			}
		}
		currentPoint = closest;
		return(currentPoint);
	}  end findClosest 

*/	
	/*********************************************************************
	Return the current point as a <code>Point</code> object.
	 ********************************************************************/
	public RealPoint getPoint (
	) {
		return new RealPoint(precisionPoint[currentPoint][0], 
							 precisionPoint[currentPoint][1]);
	} /* end getPoint */

	/**
	 * Return the number of points for the given transformation
	 * @param t transformation type in question
	 * @return number of points needed to constrain the transformation
	 */
	static public int pointCount(TransformationType t) {
		switch(t) {
		case TRANSLATION: return 1;
		case RIGID_BODY: return 3;
		case SCALED_ROTATION: return 2;
		case AFFINE: return 3;
		case BILINEAR: return 4;
		}
		return 0;
	}
	/*********************************************************************
	Return all landmarks as an array <code>double[transformation / 2][2]</code>,
	except for a rigid-body transformation for which the array has size
	<code>double[3][2]</code>.
	 ********************************************************************/
	public double[][] getPoints (
	) {
		return(precisionPoint);
	} /* end getPoints */

	/*********************************************************************
	Modify the location of the current point. Clip the admissible range
	to the image size.
	@param x Desired new horizontal coordinate in pixel units.
	@param y Desired new vertical coordinate in pixel units.
	 ********************************************************************/
	public void movePoint (
			double x,
			double y
	) {
		interactive = true;
		x = interval.clipX(x);
		y = interval.clipY(y);
		if ((transformation == TransformationType.RIGID_BODY) && (currentPoint != 0)) {
			double [] other = precisionPoint[3-currentPoint];
			if (0.5 * Math.sqrt((other[0]-x)*(other[0]-x) + (other[1]-y)*(other[1]-y)) <= CROSS_HALFSIZE) {
				return;
			}
		}
		precisionPoint[currentPoint][0] = x;
		precisionPoint[currentPoint][1] = y;
	} /* end movePoint */

	/*********************************************************************
	* Set a new current point.
	* @param currentPoint New current point index.
	 ********************************************************************/
	public void setCurrentPoint (
			final int currentPoint
	) {
		this.currentPoint = currentPoint;
	} /* end setCurrentPoint */

	/*********************************************************************
	 * Set new position for all landmarks, without clipping.
	 * @param precisionPoint New coordinates in canvas units.
	 ********************************************************************/
	public void setPoints (
			final double[][] precisionPoint
	) {
		interactive = false;
		for (int i=0; i<precisionPoint.length; i++) {
			this.precisionPoint[i][0] = precisionPoint[i][0];
			this.precisionPoint[i][1] = precisionPoint[i][1];
		}
	} /* end setPoints */

	/*********************************************************************
	 * Reset the landmarks to their initial position for the given
	 * transformation.
	 * @param transformation Transformation code.
	 ********************************************************************/
	public void setTransformation (
			final TransformationType transformation
	) {
		interactive = true;
		this.transformation = transformation;
		final int width = interval.getWidth();
		final int height = interval.getHeight();
		final double xMid = 0.5 * (double)width;
		final double yMid = 0.5 * (double)height;
		final double xMin = 0.25 * GOLDEN_RATIO * (double)width;
		final double yMin = 0.25 * GOLDEN_RATIO * (double)height;
		final double xMax = (double)width - 0.25 * GOLDEN_RATIO * (double)width;
		final double yMax = (double)height - 0.25 * GOLDEN_RATIO * (double)height;
		currentPoint = 0;
		switch (transformation) {
		case TRANSLATION: {
			precisionPoint = new double [][] {{xMid, yMid}};
			break;
		}
		case RIGID_BODY: {
			precisionPoint = new double [][] { { xMid, yMid}, {xMid, yMin}, {xMid, yMax}};
			break;
		}
		case SCALED_ROTATION: {
			precisionPoint = new double [][] { {xMin, yMid}, {xMax, yMid} };
			break;
		}
		case AFFINE: {
			precisionPoint = new double [][] { { xMid, yMin}, {xMin, yMax}, {xMax, yMax} };
			break;
		}
		case BILINEAR: {
			precisionPoint = new double[][] { { xMin, yMin }, { xMin, yMax}, {xMax, yMin}, {xMax, yMax}};
			break;
		}
		}
		setSpectrum();
	} /* end setTransformation */

	/**
	 * Initialize using an external point array.
	 * 
	 * @param precisionPoint array of landmark points
	 * @param transformation transformation to be applied
	 * @param xOffset x offset from the origin of the cropped region
	 * @param yOffset y offset from the origin of the cropped region
	 */
	public TurboRegPointHandler (
			final double[][] precisionPoint,
			final TransformationType transformation,
			double xOffset,
			double yOffset
	) {
		this.transformation = transformation;
		this.precisionPoint = precisionPoint;
		this.interval = null;
		interactive = false;
		this.xOffset = xOffset;
		this.yOffset = yOffset;
	} /* end turboRegPointHandler */

	/**
	 * Initialize using point values in a results table
	 * @param transformation the transformation type
	 * @param resultsTable a results table containing the coordinates.
	 *                     We look for either columns of sourceX and sourceY
	 *                     or targetX and targetY.
	 * @param isTarget determines whether to use source or target points.
	 */
	public TurboRegPointHandler (
			TransformationType transformation, 
			ResultsTable resultsTable,
			TurboRegImage interval){
		final boolean isTarget = interval.isTarget();
		final int nPoints = pointCount(transformation);
		this.transformation = transformation;
		this.precisionPoint = new double[nPoints][2];
		this.interval = interval;
		xOffset = interval.xOffset;
		yOffset = interval.yOffset;
		double [] offset = { xOffset, yOffset };
		if (resultsTable == null) {
			setTransformation(transformation);
			return;
		}
		HashMap<String, DoubleColumn> columnMap = new HashMap<String, DoubleColumn>();
		for (int i=0; i<resultsTable.getColumnCount(); i++) {
			columnMap.put(resultsTable.getColumnHeader(i), resultsTable.get(i));
		}
		String [] columnNames = isTarget? new String [] { TARGET_X, TARGET_Y}: new String [] {SOURCE_X, SOURCE_Y};
		if ((resultsTable == null) || 
			(resultsTable.getRowCount() != nPoints) ||
			(! columnMap.containsKey(columnNames[0])) || 
			(! columnMap.containsKey(columnNames[1]))){
			setTransformation(transformation);
		} else {
			for (int j=0; j<2; j++) {
				final DoubleColumn c = resultsTable.get(columnNames[j]);
				for (int i=0; i<precisionPoint.length; i++) {
					Double value = c.get(i);
					if (value != null) precisionPoint[i][j] = value - offset[j];
				}
			}
		}
	}
	
	/**
	 * Fill the results table with the points in the handler
	 * 
	 * @param resultsTable put stuff in this results table
	 * @param interval the image b
	 * @param isTarget true to use target point names, false to use source
	 */
	public void getResults(ResultsTable resultsTable) {
		String [] columnNames = interval.isTarget()? 
				new String [] { TARGET_X, TARGET_Y}: new String [] {SOURCE_X, SOURCE_Y};
		double [] offset = { xOffset, yOffset };
		resultsTable.setRowCount(TurboRegPointHandler.pointCount(transformation));
		for (int j=0; j<2; j++) {
			DoubleColumn c = resultsTable.get(columnNames[j]);
			for (int i=0; i<precisionPoint.length; i++) {
				c.set(i, precisionPoint[i][j] + offset[j]);
			}
		}
	}
	/*....................................................................
	Private methods
....................................................................*/

	/*------------------------------------------------------------------*/
/*	private void drawArcs (
			final Graphics g
	) {
		final double mag = ic.getMagnification();
		final int dx = (int)(mag / 2.0);
		final int dy = (int)(mag / 2.0);
		final Point p = point[1];
		final Point q = point[2];
		final double x0 = (double)(ic.screenX(p.x) + ic.screenX(q.x));
		final double y0 = (double)(ic.screenY(p.y) + ic.screenY(q.y));
		final double dx0 = (double)(ic.screenX(p.x) - ic.screenX(q.x));
		final double dy0 = (double)(ic.screenY(p.y) - ic.screenY(q.y));
		final double radius = 0.5 * Math.sqrt(dx0 * dx0 + dy0 * dy0);
		final double orientation = Math.atan2(dx0, dy0);
		final double spacerAngle = Math.asin((double)CROSS_HALFSIZE / radius);
		g.setColor(spectrum[1]);
		g.drawArc((int)Math.round(0.5 * x0 - radius) + dx,
				(int)Math.round(0.5 * y0 - radius) + dy,
				(int)Math.round(2.0 * radius), (int)Math.round(2.0 * radius),
				(int)Math.round((orientation + spacerAngle + Math.PI)
						* 180.0 / Math.PI),
						(int)Math.round((Math.PI - 2.0 * spacerAngle) * 180.0 / Math.PI));
		g.setColor(spectrum[2]);
		g.drawArc((int)Math.round(0.5 * x0 - radius) + dx,
				(int)Math.round(0.5 * y0 - radius) + dy,
				(int)Math.round(2.0 * radius), (int)Math.round(2.0 * radius),
				(int)Math.round((orientation + spacerAngle) * 180.0 / Math.PI),
				(int)Math.round((Math.PI - 2.0 * spacerAngle) * 180.0 / Math.PI));
	}  end drawArcs 

	------------------------------------------------------------------
	private void drawHorizon (
			final Graphics g
	) {
		final double mag = ic.getMagnification();
		final int dx = (int)(mag / 2.0);
		final int dy = (int)(mag / 2.0);
		final Point p = point[1];
		final Point q = point[2];
		final double x0 = (double)(ic.screenX(p.x) + ic.screenX(q.x));
		final double y0 = (double)(ic.screenY(p.y) + ic.screenY(q.y));
		final double dx0 = (double)(ic.screenX(p.x) - ic.screenX(q.x));
		final double dy0 = (double)(ic.screenY(p.y) - ic.screenY(q.y));
		final double radius = 0.5 * Math.sqrt(dx0 * dx0 + dy0 * dy0);
		final double spacerAngle = Math.asin((double)CROSS_HALFSIZE / radius);
		final double s0 = Math.sin(spacerAngle);
		final double s = 0.5 * dx0 / radius;
		final double c = 0.5 * dy0 / radius;
		double u;
		double v;
		g.setColor(spectrum[1]);
		u = 0.5 * (x0 + s0 * dx0);
		v = 0.5 * (y0 + s0 * dy0);
		if (Math.abs(s) < Math.abs(c)) {
			g.drawLine(-dx, (int)Math.round(
					v + (u + 2.0 * (double)dx) * s / c) + dy,
					(int)Math.round(mag * (double)ic.getSrcRect().width - 1.0) + dx,
					(int)Math.round(v - (mag * (double)ic.getSrcRect().width - 1.0 - u)
							* s / c) + dy);
		}
		else {
			g.drawLine((int)Math.round(
					u + (v + 2.0 * (double)dy) * c / s) + dx, -dy,
					(int)Math.round(u - (mag * (double)ic.getSrcRect().height - 1.0 - v)
							* c / s) + dx,
							(int)Math.round(mag * (double)ic.getSrcRect().height - 1.0) + dy);
		}
		g.setColor(spectrum[2]);
		u = 0.5 * (x0 - s0 * dx0);
		v = 0.5 * (y0 - s0 * dy0);
		if (Math.abs(s) < Math.abs(c)) {
			g.drawLine(-dx, (int)Math.round(
					v + (u + 2.0 * (double)dx) * s / c) + dy,
					(int)Math.round(mag * (double)ic.getSrcRect().width - 1.0) + dx,
					(int)Math.round(v - (mag * (double)ic.getSrcRect().width - 1.0 - u)
							* s / c) + dy);
		}
		else {
			g.drawLine((int)Math.round(
					u + (v + 2.0 * (double)dy) * c / s) + dx, -dy,
					(int)Math.round(u - (mag * (double)ic.getSrcRect().height - 1.0 - v)
							* c / s) + dx, (int)Math.round(
									mag * (double)ic.getSrcRect().height - 1.0) + dy);
		}
	}  end drawHorizon 
*/
	/*------------------------------------------------------------------*/
	private void setSpectrum (
	) {
		if (transformation == TransformationType.RIGID_BODY) {
			spectrum[0] = Colors.GREEN;
			spectrum[1] = new ColorRGB(16, 119, 169);
			spectrum[2] = new ColorRGB(119, 85, 51);
		}
		else {
			spectrum[0] = Colors.GREEN;
			spectrum[1] = Colors.YELLOW;
			spectrum[2] = Colors.MAGENTA;
			spectrum[3] = Colors.CYAN;
		}
	} /* end setSpectrum */

}