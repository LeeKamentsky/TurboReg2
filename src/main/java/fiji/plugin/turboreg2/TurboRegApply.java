/**
 *
 */
package fiji.plugin.turboreg2;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.RandomAccessibleInterval;
import net.imagej.ImgPlus;
import net.imglib2.img.array.ArrayImgs;
import net.imagej.axis.Axes;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

import org.scijava.ItemIO;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import org.scijava.command.Command;
import net.imagej.Dataset;
import net.imagej.display.DataView;
import net.imagej.display.DatasetView;
import net.imagej.display.ImageDisplay;
import net.imagej.table.TableDisplay;
import org.scijava.menu.MenuConstants;
import org.scijava.module.ModuleException;

/**
 * @author Philippe Thevenaz
 * @author Lee Kamentsky
 *
 * The <code>TurboRegApply</code> command applies a registration done by
 * <code>TurboRegRegister</code> to an image.
 */
@Plugin(type = Command.class, menu = {
    @Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = MenuConstants.PLUGINS_MNEMONIC),
    @Menu(label = "TurboReg"),
    @Menu(label = "Apply", weight = 2)
})
public class TurboRegApply extends TurboRegCommandBase {

    @Parameter(label = "Image to transform",
            type = ItemIO.BOTH)
    private ImageDisplay display;

    @Parameter(label = "Registration table", type = ItemIO.INPUT,
            description = "The registration table as produced by TurboRegRegister.")
    private TableDisplay registrationDisplay;

    @Parameter(label = "Fast mode", type = ItemIO.INPUT,
            description = "Check this box to transform the image quickly, but without sub-pixel interpolation\n"
            + "Uncheck the box to produce a better image, but more slowly")
    private boolean accelerated = false;

    @Parameter(label = "All stack frames", type = ItemIO.INPUT,
            description = "Check this to transform all frames in a stack similarly, leave unchecked to only transform the selected frame")
    private boolean allFrames;

    @Override
    protected TableDisplay getTableDisplay() {
        return registrationDisplay;
    }

    @Override
    protected void setTableDisplay(TableDisplay dummy) {
        throw new UnsupportedOperationException("This plugin has a read-only registration table");
    }

