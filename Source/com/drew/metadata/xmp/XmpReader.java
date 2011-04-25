/*
 * This is public domain software - that is, you can do whatever you want
 * with it, and include it software that is licensed under the GNU or the
 * BSD license, or whatever other licence you choose, including proprietary
 * closed source licenses.  I do ask that you leave this header in tact.
 *
 * If you make modifications to this code that you think would benefit the
 * wider community, please send me a copy and I'll post it on my site.
 *
 * If you make use of this code, I'd appreciate hearing about it.
 *   drew@drewnoakes.com
 * Latest version of this software kept at
 *   http://drewnoakes.com/
 *
 * This is an addon to Drew Noakes metadata-extractor Library.
 * The addon is based on Drew's interface classes.
 * The XMP-Reader is based on Adobe's XMP-Toolkit:
 * Copyright (c) 1999 - 2007, Adobe Systems Incorporated All rights reserved.
 *
 * Created by Torsten Skadell on 15-Jan-2008
 * Updated by Drew Noakes on 24-04-2011
 */
package com.drew.metadata.xmp;

import com.adobe.xmp.XMPException;
import com.adobe.xmp.XMPIterator;
import com.adobe.xmp.XMPMeta;
import com.adobe.xmp.XMPMetaFactory;
import com.adobe.xmp.properties.XMPPropertyInfo;
import com.drew.imaging.jpeg.JpegProcessingException;
import com.drew.imaging.jpeg.JpegSegmentData;
import com.drew.imaging.jpeg.JpegSegmentReader;
import com.drew.lang.Rational;
import com.drew.metadata.Metadata;
import com.drew.metadata.MetadataReader;

import java.io.File;
import java.io.InputStream;
import java.util.Calendar;

/**
 * Extracts Xmp data from a JPEG header segment.
 * <p/>
 * The extractions is done with adobe's XmpCore-Library (XMP-Toolkit)
 * Copyright (c) 1999 - 2007, Adobe Systems Incorporated All rights reserved.
 *
 * @author Torsten Skadell, Drew Noakes
 */
public class XmpReader implements MetadataReader
{
    private static final int FMT_STRING = 1;
    private static final int FMT_RATIONAL = 2;
    private static final int FMT_INT = 3;
    private static final int FMT_DOUBLE = 4;

    private static final String SCHEMA_EXIF_SPECIFIC_PROPERTIES = "http://ns.adobe.com/exif/1.0/";
    private static final String SCHEMA_EXIF_ADDITIONAL_PROPERTIES = "http://ns.adobe.com/exif/1.0/aux/";
    private static final String SCHEMA_EXIF_TIFF_PROPERTIES = "http://ns.adobe.com/tiff/1.0/";

    private final byte[] _data;

    /** Creates an XmpReader for a JpegSegmentData object. */
    @Deprecated
    public XmpReader(JpegSegmentData segmentData)
    {
        this(segmentData.getSegment(JpegSegmentReader.SEGMENT_APP1, 1));
    }

    /** Creates an XmpReader for a Jpeg file. */
    @Deprecated
    public XmpReader(File file) throws JpegProcessingException
    {
        this(new JpegSegmentReader(file).readSegment(JpegSegmentReader.SEGMENT_APP1, 1));
    }

    /**
     * Creates an XmpReader for a Jpeg stream.
     *
     * @param is JPEG stream. Stream will be closed.
     */
    @Deprecated
    public XmpReader(InputStream is) throws JpegProcessingException
    {
        this(new JpegSegmentReader(is).readSegment(JpegSegmentReader.SEGMENT_APP1, 1));
    }

    /** Creates an XmpReader for the given JPEG header segment. */
    public XmpReader(byte[] data)
    {
        _data = data;
    }

