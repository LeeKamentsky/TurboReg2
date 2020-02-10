package fiji.plugin.turboreg2;

import net.imglib2.Cursor;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

/**
 * *******************************************************************
 * @author Philippe Thevenaz
 * @author Lee Kamentsky This class is responsible for the image preprocessing that takes place
 * concurrently with user-interface events. It contains methods to compute B-spline coefficients and
 * their pyramids, image pyramids, gradients, and gradient pyramids.
 *******************************************************************
 */
class TurboRegImage
        extends TurboRegInterval<Object>
        implements
        Runnable {

    /* begin class turboRegImage */

    private float[] image;
    private float[] xGradient;
    private float[] yGradient;
    private TransformationType transformation;

    /**
     * *******************************************************************
     * Return the B-spline coefficients of the full-size image.
	 *******************************************************************
     */
    public float[] getCoefficient() {
        return (coefficient);
    }

    /* end getCoefficient */

    /**
     * *******************************************************************
     * Return the full-size image array.
	 *******************************************************************
     */
    public float[] getImage() {
        return (image);
    }

    /* end getImage */

    /**
     * *******************************************************************
     * Return the full-size horizontal gradient of the image, if available.
     *
     * @see turboRegImage#getPyramid()
	 *******************************************************************
     */
    public float[] getXGradient() {
        return (xGradient);
    }

    /* end getXGradient */

    /**
     * *******************************************************************
     * Return the full-size vertical gradient of the image, if available.
     *
     * @see turboRegImage#getImage()
	 *******************************************************************
     */
    public float[] getYGradient() {
        return (yGradient);
    }

    /* end getYGradient */

    /**
     * *******************************************************************
     * Start the image precomputations. The computation of the B-spline coefficients of the
     * full-size image is not interruptible; all other methods are.
	 *******************************************************************
     */
    @Override
    public void run() {
        coefficient = getBasicFromCardinal2D();
        switch (transformation) {
            case TRANSLATION:
            case RIGID_BODY:
            case SCALED_ROTATION:
            case AFFINE: {
                if (isTarget) {
                    buildCoefficientPyramid();
                } else {
                    imageToXYGradient2D();
                    buildImageAndGradientPyramid();
                }
                break;
            }
            case BILINEAR: {
                if (isTarget) {
                    buildImagePyramid();
                } else {
                    buildCoefficientPyramid();
                }
                break;
            }
        }
    }

    /* end run */

    /**
     * *******************************************************************
     * Set or modify the transformation.
	 *******************************************************************
     */
    public void setTransformation(
            final TransformationType transformation
    ) {
        this.transformation = transformation;
    }

    /* end setTransformation */

    /**
     * *******************************************************************
     * Converts the pixel array of the incoming <code>RandomAccessibleInterval</code> object into a
     * local <code>float</code> array.
     *
     * @param display <code>ImageDisplay</code> object to preprocess.
     * @param transformation Transformation code.
     * @param isTarget Tags the current object as a target or source image.
	 *******************************************************************
     */
    public void setData(
            final RandomAccessibleInterval<? extends RealType<?>> data,
            final boolean isTarget
    ) {
        this.isTarget = isTarget;
        width = (int) data.dimension(0);
        height = (int) data.dimension(1);
        xOffset = data.min(0);
        yOffset = data.min(1);
        int k = 0;
        image = new float[width * height];
        Cursor<? extends RealType<?>> c = Views.iterable(data).cursor();
        addWorkload(isTarget ? "Copying target image" : "Copying source image", height);
        while (c.hasNext()) {
            final RealType<?> t = c.next();
            final int idx = (int) (c.getLongPosition(0) - xOffset)
                    + width * (int) (c.getLongPosition(1) - yOffset);
            image[idx] = t.getRealFloat();
            if ((k % width) == 0) {
                workloadStep();
            }
            k++;
        }
        workloadDone();
    }

    /* end turboRegImage */

    /**
     * Set the data using the buffer of an ArrayImg.
     *
     * @param buffer buffer of float pixel data organized in x rasters
     * @param width length of a raster
     * @param height number of rasters
     * @param xOffset x offset of the buffer to the origin
     * @param yOffset y offset of the buffer to the origin
     * @param isTarget true if this image is the target image, false if source
     */
    public void setData(float[] buffer, int width, int height, long xOffset, long yOffset, boolean isTarget) {
        this.isTarget = isTarget;
        this.width = width;
        this.height = height;
        this.xOffset = xOffset;
        this.yOffset = yOffset;
        image = buffer;
    }

    /*------------------------------------------------------------------*/
    private void antiSymmetricFirMirrorOffBounds1D(
            final double[] h,
            final double[] c,
            final double[] s
    ) {
        if (2 <= c.length) {
            s[0] = h[1] * (c[1] - c[0]);
            for (int i = 1; (i < (s.length - 1)); i++) {
                s[i] = h[1] * (c[i + 1] - c[i - 1]);
            }
            s[s.length - 1] = h[1] * (c[c.length - 1] - c[c.length - 2]);
        } else {
            s[0] = 0.0;
        }
    }

    /* end antiSymmetricFirMirrorOffBounds1D */

 /*------------------------------------------------------------------*/
    private void basicToCardinal2D(
            final float[] basic,
            final float[] cardinal,
            final int width,
            final int height,
            final int degree
    ) {
        final double[] hLine = new double[width];
        final double[] vLine = new double[height];
        final double[] hData = new double[width];
        final double[] vData = new double[height];
        double[] h = null;
        switch (degree) {
            case 3: {
                h = new double[2];
                h[0] = 2.0 / 3.0;
                h[1] = 1.0 / 6.0;
                break;
            }
            case 7: {
                h = new double[4];
                h[0] = 151.0 / 315.0;
                h[1] = 397.0 / 1680.0;
                h[2] = 1.0 / 42.0;
                h[3] = 1.0 / 5040.0;
                break;
            }
            default: {
                h = new double[1];
                h[0] = 1.0;
            }
        }
        int workload = width + height;
        addWorkload("Filtering image", workload);
        for (int y = 0; ((y < height) && (!isCanceled())); y++) {
            extractRow(basic, y, hLine);
            symmetricFirMirrorOffBounds1D(h, hLine, hData);
            putRow(cardinal, y, hData);
            workloadStep();
            workload--;
        }
        for (int x = 0; ((x < width) && (!isCanceled())); x++) {
            extractColumn(cardinal, width, x, vLine);
            symmetricFirMirrorOffBounds1D(h, vLine, vData);
            putColumn(cardinal, width, x, vData);
            workloadStep();
            workload--;
        }
        workloadDone();
    }

    /* end basicToCardinal2D */

 /*------------------------------------------------------------------*/
    private void buildCoefficientPyramid() {
        int fullWidth;
        int fullHeight;
        float[] fullDual = new float[width * height];
        int halfWidth = width;
        int halfHeight = height;
        if (1 < pyramidDepth) {
            basicToCardinal2D(coefficient, fullDual, width, height, 7);
        }
        for (int depth = 1; ((depth < pyramidDepth) && (!isCanceled()));
                depth++) {
            fullWidth = halfWidth;
            fullHeight = halfHeight;
            halfWidth /= 2;
            halfHeight /= 2;
            final float[] halfDual = getHalfDual2D(fullDual, fullWidth, fullHeight);
            final float[] halfCoefficient = getBasicFromCardinal2D(
                    halfDual, halfWidth, halfHeight, 7);
            pyramid.push(halfCoefficient);
            pyramid.push(new Integer(halfHeight));
            pyramid.push(new Integer(halfWidth));
            fullDual = halfDual;
        }
    }

    /* end buildCoefficientPyramid */

 /*------------------------------------------------------------------*/
    private void buildImageAndGradientPyramid() {
        int fullWidth;
        int fullHeight;
        float[] fullDual = new float[width * height];
        int halfWidth = width;
        int halfHeight = height;
        if (1 < pyramidDepth) {
            cardinalToDual2D(image, fullDual, width, height, 3);
        }
        for (int depth = 1; ((depth < pyramidDepth) && (!isCanceled()));
                depth++) {
            fullWidth = halfWidth;
            fullHeight = halfHeight;
            halfWidth /= 2;
            halfHeight /= 2;
            final float[] halfDual = getHalfDual2D(fullDual, fullWidth, fullHeight);
            final float[] halfImage = getBasicFromCardinal2D(
                    halfDual, halfWidth, halfHeight, 7);
            final float[] halfXGradient = new float[halfWidth * halfHeight];
            final float[] halfYGradient = new float[halfWidth * halfHeight];
            coefficientToXYGradient2D(halfImage, halfXGradient, halfYGradient,
                    halfWidth, halfHeight);
            basicToCardinal2D(halfImage, halfImage, halfWidth, halfHeight, 3);
            pyramid.push(halfYGradient);
            pyramid.push(halfXGradient);
            pyramid.push(halfImage);
            pyramid.push(halfHeight);
            pyramid.push(halfWidth);
            fullDual = halfDual;
        }
    }

    /* end buildImageAndGradientPyramid */

 /*------------------------------------------------------------------*/
    private void buildImagePyramid() {
        int fullWidth;
        int fullHeight;
        float[] fullDual = new float[width * height];
        int halfWidth = width;
        int halfHeight = height;
        if (1 < pyramidDepth) {
            cardinalToDual2D(image, fullDual, width, height, 3);
        }
        for (int depth = 1; ((depth < pyramidDepth) && (!isCanceled()));
                depth++) {
            fullWidth = halfWidth;
            fullHeight = halfHeight;
            halfWidth /= 2;
            halfHeight /= 2;
            final float[] halfDual = getHalfDual2D(fullDual, fullWidth, fullHeight);
            final float[] halfImage = new float[halfWidth * halfHeight];
            dualToCardinal2D(halfDual, halfImage, halfWidth, halfHeight, 3);
            pyramid.push(halfImage);
            pyramid.push(halfHeight);
            pyramid.push(halfWidth);
            fullDual = halfDual;
        }
    }

    /* end buildImagePyramid */

 /*------------------------------------------------------------------*/
    private void cardinalToDual2D(
            final float[] cardinal,
            final float[] dual,
            final int width,
            final int height,
            final int degree
    ) {
        basicToCardinal2D(getBasicFromCardinal2D(cardinal, width, height, degree),
                dual, width, height, 2 * degree + 1);
    }

    /* end cardinalToDual2D */

 /*------------------------------------------------------------------*/
    private void coefficientToGradient1D(
            final double[] c
    ) {
        final double[] h = {0.0, 1.0 / 2.0};
        final double[] s = new double[c.length];
        antiSymmetricFirMirrorOffBounds1D(h, c, s);
        System.arraycopy(s, 0, c, 0, s.length);
    }

    /* end coefficientToGradient1D */

 /*------------------------------------------------------------------*/
    private void coefficientToSamples1D(
            final double[] c
    ) {
        final double[] h = {2.0 / 3.0, 1.0 / 6.0};
        final double[] s = new double[c.length];
        symmetricFirMirrorOffBounds1D(h, c, s);
        System.arraycopy(s, 0, c, 0, s.length);
    }

    /* end coefficientToSamples1D */

 /*------------------------------------------------------------------*/
    private void coefficientToXYGradient2D(
            final float[] basic,
            final float[] xGradient,
            final float[] yGradient,
            final int width,
            final int height
    ) {
        final double[] hLine = new double[width];
        final double[] hData = new double[width];
        final double[] vLine = new double[height];
        int workload = 2 * (width + height);
        addWorkload("Computing gradient", workload);
        for (int y = 0; ((y < height) && (!isCanceled())); y++) {
            extractRow(basic, y, hLine);
            System.arraycopy(hLine, 0, hData, 0, width);
            coefficientToGradient1D(hLine);
            workloadStep();
            workload--;
            coefficientToSamples1D(hData);
            putRow(xGradient, y, hLine);
            putRow(yGradient, y, hData);
            workloadStep();
            workload--;
        }
        for (int x = 0; ((x < width) && (!isCanceled())); x++) {
            extractColumn(xGradient, width, x, vLine);
            coefficientToSamples1D(vLine);
            putColumn(xGradient, width, x, vLine);
            workloadStep();
            workload--;
            extractColumn(yGradient, width, x, vLine);
            coefficientToGradient1D(vLine);
            putColumn(yGradient, width, x, vLine);
            workloadStep();
            workload--;
        }
        workloadDone();
    }

    /* end coefficientToXYGradient2D */

 /*------------------------------------------------------------------*/
    private void dualToCardinal2D(
            final float[] dual,
            final float[] cardinal,
            final int width,
            final int height,
            final int degree
    ) {
        basicToCardinal2D(getBasicFromCardinal2D(dual, width, height,
                2 * degree + 1), cardinal, width, height, degree);
    }

    /* end dualToCardinal2D */

 /*------------------------------------------------------------------*/
    private void extractColumn(
            final float[] array,
            final int width,
            int x,
            final double[] column
    ) {
        for (int i = 0; (i < column.length); i++) {
            column[i] = (double) array[x];
            x += width;
        }
    }

    /* end extractColumn */

 /*------------------------------------------------------------------*/
    private void extractRow(
            final float[] array,
            int y,
            final double[] row
    ) {
        y *= row.length;
        for (int i = 0; (i < row.length); i++) {
            row[i] = (double) array[y++];
        }
    }

    /* end extractRow */

 /*------------------------------------------------------------------*/
    private float[] getBasicFromCardinal2D() {
        final float[] basic = new float[width * height];
        final double[] hLine = new double[width];
        final double[] vLine = new double[height];
        addWorkload("Interpolating", width + height);
        for (int y = 0; (y < height); y++) {
            extractRow(image, y, hLine);
            samplesToInterpolationCoefficient1D(hLine, 3, 0.0);
            putRow(basic, y, hLine);
            workloadStep();
        }
        for (int x = 0; (x < width); x++) {
            extractColumn(basic, width, x, vLine);
            samplesToInterpolationCoefficient1D(vLine, 3, 0.0);
            putColumn(basic, width, x, vLine);
            workloadStep();
        }
        workloadDone();
        return (basic);
    }

    /* end getBasicFromCardinal2D */

 /*------------------------------------------------------------------*/
    private float[] getBasicFromCardinal2D(
            final float[] cardinal,
            final int width,
            final int height,
            final int degree
    ) {
        final float[] basic = new float[width * height];
        final double[] hLine = new double[width];
        final double[] vLine = new double[height];
        int workload = width + height;
        addWorkload("Interpolating", workload);
        for (int y = 0; ((y < height) && (!isCanceled())); y++) {
            extractRow(cardinal, y, hLine);
            samplesToInterpolationCoefficient1D(hLine, degree, 0.0);
            putRow(basic, y, hLine);
            workloadStep();
        }
        for (int x = 0; ((x < width) && (!isCanceled())); x++) {
            extractColumn(basic, width, x, vLine);
            samplesToInterpolationCoefficient1D(vLine, degree, 0.0);
            putColumn(basic, width, x, vLine);
            workloadStep();
        }
        workloadDone();
        return (basic);
    }

    /* end getBasicFromCardinal2D */

 /*------------------------------------------------------------------*/
    private float[] getHalfDual2D(
            final float[] fullDual,
            final int fullWidth,
            final int fullHeight
    ) {
        final int halfWidth = fullWidth / 2;
        final int halfHeight = fullHeight / 2;
        final double[] hLine = new double[fullWidth];
        final double[] hData = new double[halfWidth];
        final double[] vLine = new double[fullHeight];
        final double[] vData = new double[halfHeight];
        final float[] demiDual = new float[halfWidth * fullHeight];
        final float[] halfDual = new float[halfWidth * halfHeight];
        int workload = halfWidth + fullHeight;
        addWorkload("Reducing", workload);
        for (int y = 0; ((y < fullHeight) && (!isCanceled())); y++) {
            extractRow(fullDual, y, hLine);
            reduceDual1D(hLine, hData);
            putRow(demiDual, y, hData);
            workloadStep();
        }
        for (int x = 0; ((x < halfWidth) && (!isCanceled())); x++) {
            extractColumn(demiDual, halfWidth, x, vLine);
            reduceDual1D(vLine, vData);
            putColumn(halfDual, halfWidth, x, vData);
            workloadStep();
        }
        workloadDone();
        return (halfDual);
    }

    /* end getHalfDual2D */

 /*------------------------------------------------------------------*/
    private double getInitialAntiCausalCoefficientMirrorOffBounds(
            final double[] c,
            final double z,
            final double tolerance
    ) {
        return (z * c[c.length - 1] / (z - 1.0));
    }

    /* end getInitialAntiCausalCoefficientMirrorOffBounds */

 /*------------------------------------------------------------------*/
    private double getInitialCausalCoefficientMirrorOffBounds(
            final double[] c,
            final double z,
            final double tolerance
    ) {
        double z1 = z;
        double zn = Math.pow(z, c.length);
        double sum = (1.0 + z) * (c[0] + zn * c[c.length - 1]);
        int horizon = c.length;
        if (0.0 < tolerance) {
            horizon = 2 + (int) (Math.log(tolerance) / Math.log(Math.abs(z)));
            horizon = (horizon < c.length) ? (horizon) : (c.length);
        }
        zn = zn * zn;
        for (int n = 1; (n < (horizon - 1)); n++) {
            z1 = z1 * z;
            zn = zn / z;
            sum = sum + (z1 + zn) * c[n];
        }
        return (sum / (1.0 - Math.pow(z, 2 * c.length)));
    }

    /* end getInitialCausalCoefficientMirrorOffBounds */

 /*------------------------------------------------------------------*/
    private void imageToXYGradient2D() {
        final double[] hLine = new double[width];
        final double[] vLine = new double[height];
        xGradient = new float[width * height];
        yGradient = new float[width * height];
        int workload = width + height;
        addWorkload("Computing gradient", workload);
        for (int y = 0; ((y < height) && (!isCanceled())); y++) {
            extractRow(image, y, hLine);
            samplesToInterpolationCoefficient1D(hLine, 3, 0.0);
            coefficientToGradient1D(hLine);
            putRow(xGradient, y, hLine);
            workloadStep();
        }
        for (int x = 0; ((x < width) && (!isCanceled())); x++) {
            extractColumn(image, width, x, vLine);
            samplesToInterpolationCoefficient1D(vLine, 3, 0.0);
            coefficientToGradient1D(vLine);
            putColumn(yGradient, width, x, vLine);
            workloadStep();
        }
        workloadDone();
    }

    /* end imageToXYGradient2D */

 /*------------------------------------------------------------------*/
    private void putColumn(
            final float[] array,
            final int width,
            int x,
            final double[] column
    ) {
        for (int i = 0; (i < column.length); i++) {
            array[x] = (float) column[i];
            x += width;
        }
    }

    /* end putColumn */

 /*------------------------------------------------------------------*/
    private void putRow(
            final float[] array,
            int y,
            final double[] row
    ) {
        y *= row.length;
        for (int i = 0; (i < row.length); i++) {
            array[y++] = (float) row[i];
        }
    }

    /* end putRow */

 /*------------------------------------------------------------------*/
    private void reduceDual1D(
            final double[] c,
            final double[] s
    ) {
        final double h[] = {6.0 / 16.0, 4.0 / 16.0, 1.0 / 16.0};
        if (2 <= s.length) {
            s[0] = h[0] * c[0] + h[1] * (c[0] + c[1]) + h[2] * (c[1] + c[2]);
            for (int i = 2, j = 1; (j < (s.length - 1)); i += 2, j++) {
                s[j] = h[0] * c[i] + h[1] * (c[i - 1] + c[i + 1])
                        + h[2] * (c[i - 2] + c[i + 2]);
            }
            if (c.length == (2 * s.length)) {
                s[s.length - 1] = h[0] * c[c.length - 2]
                        + h[1] * (c[c.length - 3] + c[c.length - 1])
                        + h[2] * (c[c.length - 4] + c[c.length - 1]);
            } else {
                s[s.length - 1] = h[0] * c[c.length - 3]
                        + h[1] * (c[c.length - 4] + c[c.length - 2])
                        + h[2] * (c[c.length - 5] + c[c.length - 1]);
            }
        } else {
            switch (c.length) {
                case 3: {
                    s[0] = h[0] * c[0]
                            + h[1] * (c[0] + c[1]) + h[2] * (c[1] + c[2]);
                    break;
                }
                case 2: {
                    s[0] = h[0] * c[0] + h[1] * (c[0] + c[1]) + 2.0 * h[2] * c[1];
                    break;
                }
            }
        }
    }

    /* end reduceDual1D */

 /*------------------------------------------------------------------*/
    private void samplesToInterpolationCoefficient1D(
            final double[] c,
            final int degree,
            final double tolerance
    ) {
        double[] z = new double[0];
        double lambda = 1.0;
        switch (degree) {
            case 3: {
                z = new double[1];
                z[0] = Math.sqrt(3.0) - 2.0;
                break;
            }
            case 7: {
                z = new double[3];
                z[0]
                        = -0.5352804307964381655424037816816460718339231523426924148812;
                z[1]
                        = -0.122554615192326690515272264359357343605486549427295558490763;
                z[2]
                        = -0.0091486948096082769285930216516478534156925639545994482648003;
                break;
            }
        }
        if (c.length == 1) {
            return;
        }
        for (int k = 0; (k < z.length); k++) {
            lambda *= (1.0 - z[k]) * (1.0 - 1.0 / z[k]);
        }
        for (int n = 0; (n < c.length); n++) {
            c[n] = c[n] * lambda;
        }
        for (int k = 0; (k < z.length); k++) {
            c[0] = getInitialCausalCoefficientMirrorOffBounds(c, z[k], tolerance);
            for (int n = 1; (n < c.length); n++) {
                c[n] = c[n] + z[k] * c[n - 1];
            }
            c[c.length - 1] = getInitialAntiCausalCoefficientMirrorOffBounds(
                    c, z[k], tolerance);
            for (int n = c.length - 2; (0 <= n); n--) {
                c[n] = z[k] * (c[n + 1] - c[n]);
            }
        }
    }

    /* end samplesToInterpolationCoefficient1D */

 /*------------------------------------------------------------------*/
    private void symmetricFirMirrorOffBounds1D(
            final double[] h,
            final double[] c,
            final double[] s
    ) {
        switch (h.length) {
            case 2: {
                if (2 <= c.length) {
                    s[0] = h[0] * c[0] + h[1] * (c[0] + c[1]);
                    for (int i = 1; (i < (s.length - 1)); i++) {
                        s[i] = h[0] * c[i] + h[1] * (c[i - 1] + c[i + 1]);
                    }
                    s[s.length - 1] = h[0] * c[c.length - 1]
                            + h[1] * (c[c.length - 2] + c[c.length - 1]);
                } else {
                    s[0] = (h[0] + 2.0 * h[1]) * c[0];
                }
                break;
            }
            case 4: {
                if (6 <= c.length) {
                    s[0] = h[0] * c[0] + h[1] * (c[0] + c[1]) + h[2] * (c[1] + c[2])
                            + h[3] * (c[2] + c[3]);
                    s[1] = h[0] * c[1] + h[1] * (c[0] + c[2]) + h[2] * (c[0] + c[3])
                            + h[3] * (c[1] + c[4]);
                    s[2] = h[0] * c[2] + h[1] * (c[1] + c[3]) + h[2] * (c[0] + c[4])
                            + h[3] * (c[0] + c[5]);
                    for (int i = 3; (i < (s.length - 3)); i++) {
                        s[i] = h[0] * c[i] + h[1] * (c[i - 1] + c[i + 1])
                                + h[2] * (c[i - 2] + c[i + 2])
                                + h[3] * (c[i - 3] + c[i + 3]);
                    }
                    s[s.length - 3] = h[0] * c[c.length - 3]
                            + h[1] * (c[c.length - 4] + c[c.length - 2])
                            + h[2] * (c[c.length - 5] + c[c.length - 1])
                            + h[3] * (c[c.length - 6] + c[c.length - 1]);
                    s[s.length - 2] = h[0] * c[c.length - 2]
                            + h[1] * (c[c.length - 3] + c[c.length - 1])
                            + h[2] * (c[c.length - 4] + c[c.length - 1])
                            + h[3] * (c[c.length - 5] + c[c.length - 2]);
                    s[s.length - 1] = h[0] * c[c.length - 1]
                            + h[1] * (c[c.length - 2] + c[c.length - 1])
                            + h[2] * (c[c.length - 3] + c[c.length - 2])
                            + h[3] * (c[c.length - 4] + c[c.length - 3]);
                } else {
                    switch (c.length) {
                        case 5: {
                            s[0] = h[0] * c[0] + h[1] * (c[0] + c[1])
                                    + h[2] * (c[1] + c[2]) + h[3] * (c[2] + c[3]);
                            s[1] = h[0] * c[1] + h[1] * (c[0] + c[2])
                                    + h[2] * (c[0] + c[3]) + h[3] * (c[1] + c[4]);
                            s[2] = h[0] * c[2] + h[1] * (c[1] + c[3])
                                    + (h[2] + h[3]) * (c[0] + c[4]);
                            s[3] = h[0] * c[3] + h[1] * (c[2] + c[4])
                                    + h[2] * (c[1] + c[4]) + h[3] * (c[0] + c[3]);
                            s[4] = h[0] * c[4] + h[1] * (c[3] + c[4])
                                    + h[2] * (c[2] + c[3]) + h[3] * (c[1] + c[2]);
                            break;
                        }
                        case 4: {
                            s[0] = h[0] * c[0] + h[1] * (c[0] + c[1])
                                    + h[2] * (c[1] + c[2]) + h[3] * (c[2] + c[3]);
                            s[1] = h[0] * c[1] + h[1] * (c[0] + c[2])
                                    + h[2] * (c[0] + c[3]) + h[3] * (c[1] + c[3]);
                            s[2] = h[0] * c[2] + h[1] * (c[1] + c[3])
                                    + h[2] * (c[0] + c[3]) + h[3] * (c[0] + c[2]);
                            s[3] = h[0] * c[3] + h[1] * (c[2] + c[3])
                                    + h[2] * (c[1] + c[2]) + h[3] * (c[0] + c[1]);
                            break;
                        }
                        case 3: {
                            s[0] = h[0] * c[0] + h[1] * (c[0] + c[1])
                                    + h[2] * (c[1] + c[2]) + 2.0 * h[3] * c[2];
                            s[1] = h[0] * c[1] + (h[1] + h[2]) * (c[0] + c[2])
                                    + 2.0 * h[3] * c[1];
                            s[2] = h[0] * c[2] + h[1] * (c[1] + c[2])
                                    + h[2] * (c[0] + c[1]) + 2.0 * h[3] * c[0];
                            break;
                        }
                        case 2: {
                            s[0] = (h[0] + h[1] + h[3]) * c[0]
                                    + (h[1] + 2.0 * h[2] + h[3]) * c[1];
                            s[1] = (h[0] + h[1] + h[3]) * c[1]
                                    + (h[1] + 2.0 * h[2] + h[3]) * c[0];
                            break;
                        }
                        case 1: {
                            s[0] = (h[0] + 2.0 * (h[1] + h[2] + h[3])) * c[0];
                            break;
                        }
                    }
                }
                break;
            }
        }
    }
    /* end symmetricFirMirrorOffBounds1D */

}