    @Override
    public void run() {
        long[] plane = new long[display.numDimensions() - 2];
        DataView view = display.getActiveView();
        DatasetView dsView = (DatasetView) view;
        ArrayList<Future<?>> runningPlanes = new ArrayList<>();
        if (plane.length == 0) {
            runningPlanes.add(runPlane(plane));
        } else if (allFrames) {
            int nPlanes = 1;
            for (int i = 0; i < plane.length; i++) {
                nPlanes *= display.dimension(i + 2);
            }
            for (int i = 0; i < nPlanes; i++) {
                /*
				 * Increment plane # with carryover.
                 */
                for (int j = 0; j < plane.length; j++) {
                    if (++plane[j] == display.dimension(j + 2)) {
                        plane[j] = 0;
                    } else {
                        break;
                    }
                }
                runningPlanes.add(runPlane(plane));
            }
        } else {
            for (int i = 0; i < plane.length; i++) {
                plane[i] = view.getLongPosition(i + 2);
            }
            int channelAxis = dsView.getData().dimensionIndex(Axes.CHANNEL);
            if (channelAxis < 0) {
                runningPlanes.add(runPlane(plane));
            } else {
                for (int i = 0; i < display.dimension(channelAxis); i++) {
                    plane[channelAxis - 2] = i;
                    runningPlanes.add(runPlane(plane));
                }
            }
        }
        for (Future<?> f : runningPlanes) {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException e) {
                logService.error(e);
            }
        }
    }

    /**
     * Kick off processing one plane.
     *
     * @param plane
     * @return
     */
    protected Future<?> runPlane(long[] plane) {
        return threadService.run(new RunPlane(plane));
    }

    /**
     * The <code>RunPlane</code> class is a runnable that transforms one plane of the display.
     */
    protected class RunPlane implements Runnable {

        final private long[] plane;

        RunPlane(long[] plane) {
            this.plane = Arrays.copyOf(plane, plane.length);
        }

        @Override
        public void run() {
            try {
                DataView view = display.getActiveView();
                if ((view == null) || !(view instanceof DatasetView)) {
                    throw new ModuleException("The display doesn't have an active dataset (image)");
                }
                DatasetView dsView = (DatasetView) view;
                Dataset ds = dsView.getData();
                ImgPlus<? extends RealType<?>> data = ds.getImgPlus();
                /*
		 * Reduce the dimensionality of the target to a slice.
                 */
                Integer channel = null;
                RandomAccessibleInterval<? extends RealType<?>> hsData = data;
                for (int d = data.numDimensions() - 1; d >= 2; d--) {
                    if (data.axis(d).equals(Axes.CHANNEL)) {
                        channel = d;
                    }
                    hsData = Views.hyperSlice(hsData, d, plane[d - 2]);
                }
                final long width = display.dimension(0);
                final long height = display.dimension(1);
                final int pyramidDepth = TurboRegInterval.getPyramidDepth(display, display, null);
                final TransformationType transformation = getTransformationType();
                final TurboRegImage sourceImg = new TurboRegImage();
                final TurboRegImage targetImg = new TurboRegImage();
                for (TurboRegImage img : new TurboRegImage[]{sourceImg, targetImg}) {
                    img.setTransformation(transformation);
                    img.setStatusService(statusService);
                    img.setPyramidDepth(pyramidDepth);
                    img.setExecutionContext(TurboRegApply.this);
                }
                /*
		 * Copy the source data to a buffer.
                 */
                float[] srcBuffer = new float[(int) (width * height)];
                ImgPlus<FloatType> srcImgPlus = ImgPlus.wrap(ArrayImgs.floats(srcBuffer, width, height));
                IterableInterval<? extends RealType<?>> src = Views.flatIterable(hsData);
                Cursor<? extends RealType<?>> srcCursor = src.cursor();
                Cursor<FloatType> bufferCursor = Views.flatIterable(srcImgPlus).cursor();
                while (bufferCursor.hasNext()) {
                    bufferCursor.next().set(srcCursor.next().getRealFloat());
                }

                sourceImg.setData(srcBuffer, (int) width, (int) height, 0, 0, false);
                Future<?> sourceImgDone = threadService.run(sourceImg);
                float[] buffer = new float[(int) (display.dimension(0) * display.dimension(1))];
                ImgPlus<FloatType> targetImgPlus = ImgPlus.wrap(ArrayImgs.floats(buffer, width, height));
                targetImg.setData(buffer, (int) width, (int) height, 0, 0, true);

                final TurboRegPointHandler sourcePh = new TurboRegPointHandler(
                        transformation, getResultsTable(), sourceImg);
                final TurboRegPointHandler targetPh = new TurboRegPointHandler(
                        transformation, getResultsTable(), targetImg);
                sourceImgDone.get();
                
                TurboRegTransform tt = new TurboRegTransform(
                        sourceImg, null, sourcePh, targetImg, null, targetPh,
                        transformation, accelerated, false, statusService);
                tt.doBatchFinalTransform();
                
                /*
		 * Copy the target to the plane.
                 */
                float channelMinimum = (float) ((channel == null) ? 0 : dsView.getChannelMin(channel));
                float channelMaximum = (float) ((channel == null) ? 255.0 : dsView.getChannelMax(channel));
                srcCursor = src.cursor();
                bufferCursor = Views.flatIterable(targetImgPlus).cursor();
                while (bufferCursor.hasNext()) {
                    srcCursor.next().setReal(Math.max(channelMinimum, Math.min(channelMaximum, bufferCursor.next().get())));
                }
            } catch (InterruptedException | ExecutionException e) {
                logService.error(e);
            } catch (ModuleException ex) {
                Logger.getLogger(TurboRegApply.class.getName()).log(Level.SEVERE, null, ex);
            }
            display.update();
        }
    }
}
