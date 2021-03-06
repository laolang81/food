/*
 *  ====================================================================
 *    Licensed to the Apache Software Foundation (ASF) under one or more
 *    contributor license agreements.  See the NOTICE file distributed with
 *    this work for additional information regarding copyright ownership.
 *    The ASF licenses this file to You under the Apache License, Version 2.0
 *    (the "License"); you may not use this file except in compliance with
 *    the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 * ====================================================================
 */

package com.sniper.springmvc.utils;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

import javax.imageio.ImageIO;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.batik.anim.dom.SVGDOMImplementation;
import org.apache.batik.svggen.SVGGraphics2D;
import org.apache.batik.transcoder.wmf.tosvg.WMFPainter;
import org.apache.batik.transcoder.wmf.tosvg.WMFRecordStore;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFImageRenderer;
import org.apache.poi.xslf.usermodel.XSLFPictureData;
import org.apache.poi.xslf.usermodel.XSLFRenderingHint;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;

/**
 * Convert each slide of a .pptx presentation into SVG
 * 
 * @author Yegor Kozlov
 */
public class PPTX2SVG {

	static void usage() {
		System.out.println("Usage: PPTX2SVG  <pptx file>");
	}

	public static void main(String[] args) throws Exception {

		String file = "/home/sniper/图片/1.pptx";
		System.out.println("Processing " + file);

		// read the .pptx file
		XMLSlideShow ppt = new XMLSlideShow(OPCPackage.open(file));

		Dimension pgsize = ppt.getPageSize();

		// convert each slide into a .svg file
		XSLFSlide[] slide = ppt.getSlides();
		for (int i = 0; i < slide.length; i++) {
			// Create initial SVG DOM

			DOMImplementation domImpl = SVGDOMImplementation
					.getDOMImplementation();
			String svgNS = SVGDOMImplementation.SVG_NAMESPACE_URI;
			Document doc = domImpl.createDocument(svgNS, "svg", null);
			// Use Batik SVG Graphics2D driver
			SVGGraphics2D graphics = new SVGGraphics2D(doc);
			
			/*TestSVGGen test = new TestSVGGen();
	        test.paint(graphics);*/
	        
			graphics.setRenderingHint(XSLFRenderingHint.IMAGE_RENDERER,
					new WMFImageRender());
			graphics.setSVGCanvasSize(pgsize);

			String title = slide[i].getTitle();
			System.out.println("Rendering slide " + (i + 1)
					+ (title == null ? "" : ": " + title));
			
			// draw stuff. All the heavy-lifting happens here
			slide[i].draw(graphics);
			
			
			
			// save the result.
			int sep = file.lastIndexOf(".");
			String fname = file.substring(0, sep == -1 ? file.length() : sep)
					+ "-" + (i + 1) + ".svg";
			OutputStreamWriter out = new OutputStreamWriter(
					new FileOutputStream(fname), "UTF-8");
			DOMSource domSource = new DOMSource(graphics.getRoot());
			StreamResult streamResult = new StreamResult(out);
			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer serializer = tf.newTransformer();
			serializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			serializer.setOutputProperty(OutputKeys.INDENT, "yes");
			serializer.transform(domSource, streamResult);
			out.flush();
			out.close();
		}
		System.out.println("Done");
	}

	/**
	 * 
	 * Image renderer with support for .wmf images
	 */
	static class WMFImageRender extends XSLFImageRenderer {

		/**
		 * Use Apache Batik to render WMF, delegate all other types of images to
		 * the javax.imageio framework
		 */
		@Override
		public boolean drawImage(Graphics2D graphics, XSLFPictureData data,
				Rectangle2D anchor) {
			try {
				// see what type of image we are
				PackagePart part = data.getPackagePart();
				String contentType = part.getContentType();
				if (contentType.equals("image/x-wmf")) {
					WMFRecordStore currentStore = new WMFRecordStore();
					currentStore
							.read(new DataInputStream(part.getInputStream()));
					int wmfwidth = currentStore.getWidthPixels();
					float conv = (float) anchor.getWidth() / wmfwidth;

					// Build a painter for the RecordStore
					WMFPainter painter = new WMFPainter(currentStore,
							(int) anchor.getX(), (int) anchor.getY(), conv);
					painter.paint(graphics);
				} else {
					BufferedImage img = ImageIO.read(data.getPackagePart()
							.getInputStream());
					graphics.drawImage(img, (int) anchor.getX(),
							(int) anchor.getY(), (int) anchor.getWidth(),
							(int) anchor.getHeight(), null);
				}
			} catch (Exception e) {
				return false;
			}
			return true;
		}

		/**
		 * Convert data form the supplied package part into a BufferedImage.
		 * This method is used to create texture paint.
		 */
		@Override
		public BufferedImage readImage(PackagePart packagePart)
				throws IOException {
			String contentType = packagePart.getContentType();
			if (contentType.equals("image/x-wmf")) {
				try {
					WMFRecordStore currentStore = new WMFRecordStore();
					currentStore.read(new DataInputStream(packagePart
							.getInputStream()));
					int wmfwidth = currentStore.getWidthPixels();
					int wmfheight = currentStore.getHeightPixels();

					BufferedImage img = new BufferedImage(wmfwidth, wmfheight,
							BufferedImage.TYPE_INT_RGB);
					Graphics2D graphics = img.createGraphics();

					// Build a painter for the RecordStore
					WMFPainter painter = new WMFPainter(currentStore, 0, 0,
							1.0f);
					painter.paint(graphics);

					return img;
				} catch (IOException e) {
					return null;
				}
			} else {
				return ImageIO.read(packagePart.getInputStream());
			}
		}

	}
}