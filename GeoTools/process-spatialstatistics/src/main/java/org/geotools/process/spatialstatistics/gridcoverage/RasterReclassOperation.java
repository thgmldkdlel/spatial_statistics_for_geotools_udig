/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2014, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotools.process.spatialstatistics.gridcoverage;

import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.media.jai.PlanarImage;
import javax.media.jai.iterator.RectIter;
import javax.media.jai.iterator.RectIterFactory;
import javax.media.jai.iterator.WritableRectIter;

import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.process.ProcessException;
import org.geotools.process.spatialstatistics.core.SSUtils;
import org.geotools.process.spatialstatistics.enumeration.RasterPixelType;
import org.geotools.resources.i18n.ErrorKeys;
import org.geotools.resources.i18n.Errors;
import org.geotools.util.logging.Logging;
import org.jaitools.tiledimage.DiskMemImage;

/**
 * Reclassifies a raster data.
 * 
 * @author Minpa Lee, MangoSystem
 * 
 * @source $URL$
 */
public class RasterReclassOperation extends RasterProcessingOperation {
    protected static final Logger LOGGER = Logging.getLogger(RasterKernelDensityOperation.class);

    private SortedMap<Double, ReclassRange> reclassRange = new TreeMap<Double, ReclassRange>();

    public GridCoverage2D execute(GridCoverage2D inputGc, Integer bandIndex, String ranges) {
        // ranges: "0.00 30.00 1; 30.00 270.00 2; 270.00 365.00 3"
        if (!prepareRanges(ranges)) {
            throw new ProcessException(Errors.format(ErrorKeys.ILLEGAL_ARGUMENT_$1, "ranges"));
        }

        RasterPixelType pixelType = RasterPixelType.SHORT;
        if (reclassRange.lastKey() > Short.MAX_VALUE && reclassRange.lastKey() < Integer.MAX_VALUE) {
            pixelType = RasterPixelType.INTEGER;
        } else if (reclassRange.lastKey() > Integer.MAX_VALUE
                && reclassRange.lastKey() < Float.MAX_VALUE) {
            pixelType = RasterPixelType.FLOAT;
        }

        DiskMemImage outputImage = this.createDiskMemImage(inputGc, pixelType);
        this.NoData = pixelType == RasterPixelType.INTEGER ? Integer.MIN_VALUE : Float.MIN_VALUE;

        final double inputNoData = RasterHelper.getNoDataValue(inputGc);
        PlanarImage inputImage = (PlanarImage) inputGc.getRenderedImage();

        RectIter inputIter = RectIterFactory.create(inputImage, inputImage.getBounds());
        WritableRectIter writerIter = RectIterFactory.createWritable(outputImage,
                outputImage.getBounds());

        inputIter.startLines();
        writerIter.startLines();
        while (!inputIter.finishedLines() && !writerIter.finishedLines()) {
            inputIter.startPixels();
            writerIter.startPixels();
            while (!inputIter.finishedPixels() && !writerIter.finishedPixels()) {
                double gridVal = inputIter.getSampleDouble(bandIndex);

                if (SSUtils.compareDouble(inputNoData, gridVal)) {
                    writerIter.setSample(0, NoData);
                } else {
                    double retVal = this.NoData;
                    for (ReclassRange rge : reclassRange.values()) {
                        if (gridVal >= rge.minimum
                                && gridVal < (rge.maximum + SSUtils.DOUBLE_COMPARE_TOLERANCE)) {
                            retVal = rge.key;
                            updateStatistics(retVal);
                            break;
                        }
                    }
                    writerIter.setSample(0, retVal);
                }

                inputIter.nextPixel();
                writerIter.nextPixel();
            }
            inputIter.nextLine();
            writerIter.nextLine();
        }

        return createGridCoverage("Reclass", outputImage, 0, NoData, MinValue, MaxValue, Extent);
    }

    private boolean prepareRanges(String ranges) {
        // remove duplicate space
        while (ranges.contains("  ")) {
            ranges = ranges.replace("  ", " ");
        }

        String[] reclassIntervals = ranges.split(";");
        for (String reclass : reclassIntervals) {
            try {
                String[] vals = reclass.trim().split(" ");
                ReclassRange key = new ReclassRange();
                if (vals.length >= 3) {
                    key.minimum = Double.valueOf(vals[0]);
                    key.maximum = Double.valueOf(vals[1]);
                    key.key = Double.valueOf(vals[2]);
                } else if (vals.length == 2) {
                    key.minimum = Double.valueOf(vals[0]);
                    key.key = Double.valueOf(vals[1]);
                }
                reclassRange.put(key.key, key);
            } catch (NumberFormatException e) {
                LOGGER.log(Level.FINE, e.getMessage(), e);
                return false;
            }
        }

        return true;
    }

    final class ReclassRange {
        public Double key = null;

        public Double minimum = Double.MIN_VALUE;

        public Double maximum = Double.MAX_VALUE;

        public ReclassRange() {
        }

        public ReclassRange(Double key, Double min, Double max) {
            this.key = key;
            this.minimum = min;
            this.maximum = max;
        }

        @Override
        public String toString() {
            return key + " : " + minimum + " ~ " + maximum;
        }
    }
}
