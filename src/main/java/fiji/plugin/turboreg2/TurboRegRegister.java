/**
 *
 */
package fiji.plugin.turboreg2;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.scijava.ItemIO;
import org.scijava.plugin.Menu;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import org.scijava.command.Command;
import org.scijava.log.LogService;
import org.scijava.menu.MenuConstants;
import org.scijava.module.ModuleException;

import net.imagej.display.ImageDisplay;
import net.imagej.display.OverlayService;
import net.imagej.overlay.Overlay;
import net.imagej.overlay.PointOverlay;
import net.imagej.table.TableDisplay;

/**
 * @author Philippe Thevenaz
 * @author Lee Kamentsky
 *
 * Adapted from the TurboReg ImageJ plugin by Phillipe Thevenaz.
 *
 * This plugin computes the alignment of the source to the target, producing a table that can be
 * used to produce the alignment transform.
 */
@Plugin(type = Command.class, menu = {
    @Menu(label = MenuConstants.PLUGINS_LABEL, weight = MenuConstants.PLUGINS_WEIGHT, mnemonic = MenuConstants.PLUGINS_MNEMONIC),
    @Menu(label = "TurboReg"),
    @Menu(label = "Register", weight = 1)
})
public class TurboRegRegister extends TurboRegCommandBase {

    @Parameter
    OverlayService overlayService;

    @Parameter
    LogService log;

    @Parameter(type = ItemIO.INPUT, label = "Source",
            description = "Use this display as a reference for aligning the target image.",
            required = true)
    ImageDisplay source;

    @Parameter(type = ItemIO.INPUT, label = "Target", description = "Align this display to the source image.", required = true)
    ImageDisplay target;

    @Parameter(type = ItemIO.BOTH, label = "Registration table", required = false,
            description = "The alignment between the source and target.\n"
            + "If an alignment is input, it is used to seed the initial conditions.\n")
    protected TableDisplay alignment;

    @Parameter(type = ItemIO.INPUT, label = "Add landmarks to images",
            description = "Check this option to draw the landmark points on the source and target images")
    private boolean addLandmarks;

    @Override
    protected TableDisplay getTableDisplay() {
        return alignment;
    }

    @Override
    protected void setTableDisplay(TableDisplay src) {
        alignment = src;
    }

    /* (non-Javadoc)
	 * @see java.lang.Runnable#run()
     */
    @Override
    public void run() {
        TransformationType transformation = getTransformationType();
        /*
         * The body of "run" is an adaptation of TurboReg's alignImages
         */
        try {
            final Future<TurboRegImage> fSourceImg = threadService.run(
                    new Callable<TurboRegImage>() {
                public TurboRegImage call() throws Exception {
                    TurboRegImage result = makeTurboRegImage(false);
                    result.run();
                    return result;
                }
            });
            final Future<TurboRegImage> fTargetImg = threadService.run(
                    new Callable<TurboRegImage>() {
                public TurboRegImage call() throws Exception {
                    TurboRegImage result = makeTurboRegImage(true);
                    result.run();
                    return result;
                }
            });
            final Future<TurboRegMask> fSourceMask = threadService.run(
                    new Callable<TurboRegMask>() {
                public TurboRegMask call() throws Exception {
                    TurboRegMask result = makeTurboRegMask(source);
                    result.run();
                    return result;
                }
            });
            final Future<TurboRegMask> fTargetMask = threadService.run(
                    new Callable<TurboRegMask>() {
                public TurboRegMask call() throws Exception {
                    TurboRegMask result = makeTurboRegMask(target);
                    result.run();
                    return result;
                }
            });
            final TurboRegImage sourceImg = fSourceImg.get();
            final TurboRegImage targetImg = fTargetImg.get();
            final TurboRegMask sourceMask = fSourceMask.get();
            final TurboRegMask targetMask = fTargetMask.get();
            final TurboRegPointHandler sourcePh = new TurboRegPointHandler(
                    transformation, getResultsTable(), sourceImg);
            final TurboRegPointHandler targetPh = new TurboRegPointHandler(
                    transformation, getResultsTable(), targetImg);
            
            TurboRegTransform tt = new TurboRegTransform(
                    sourceImg, sourceMask, sourcePh, targetImg, targetMask, targetPh,
                    transformation, true, false, statusService);
            tt.doRegistration();
            
            if (alignment == null) {
                createResultsTable();
            }
            
            sourcePh.getResults(getResultsTable());
            targetPh.getResults(getResultsTable());
            alignment.update();
            
            if (addLandmarks) {
                TurboRegPointHandler[] ph = {sourcePh, targetPh};
                ImageDisplay[] dsp = {source, target};
                for (int idx = 0; idx < 2; idx++) {
                    ArrayList<Overlay> overlays = new ArrayList<>();
                    for (double[] point : ph[idx].getPoints()) {
                        overlays.add(new PointOverlay(this.getContext(), point));
                    }
                    overlayService.addOverlays(dsp[idx], overlays);
                    dsp[idx].update();
                }
            }
        } catch (InterruptedException | ExecutionException e) {
            logService.error(e);
            cancel(e.getMessage());
        }
    }

    /**
     * make a <code>TurboRegImage</code> out of the source or target
     *
     * @param isTarget
     * @return
     * @throws ModuleException
     */
    private TurboRegImage makeTurboRegImage(boolean isTarget) throws ModuleException {
        final TransformationType transformationType = getTransformationType();
        final int pyramidDepth = TurboRegInterval.getPyramidDepth(source, target, overlayService);
        final TurboRegImage sourceImg = new TurboRegImage();
        sourceImg.setTransformation(transformationType);
        sourceImg.setStatusService(statusService);
        sourceImg.setPyramidDepth(pyramidDepth);
        sourceImg.setExecutionContext(this);
        sourceImg.setData(TurboRegInterval.getImageDisplayPlane(isTarget ? target : source, overlayService), isTarget);
        return sourceImg;
    }

    /**
     * make a <code>TurboRegMask</code> out of the overlays of a display
     *
     * @param display the display to be processed
     * @return a TurboRegMask representing the display's ROI.
     * @throws ModuleException
     */
    private TurboRegMask makeTurboRegMask(ImageDisplay display) throws ModuleException {
        final int pyramidDepth = TurboRegInterval.getPyramidDepth(source, target, overlayService);
        final TurboRegMask mask = new TurboRegMask();
        mask.setExecutionContext(this);
        mask.setStatusService(statusService);
        mask.setPyramidDepth(pyramidDepth);
        mask.setData(display, overlayService);
        return mask;
    }

}
