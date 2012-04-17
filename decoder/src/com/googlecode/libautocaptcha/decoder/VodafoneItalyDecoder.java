/*
 *  This file is part of libautocaptcha
 *  
 *  libautocaptcha is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  libautocaptcha is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License
 *  along with libautocaptcha.  If not, see <http://www.gnu.org/licenses/>.
 *  
 */

package com.googlecode.libautocaptcha.decoder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.sourceforge.javaocr.Image;
import net.sourceforge.javaocr.cluster.FeatureExtractor;
import net.sourceforge.javaocr.filter.GrayscaleToRGBA;
import net.sourceforge.javaocr.filter.MedianFilter;
import net.sourceforge.javaocr.filter.RGBAToGrayscale;
import net.sourceforge.javaocr.filter.ThresholdFilter;
import net.sourceforge.javaocr.ocr.PixelImage;
import net.sourceforge.javaocr.ocr.Shrinker;
import net.sourceforge.javaocr.ocr.SlicerV;
import net.sourceforge.javaocr.plugin.cluster.Cluster;
import net.sourceforge.javaocr.plugin.cluster.Match;
import net.sourceforge.javaocr.plugin.cluster.MetricMatcher;
import net.sourceforge.javaocr.plugin.cluster.SigmaWeightedEuclidianDistanceCluster;
import net.sourceforge.javaocr.plugin.moment.HuMoments;
import net.sourceforge.javaocr.plugin.nn.ResizeFilter;

import com.googlecode.libautocaptcha.Decoder;

/**
 * Decoder for vodafone.it visual CAPTCHAs.
 * @author Andrea De Pasquale
 */
public class VodafoneItalyDecoder implements Decoder {
  
  protected static String DATA_CLASS = 
      "com.googlecode.libautocaptcha.decoder.data.VodafoneItalyData";
  protected static String BACKGROUND_FIELD = "BACKGROUND";
  protected static String CHARACTERS_FIELD = "CHARACTERS";
  protected static String CLUSTERS_FIELD = "CLUSTERS";
  
  public static int WIDTH = 150;
  public static int HEIGHT = 25;
  
  protected static int BG = 0; // black background
  protected static int FG = 4; // white foreground
  
  protected RGBAToGrayscale grayFilter;
  protected GrayscaleToRGBA rgbaFilter;
  protected Image background;
  protected FeatureExtractor featureExtractor;
  protected MetricMatcher metricMatcher;
  protected Map<Cluster, Character> characters;
  protected Map<Character, Cluster> clusters;
  
  public VodafoneItalyDecoder() {
    grayFilter = new RGBAToGrayscale();
    rgbaFilter = new GrayscaleToRGBA();
    background = new PixelImage(WIDTH, HEIGHT);
    featureExtractor = new HuMoments();
    metricMatcher = new MetricMatcher();
    characters = new HashMap<Cluster, Character>();
    clusters = new HashMap<Character, Cluster>();
    
    try {
      Class<?> data = Class.forName(DATA_CLASS);
      int[] backgroundBuffer = (int[]) data.getField(BACKGROUND_FIELD).get(null);
      background = new PixelImage(backgroundBuffer, WIDTH, HEIGHT);
      char[] characterArray = (char[]) data.getField(CHARACTERS_FIELD).get(null);
      Cluster[] clusterArray = (Cluster[]) data.getField(CLUSTERS_FIELD).get(null);
      for (int c = 0; c < characterArray.length && c < clusterArray.length; ++c) {
        clusters.put(characterArray[c], clusterArray[c]);
        characters.put(clusterArray[c], characterArray[c]);
        metricMatcher.getClusters().add(clusterArray[c]);
      }
    } catch (Exception e) {
      System.err.println("WARNING: no data class or incomplete data class");
    }
  }
  
  @Override
  public String decode(Image captcha) {
    if (captcha.getWidth()  != WIDTH || 
        captcha.getHeight() != HEIGHT)
      return null;

    grayFilter.process(captcha);
    Image foreground = extractForeground(captcha);
    List<Image> glyphs = slice(foreground);
    return match(glyphs);
  }
  
  /**
   * Remove the fixed, known background and threshold the result.
   * @param image Grayscale CAPTCHA image.
   * @return Binarized image with glyphs as foreground.
   */
  protected Image extractForeground(Image image) {
    final int TH = 47; // threshold for background
    for (int i = 0; i < HEIGHT; ++i) {
      for (image.iterateH(i), background.iterateH(i); 
          image.hasNext() && background.hasNext();) {
        if (Math.abs(image.next() - background.next()) < TH) 
          image.put(BG);
        else image.put(FG);
      }
    }
    
    return image;
    
//    final int WINDOW = 2;
//    final int WINDOW_2 = 4; // WINDOW^2
//    Image foreground = new PixelImage(WIDTH, HEIGHT);
//    MedianFilter medianFilter = new MedianFilter(foreground, WINDOW);
//    medianFilter.process(image);
//    final int TH_MED = 2 * FG / WINDOW_2;
//    ThresholdFilter thresholdFilter = new ThresholdFilter(TH_MED, FG, BG);
//    thresholdFilter.process(foreground);
//    return foreground;
  }
  
