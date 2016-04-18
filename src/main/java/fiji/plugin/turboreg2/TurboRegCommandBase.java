package fiji.plugin.turboreg2;

import net.imagej.ImageJ;
import org.scijava.ItemIO;
import org.scijava.app.StatusService;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.thread.ThreadService;

import org.scijava.command.ContextCommand;
import net.imagej.table.DefaultResultsTable;
import net.imagej.table.ResultsTable;
import net.imagej.table.Table;
import net.imagej.table.TableDisplay;
import org.scijava.display.Display;
import org.scijava.display.DisplayService;

/**
 * @author Philippe Thevenaz
 * @author Lee Kamentsky
 *
 * Shared support structure for TurboReg command plugins.
 */
abstract class TurboRegCommandBase extends ContextCommand {

    @Parameter
    protected StatusService statusService;
    @Parameter
    protected ThreadService threadService;
    @Parameter
    protected LogService logService;
    @Parameter
    DisplayService displayService;

    @Parameter(type = ItemIO.INPUT, label = "Transform", choices = {
        "Translation", "Rigid body", "Scaled rotation", "Affine",
        "Bilinear"}, description = "The transform choice controls the constraints on the alignment transformation.\n"
            + "Translation computes the X and Y offset that best aligns the two images.\n"
            + "Rigid body allows the transformation to offset and rotate the image.\n"
            + "Scaled rotation computes a transformation that can enlarge or shrink the image\n"
            + "in addition to offsetting and rotating it.\n"
            + "Affine adds shearing to the scaled rotation transform.\n"
            + "Bilinear adds warping to the affine transform.")
    private String transform = "Rigid body";

    /**
     * @return the table display for the plugin
     */
    abstract protected TableDisplay getTableDisplay();

    /**
     * Set the display table for output.
     *
     */
    abstract protected void setTableDisplay(TableDisplay display);

    protected ResultsTable getResultsTable() {
        TableDisplay alignment = getTableDisplay();
        if (alignment == null) {
            return null;
        }
        for (final Table<?, ?> table : alignment) {
            if (table instanceof ResultsTable) {
                return (ResultsTable) table;
            }
        }
        return null;
    }

    protected void createResultsTable() {
        ResultsTable table = new DefaultResultsTable();
        table.appendColumn(TurboRegPointHandler.SOURCE_X);
        table.appendColumn(TurboRegPointHandler.SOURCE_Y);
        table.appendColumn(TurboRegPointHandler.TARGET_X);
        table.appendColumn(TurboRegPointHandler.TARGET_Y);
        Display<?> display = displayService.createDisplay("Refined Landmarks", table);
        if (display instanceof TableDisplay) {
            setTableDisplay((TableDisplay) display);
        }
    }

    /**
     * @return the transformation type selected by the user
     */
    protected TransformationType getTransformationType() {
        final TransformationType transformationType = TransformationType.fromDisplayName(transform);
        return transformationType;
    }

    public static void main(final String... args) throws Exception {
        // Launch ImageJ as usual.
        final ImageJ ij = net.imagej.Main.launch(args);
    }

}