    /**
     * Performs the XMP data extraction, adding found values to the specified instance of <code>Metadata</code>.
     * The extraction is done with Adobe's XmpCore-Lib (XMP-Toolkit)
     */
    public void extract(Metadata metadata)
    {
        if (_data == null)
            return;

        // once we know there's some data, create the directory and start working on it
        XmpDirectory directory = (XmpDirectory) metadata.getDirectory(XmpDirectory.class);

        // check for the header length
        if (_data.length <= 30) {
            directory.addError("Xmp data segment must contain at least 30 bytes");
            return;
        }

        // check for the header preamble
        if (!"http://ns.adobe.com/xap/1.0/\0".equals(new String(_data, 0, 29))) {
            directory.addError("Xmp data segment doesn't begin with 'http://ns.adobe.com/xap/1.0/'");
            return;
        }

        try {
            // the parser starts at offset of 29 Bytes
            byte[] xmpBuffer = new byte[_data.length - 29];
            System.arraycopy(_data, 29, xmpBuffer, 0, _data.length - 29);

            // use XMPMetaFactory to create a XMPMeta instance based on the parsed data buffer
            XMPMeta xmpMeta = XMPMetaFactory.parseFromBuffer(xmpBuffer);

            // read all the tags and send them to the directory
            // I've added some popular tags, feel free to add more tags
            processXmpTag(xmpMeta, directory, SCHEMA_EXIF_ADDITIONAL_PROPERTIES, "aux:LensInfo", XmpDirectory.TAG_LENS_INFO, FMT_STRING);
            processXmpTag(xmpMeta, directory, SCHEMA_EXIF_ADDITIONAL_PROPERTIES, "aux:Lens", XmpDirectory.TAG_LENS, FMT_STRING);
            processXmpTag(xmpMeta, directory, SCHEMA_EXIF_ADDITIONAL_PROPERTIES, "aux:SerialNumber", XmpDirectory.TAG_SERIAL, FMT_STRING);
            processXmpTag(xmpMeta, directory, SCHEMA_EXIF_ADDITIONAL_PROPERTIES, "aux:Firmware", XmpDirectory.TAG_FIRMWARE, FMT_STRING);

            processXmpTag(xmpMeta, directory, SCHEMA_EXIF_TIFF_PROPERTIES, "tiff:Make", XmpDirectory.TAG_MAKE, FMT_STRING);
            processXmpTag(xmpMeta, directory, SCHEMA_EXIF_TIFF_PROPERTIES, "tiff:Model", XmpDirectory.TAG_MODEL, FMT_STRING);

            processXmpTag(xmpMeta, directory, SCHEMA_EXIF_SPECIFIC_PROPERTIES, "exif:ExposureTime", XmpDirectory.TAG_EXPOSURE_TIME, FMT_STRING);
            processXmpTag(xmpMeta, directory, SCHEMA_EXIF_SPECIFIC_PROPERTIES, "exif:ExposureProgram", XmpDirectory.TAG_EXPOSURE_PROG, FMT_INT);
            processXmpTag(xmpMeta, directory, SCHEMA_EXIF_SPECIFIC_PROPERTIES, "exif:ApertureValue", XmpDirectory.TAG_APERTURE_VALUE, FMT_RATIONAL);
            processXmpTag(xmpMeta, directory, SCHEMA_EXIF_SPECIFIC_PROPERTIES, "exif:FNumber", XmpDirectory.TAG_F_NUMBER, FMT_RATIONAL);
            processXmpTag(xmpMeta, directory, SCHEMA_EXIF_SPECIFIC_PROPERTIES, "exif:FocalLength", XmpDirectory.TAG_FOCAL_LENGTH, FMT_RATIONAL);
            processXmpTag(xmpMeta, directory, SCHEMA_EXIF_SPECIFIC_PROPERTIES, "exif:ShutterSpeedValue", XmpDirectory.TAG_SHUTTER_SPEED, FMT_RATIONAL);
            processXmpDateTag(xmpMeta, directory, SCHEMA_EXIF_SPECIFIC_PROPERTIES, "exif:DateTimeOriginal", XmpDirectory.TAG_DATETIME_ORIGINAL);
            processXmpDateTag(xmpMeta, directory, SCHEMA_EXIF_SPECIFIC_PROPERTIES, "exif:DateTimeDigitized", XmpDirectory.TAG_DATETIME_DIGITIZED);

            for (XMPIterator iterator = xmpMeta.iterator(); iterator.hasNext();) {
                XMPPropertyInfo propInfo = (XMPPropertyInfo) iterator.next();
                String path = propInfo.getPath();
                Object value = propInfo.getValue();
                if (path != null && value != null)
                    directory.addProperty(path, value.toString());
            }

        } catch (XMPException e) {
            directory.addError("Error parsing XMP segment: " + e.getMessage());
        }
    }

    /** Reads an property value with given namespace URI and property name. Add property value to directory if exists */
    private void processXmpTag(XMPMeta meta, XmpDirectory directory, String schemaNS, String propName, int tagType, int formatCode) throws XMPException
    {
        String property = meta.getPropertyString(schemaNS, propName);

        if (property == null)
            return;

        switch (formatCode) {
            case FMT_RATIONAL:
                String[] rationalParts = property.split("/", 2);

                if (rationalParts.length == 2) {
                    Rational rational = new Rational((long) Float.parseFloat(rationalParts[0]), (long) Float.parseFloat(rationalParts[1]));
                    directory.setRational(tagType, rational);
                } else {
                    directory.addError("Error in rational format for tag " + tagType);
                }
                break;
            case FMT_INT:
                directory.setInt(tagType, Integer.valueOf(property));
                break;
            case FMT_DOUBLE:
                directory.setDouble(tagType, Double.valueOf(property));
                break;
            case FMT_STRING:
                directory.setString(tagType, property);
                break;
            default:
                directory.addError("Unknown format code " + formatCode + " for tag " + tagType);
        }
    }

    void processXmpDateTag(XMPMeta meta, XmpDirectory directory, String schemaNS, String propName, int tagType) throws XMPException
    {
        Calendar cal = meta.getPropertyCalendar(schemaNS, propName);

        if (cal == null)
            return;

        directory.setDate(tagType, cal.getTime());
    }
}