  /**
   * Extract the background from a list of images.
   * @param images List of images with same background.
   * @return The background image, black if none. 
   */
  protected Image extractBackground(List<Image> images) {
    PixelImage background = new PixelImage(WIDTH, HEIGHT);
    for (Image i : images) grayFilter.process(i);
    
    // iterate for every pixel (x,y)
    for (int y = 0; y < HEIGHT; ++y) {
      for (int x = 0; x < WIDTH; ++x) {
        
        // build histogram of single pixel from many images 
        int[] histogram = new int[256];
        for (Image i : images) histogram[i.get(x, y)]++;

        final int H_MIN = 160; 
        final int H_MAX = 256;
        
        int maxID = H_MIN-1;
        int max = histogram[maxID];
        
        for (int h = H_MIN; h < H_MAX; ++h) {
          if (histogram[h] > max) {
            maxID = h;
            max = histogram[maxID];
          }
        }
        
        // use the value that occurs most frequently
        background.put(x, y, maxID);
      }
    }
    
    background.copy(this.background);
    rgbaFilter.process(background);
    return background;
  }
  
  /**
   * Slice an image in parts, one for every glyph found.
   * @param image Binarized image with glyphs as foreground.
   * @return A list of glyphs found in the source image.
   */
  protected List<Image> slice(Image image) {
    final int GLYPH_MIN_W = 3;
    final int GLYPH_MAX_W = 18;
    final int WIN = 2; // window for mean
    final int WIN_2 = 1; // half window for mean
    final int TH = 2 * FG / (WIN*WIN) - 1; // threshold for mean 
    ThresholdFilter thresholdFilter = new ThresholdFilter(TH, FG, BG);
    
    Shrinker shrinker = new Shrinker(BG);
    List<Image> glyphs = new ArrayList<Image>();
    SlicerV slicer = new SlicerV(image, BG);
    slicer.slice(0);
    
    while (slicer.hasNext()) {
      Image glyph = shrinker.shrink(slicer.next());
      if (glyph != null) {
        int glyphW = glyph.getWidth();
        int glyphH = glyph.getHeight();
        
        if (glyphW > GLYPH_MIN_W) {
          
          Image median = new PixelImage(glyphW+WIN, glyphH+WIN);
          MedianFilter medianFilter = new MedianFilter(median, WIN);
          medianFilter.process(glyph.chisel(
              -WIN_2, -WIN_2, glyphW+WIN, glyphH+WIN));
          thresholdFilter.process(median);
          
          if (glyphW < GLYPH_MAX_W) {
            Image square = new PixelImage(24, 24);
            ResizeFilter resizeFilter = new ResizeFilter(square);
            resizeFilter.process(shrinker.shrink(median));
            glyphs.add(square);
          } else {
            SlicerV slicer2 = new SlicerV(median, BG);
            slicer2.slice(0);
            while (slicer2.hasNext()) {
              Image glyph2 = shrinker.shrink(slicer2.next());
              if (glyph2 != null && glyph2.getWidth() > GLYPH_MIN_W) {
                Image square2 = new PixelImage(24, 24);
                ResizeFilter resizeFilter = new ResizeFilter(square2);
                resizeFilter.process(shrinker.shrink(glyph2));
                glyphs.add(square2);
              }
            }
          }
          
//        Image temp = new PixelImage(glyphW+1, glyphH+1);
//        Image strElem = new PixelImage(new int[] {FG, FG, FG, BG}, 2, 2);
//        ClosingFilter closingFilter = new ClosingFilter(strElem, temp);
//        closingFilter.process(glyph.chisel(0, 0, glyphW+1, glyphH+1));
//        temp.chisel(0, 0, glyphW, glyphH).copy(glyph);
//        glyphs.add(glyph);
          
        }
      }
    }
    
    return glyphs;
  }

  /**
   * Match a list of glyphs to a character string.
   * @param glyphs List of glyph images.
   * @return A text string containing characters.
   */
  protected String match(List<Image> glyphs) {
    String text = "";
    for (Image glyph : glyphs) {
      double[] moments = featureExtractor.extract(glyph);
      List<Match> matches = metricMatcher.match(moments);
      if (matches.size() > 0) {
        text += characters.get(matches.get(0).getCluster());
        
        for (Match m : matches) {
          Character c = characters.get(m.getCluster());
          System.out.print(c+", ");
        }
        System.out.println();
        
        System.out.println("Features: ");
        for (double f : moments)
          System.out.println(f); 
        System.out.println();

        System.out.println("Distance: ");
        for (Match m : matches)
          System.out.println(m.getCluster().distance(moments));
        System.out.println();
      }
    }
    return text;
  }
  
  /**
   * Teach the matcher which character is contained into a glyph. 
   * @param glyph Input glyph image.
   * @param character Associated character.
   */
  protected void train(Image glyph, char character) {
    double[] moments = featureExtractor.extract(glyph);
    System.out.println("Features for char " + character + ": ");
    for (double m : moments) System.out.println(m);
    
    Cluster cluster = clusters.get(character);
    if (cluster == null) {
      System.out.println("Inserting new cluster for char " + character + "...");
//      cluster = new MahalanobisDistanceCluster(featureExtractor.getSize());
//      cluster = new EuclidianDistanceCluster(featureExtractor.getSize());
      cluster = new SigmaWeightedEuclidianDistanceCluster(featureExtractor.getSize());
      clusters.put(character, cluster);
      characters.put(cluster, character);
      metricMatcher.getClusters().add(cluster);
    }
    System.out.println("Training cluster for char " + character + "...");
    cluster.train(moments);
    System.out.println("Centroid for char " + character + ": ");
    for (double c : cluster.center()) System.out.println(c);
    System.out.println();
  }
  
}
