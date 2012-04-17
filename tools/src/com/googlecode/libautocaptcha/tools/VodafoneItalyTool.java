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

package com.googlecode.libautocaptcha.tools;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import net.sourceforge.javaocr.awt.AwtImage;
import net.sourceforge.javaocr.filter.ThresholdFilter;
import net.sourceforge.javaocr.plugin.cluster.Cluster;
import net.sourceforge.javaocr.plugin.cluster.MahalanobisDistanceCluster;
import net.sourceforge.javaocr.plugin.cluster.SigmaWeightedEuclidianDistanceCluster;

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.ProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.ClientPNames;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.protocol.HTTP;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import com.googlecode.libautocaptcha.decoder.VodafoneItalyDecoder;

/**
 * GUI utility for VodafoneItalyDecoder.
 * 
 * This class can download CAPTCHAs, extract background, train the matcher
 * and test recognition results with detailed statistics. 
 * 
 * @author Andrea De Pasquale
 */
public class VodafoneItalyTool extends VodafoneItalyDecoder {

  private JFrame frame;
  private JTabbedPane tabbedPane;
  private JTextField usernameField;
  private JPasswordField passwordField;
  private JTextField receiverField;
  private JTextField numberField;
  private JLabel backgroundImage;
  private JPanel trainingLoadPanel;
  private BufferedImage trainingCaptcha;
  private JLabel trainingCaptchaImage;
  private JPanel trainingSavePanel;
  private Image[] trainingGlyph;
  private JLabel[] trainingGlyphImage;
  private JTextField[] trainingGlyphField;
  private JPanel testingLoadPanel;
  private BufferedImage testingCaptcha;
  private JLabel testingCaptchaImage;
  private JPanel testingSavePanel;
  private Image[] testingGlyph;
  private JLabel[] testingGlyphImage;
  private JTextField[] testingGlyphField;
  private JTextField javaField;
  private JProgressBar statusBar;
  private JLabel statusLabel;
  
  DefaultHttpClient httpClient;
  boolean loggedIn = false;

  private String outFolderName;
  private String outFileName;
  private File tempFolder;
  private JTextArea testingViewArea;
  
  public static void main(String[] args) {
    EventQueue.invokeLater(new Runnable() {
      public void run() {
        try {
          VodafoneItalyTool window = new VodafoneItalyTool();
          window.frame.setVisible(true);
        } catch (Exception e) {
          e.printStackTrace();
        }
      }
    });
  }

  public VodafoneItalyTool() {
    initFolders();
    initHttpClient();
    initFrame();
  }

  private void initFolders() {
    String outClass = DATA_CLASS.replace('.', '/');
    outFolderName = "./decoder/src/"+outClass.substring(0, outClass.lastIndexOf('/'));
    outFileName = outClass.substring(outClass.lastIndexOf('/')+1)+".java";
    try {
      tempFolder = File.createTempFile("libautocaptcha_", "");
      tempFolder.delete();
      tempFolder.mkdir();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
  private void initHttpClient() {
    httpClient = new DefaultHttpClient();
    httpClient.getParams().setParameter(CoreProtocolPNames.USER_AGENT, "Mozilla/5.0");
    httpClient.getParams().setParameter(ClientPNames.ALLOW_CIRCULAR_REDIRECTS, true);
    httpClient.setCookieStore(new BasicCookieStore());
    httpClient.setRedirectStrategy(new DefaultRedirectStrategy() {
      @Override
      public boolean isRedirected(HttpRequest request, HttpResponse response, 
          HttpContext context) throws ProtocolException {
        boolean isRedirected = super.isRedirected(request, response, context);
        if (!isRedirected &&
            request.getRequestLine().getMethod().equals(HttpPost.METHOD_NAME) &&
            response.getStatusLine().getStatusCode() == 302) 
          return true; // allow POST redirection
        return isRedirected;
      }      
    });
  }
  
  private void initFrame() {
    frame = new JFrame();
    frame.setTitle("libautocaptcha vodafone.it tool");
    frame.setBounds(100, 100, 450, 300);
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    
    tabbedPane = new JTabbedPane(JTabbedPane.TOP);
    frame.getContentPane().add(tabbedPane, BorderLayout.CENTER);
    
    JPanel setupPanel = new JPanel();
    FlowLayout flowLayout = (FlowLayout) setupPanel.getLayout();
    flowLayout.setAlignment(FlowLayout.LEFT);
    tabbedPane.addTab("Setup", null, setupPanel, null);
    
    JPanel setupFormPanel = new JPanel();
    setupPanel.add(setupFormPanel);
    GridBagLayout gbl_setupFormPanel = new GridBagLayout();
    gbl_setupFormPanel.columnWidths = new int[]{150, 250};
    gbl_setupFormPanel.rowHeights = new int[]{0, 0, 0};
    gbl_setupFormPanel.columnWeights = new double[]{0.0, 1.0};
    gbl_setupFormPanel.rowWeights = new double[]{0.0, 0.0, 0.0};
    setupFormPanel.setLayout(gbl_setupFormPanel);
    
    JLabel usernameLabel = new JLabel("Username");
    GridBagConstraints gbc_usernameLabel = new GridBagConstraints();
    gbc_usernameLabel.anchor = GridBagConstraints.WEST;
    gbc_usernameLabel.fill = GridBagConstraints.VERTICAL;
    gbc_usernameLabel.insets = new Insets(0, 0, 5, 5);
    gbc_usernameLabel.gridx = 0;
    gbc_usernameLabel.gridy = 0;
    setupFormPanel.add(usernameLabel, gbc_usernameLabel);
    usernameField = new JTextField();
    GridBagConstraints gbc_usernameField = new GridBagConstraints();
    gbc_usernameField.fill = GridBagConstraints.BOTH;
    gbc_usernameField.insets = new Insets(0, 0, 5, 0);
    gbc_usernameField.gridx = 1;
    gbc_usernameField.gridy = 0;
    setupFormPanel.add(usernameField, gbc_usernameField);
    usernameField.setColumns(10);
    
    JLabel passwordLabel = new JLabel("Password");
    GridBagConstraints gbc_passwordLabel = new GridBagConstraints();
    gbc_passwordLabel.anchor = GridBagConstraints.WEST;
    gbc_passwordLabel.fill = GridBagConstraints.VERTICAL;
    gbc_passwordLabel.insets = new Insets(0, 0, 5, 5);
    gbc_passwordLabel.gridx = 0;
    gbc_passwordLabel.gridy = 1;
    setupFormPanel.add(passwordLabel, gbc_passwordLabel);
    passwordField = new JPasswordField();
    GridBagConstraints gbc_passwordField = new GridBagConstraints();
    gbc_passwordField.fill = GridBagConstraints.BOTH;
    gbc_passwordField.insets = new Insets(0, 0, 5, 0);
    gbc_passwordField.gridx = 1;
    gbc_passwordField.gridy = 1;
    setupFormPanel.add(passwordField, gbc_passwordField);
    passwordField.setColumns(10);
    
    JLabel receiverLabel = new JLabel("Mobile number");
    GridBagConstraints gbc_receiverLabel = new GridBagConstraints();
    gbc_receiverLabel.fill = GridBagConstraints.VERTICAL;
    gbc_receiverLabel.anchor = GridBagConstraints.WEST;
    gbc_receiverLabel.insets = new Insets(0, 0, 5, 5);
    gbc_receiverLabel.gridx = 0;
    gbc_receiverLabel.gridy = 2;
    setupFormPanel.add(receiverLabel, gbc_receiverLabel);
    
    receiverField = new JTextField();
    GridBagConstraints gbc_receiverField = new GridBagConstraints();
    gbc_receiverField.insets = new Insets(0, 0, 5, 0);
    gbc_receiverField.fill = GridBagConstraints.BOTH;
    gbc_receiverField.gridx = 1;
    gbc_receiverField.gridy = 2;
    setupFormPanel.add(receiverField, gbc_receiverField);
    receiverField.setColumns(10);
    
    JPanel backgroundPanel = new JPanel();
    FlowLayout flowLayout_1 = (FlowLayout) backgroundPanel.getLayout();
    flowLayout_1.setAlignment(FlowLayout.LEFT);
    tabbedPane.addTab("Background", null, backgroundPanel, null);
    
    JPanel backgroundFormPanel = new JPanel();
    backgroundPanel.add(backgroundFormPanel);
    GridBagLayout gbl_backgroundFormPanel = new GridBagLayout();
    gbl_backgroundFormPanel.columnWidths = new int[]{150, 50, 100, 0};
    gbl_backgroundFormPanel.rowHeights = new int[]{0, 25, 0};
    gbl_backgroundFormPanel.columnWeights = new double[]{0.0, 0.0, 0.0, Double.MIN_VALUE};
    gbl_backgroundFormPanel.rowWeights = new double[]{0.0, 0.0, Double.MIN_VALUE};
    backgroundFormPanel.setLayout(gbl_backgroundFormPanel);
    
    JLabel numberLabel = new JLabel("Number of CAPTCHAs");
    GridBagConstraints gbc_numberLabel = new GridBagConstraints();
    gbc_numberLabel.anchor = GridBagConstraints.WEST;
    gbc_numberLabel.insets = new Insets(0, 0, 5, 5);
    gbc_numberLabel.gridx = 0;
    gbc_numberLabel.gridy = 0;
    backgroundFormPanel.add(numberLabel, gbc_numberLabel);
    
    numberField = new JTextField();
    numberField.setText("5");
    GridBagConstraints gbc_numberField = new GridBagConstraints();
    gbc_numberField.anchor = GridBagConstraints.WEST;
    gbc_numberField.insets = new Insets(0, 0, 5, 5);
    gbc_numberField.gridx = 1;
    gbc_numberField.gridy = 0;
    backgroundFormPanel.add(numberField, gbc_numberField);
    numberField.setColumns(3);
    
    JButton numberButton = new JButton("Download");
    numberButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int counter = 0;
        
        do {
          BufferedImage captcha = downloadCAPTCHA();
          if (captcha != null) {
            try {
              int number = new File(tempFolder.getAbsolutePath()).listFiles().length;
              File file = new File(tempFolder.getAbsolutePath() + "/background_"+number+".png");
              ImageIO.write(captcha, "png", file);
              Thread.sleep(2500);
            } catch (Exception x) {
              x.printStackTrace();
            }
          }
        } while (++counter < Integer.valueOf(numberField.getText()));
        
        Image background = loadBackground();
        if (background != null) {
          backgroundImage.setIcon(new ImageIcon(background));
        }
      }
    });
    GridBagConstraints gbc_numberButton = new GridBagConstraints();
    gbc_numberButton.anchor = GridBagConstraints.NORTHWEST;
    gbc_numberButton.insets = new Insets(0, 0, 5, 0);
    gbc_numberButton.gridx = 2;
    gbc_numberButton.gridy = 0;
    backgroundFormPanel.add(numberButton, gbc_numberButton);
    
    JLabel backgroundLabel = new JLabel("Current background");
    GridBagConstraints gbc_backgroundLabel = new GridBagConstraints();
    gbc_backgroundLabel.anchor = GridBagConstraints.WEST;
    gbc_backgroundLabel.insets = new Insets(0, 0, 0, 5);
    gbc_backgroundLabel.gridx = 0;
    gbc_backgroundLabel.gridy = 1;
    backgroundFormPanel.add(backgroundLabel, gbc_backgroundLabel);
    
    backgroundImage = new JLabel("");
    GridBagConstraints gbc_backgroundImage = new GridBagConstraints();
    gbc_backgroundImage.anchor = GridBagConstraints.WEST;
    gbc_backgroundImage.gridwidth = 2;
    gbc_backgroundImage.insets = new Insets(0, 0, 0, 5);
    gbc_backgroundImage.gridx = 1;
    gbc_backgroundImage.gridy = 1;
    backgroundFormPanel.add(backgroundImage, gbc_backgroundImage);
    
    JPanel trainingPanel = new JPanel();
    tabbedPane.addTab("Training", null, trainingPanel, null);
    GridBagLayout gbl_trainingPanel = new GridBagLayout();
    gbl_trainingPanel.columnWidths = new int[]{437, 0};
    gbl_trainingPanel.rowHeights = new int[]{0, 0, 0};
    gbl_trainingPanel.columnWeights = new double[]{0.0, Double.MIN_VALUE};
    gbl_trainingPanel.rowWeights = new double[]{0.0, 0.0, Double.MIN_VALUE};
    trainingPanel.setLayout(gbl_trainingPanel);
    
    trainingLoadPanel = new JPanel();
    GridBagConstraints gbc_trainingLoadPanel = new GridBagConstraints();
    gbc_trainingLoadPanel.anchor = GridBagConstraints.NORTHWEST;
    gbc_trainingLoadPanel.insets = new Insets(0, 0, 5, 0);
    gbc_trainingLoadPanel.gridx = 0;
    gbc_trainingLoadPanel.gridy = 0;
    trainingPanel.add(trainingLoadPanel, gbc_trainingLoadPanel);
    trainingLoadPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
    
    JButton trainingLoadButton = new JButton("Download");
    trainingLoadButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        trainingCaptcha = downloadCAPTCHA();
        if (trainingCaptcha != null) {
          trainingCaptchaImage.setIcon(new ImageIcon(trainingCaptcha));
          List<Image> glyphs = loadGlyphs(trainingCaptcha);
          for (int g = 0; g < 5; ++g) {
            trainingGlyphImage[g].setIcon(null);
            trainingGlyphField[g].setText("");
          }
          for (int g = 0; g < glyphs.size() && g < 5; ++g) {
            trainingGlyph[g] = glyphs.get(g);
            trainingGlyphImage[g].setIcon(new ImageIcon(trainingGlyph[g]));
          }
          trainingLoadPanel.invalidate();
          trainingSavePanel.invalidate();
        }
      }
    });
    trainingLoadPanel.add(trainingLoadButton);
    
    trainingCaptchaImage = new JLabel("");
    trainingLoadPanel.add(trainingCaptchaImage);
    
    trainingSavePanel = new JPanel();
    GridBagConstraints gbc_trainingSavePanel = new GridBagConstraints();
    gbc_trainingSavePanel.insets = new Insets(0, 5, 0, 0);
    gbc_trainingSavePanel.anchor = GridBagConstraints.NORTHWEST;
    gbc_trainingSavePanel.gridx = 0;
    gbc_trainingSavePanel.gridy = 1;
    trainingPanel.add(trainingSavePanel, gbc_trainingSavePanel);
    GridBagLayout gbl_trainingSavePanel = new GridBagLayout();
    gbl_trainingSavePanel.columnWidths = new int[]{25, 25, 25, 25, 25, 0, 0};
    gbl_trainingSavePanel.rowHeights = new int[]{25, 0, 0};
    gbl_trainingSavePanel.columnWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
    gbl_trainingSavePanel.rowWeights = new double[]{0.0, 0.0, Double.MIN_VALUE};
    trainingSavePanel.setLayout(gbl_trainingSavePanel);

    trainingGlyph = new Image[5];
    trainingGlyphImage = new JLabel[5];
    trainingGlyphField = new JTextField[5];
    
    trainingGlyphImage[0] = new JLabel("");
    GridBagConstraints gbc_glyphImage0 = new GridBagConstraints();
    gbc_glyphImage0.anchor = GridBagConstraints.NORTHWEST;
    gbc_glyphImage0.insets = new Insets(0, 0, 5, 5);
    gbc_glyphImage0.gridx = 0;
    gbc_glyphImage0.gridy = 0;
    trainingGlyphImage[0].setBorder(
        BorderFactory.createLineBorder(Color.GRAY, 2));
    trainingSavePanel.add(trainingGlyphImage[0], gbc_glyphImage0);
    
    trainingGlyphImage[1] = new JLabel("");
    GridBagConstraints gbc_glyphImage1 = new GridBagConstraints();
    gbc_glyphImage1.anchor = GridBagConstraints.NORTHWEST;
    gbc_glyphImage1.insets = new Insets(0, 0, 5, 5);
    gbc_glyphImage1.gridx = 1;
    gbc_glyphImage1.gridy = 0;
    trainingGlyphImage[1].setBorder(
        BorderFactory.createLineBorder(Color.GRAY, 2));
    trainingSavePanel.add(trainingGlyphImage[1], gbc_glyphImage1);
    
    trainingGlyphImage[2] = new JLabel("");
    GridBagConstraints gbc_glyphImage2 = new GridBagConstraints();
    gbc_glyphImage2.anchor = GridBagConstraints.NORTHWEST;
    gbc_glyphImage2.insets = new Insets(0, 0, 5, 5);
    gbc_glyphImage2.gridx = 2;
    gbc_glyphImage2.gridy = 0;
    trainingGlyphImage[2].setBorder(
        BorderFactory.createLineBorder(Color.GRAY, 2));
    trainingSavePanel.add(trainingGlyphImage[2], gbc_glyphImage2);
    
    trainingGlyphImage[3] = new JLabel("");
    GridBagConstraints gbc_glyphImage3 = new GridBagConstraints();
    gbc_glyphImage3.anchor = GridBagConstraints.NORTHWEST;
    gbc_glyphImage3.insets = new Insets(0, 0, 5, 5);
    gbc_glyphImage3.gridx = 3;
    gbc_glyphImage3.gridy = 0;
    trainingGlyphImage[3].setBorder(
        BorderFactory.createLineBorder(Color.GRAY, 2));
    trainingSavePanel.add(trainingGlyphImage[3], gbc_glyphImage3);
    
    trainingGlyphImage[4] = new JLabel("");
    GridBagConstraints gbc_glyphImage4 = new GridBagConstraints();
    gbc_glyphImage4.insets = new Insets(0, 0, 5, 5);
    gbc_glyphImage4.anchor = GridBagConstraints.NORTHWEST;
    gbc_glyphImage4.gridx = 4;
    gbc_glyphImage4.gridy = 0;
    trainingGlyphImage[4].setBorder(
        BorderFactory.createLineBorder(Color.GRAY, 2));
    trainingSavePanel.add(trainingGlyphImage[4], gbc_glyphImage4);
    
    JButton trainingSaveButton = new JButton("Train");
    trainingSaveButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        for (int g = 0; g < 5; ++g) {
          String s = trainingGlyphField[g].getText();
          if (s.length() == 1) {
            char c = Character.toUpperCase(s.charAt(0));
            net.sourceforge.javaocr.Image glyph = convertImage(trainingGlyph[g]);
            grayFilter.process(glyph);
            ThresholdFilter thresholdFilter = new ThresholdFilter(0, FG, BG);
            thresholdFilter.process(glyph);
            train(glyph, c);
          }
          trainingGlyphField[g].setText("");
        }
        
        for(Map.Entry<Character, Cluster> m : clusters.entrySet()) {
          System.out.println("****************************************");
          System.out.print(" character:  ");
//          int samples = ((EuclidianDistanceCluster) m.getValue()).getAmountSamples();
          int samples = ((SigmaWeightedEuclidianDistanceCluster) m.getValue()).getAmountSamples();
          for (int s = 0; s < samples; ++s) System.out.print(m.getKey());
          System.out.println();
          double[] c = m.getValue().center();
          for (int i = 0; i < c.length; ++i)
            System.out.println(" centroid[" + i + "]: " + c[i]);
        }
        
        System.out.println("****************************************");
        System.out.println("TOTAL CLUSTERS: " + clusters.size());
      }
    });
    GridBagConstraints gbc_trainingSaveButton = new GridBagConstraints();
    gbc_trainingSaveButton.gridheight = 2;
    gbc_trainingSaveButton.insets = new Insets(0, 0, 5, 0);
    gbc_trainingSaveButton.gridx = 5;
    gbc_trainingSaveButton.gridy = 0;
    trainingSavePanel.add(trainingSaveButton, gbc_trainingSaveButton);
    
    trainingGlyphField[0] = new JTextField();
    GridBagConstraints gbc_glyphField0 = new GridBagConstraints();
    gbc_glyphField0.fill = GridBagConstraints.HORIZONTAL;
    gbc_glyphField0.anchor = GridBagConstraints.NORTHWEST;
    gbc_glyphField0.insets = new Insets(0, 0, 0, 5);
    gbc_glyphField0.gridx = 0;
    gbc_glyphField0.gridy = 1;
    trainingSavePanel.add(trainingGlyphField[0], gbc_glyphField0);
    trainingGlyphField[0].setColumns(2);
    
    trainingGlyphField[1] = new JTextField();
    GridBagConstraints gbc_glyphField1 = new GridBagConstraints();
    gbc_glyphField1.fill = GridBagConstraints.HORIZONTAL;
    gbc_glyphField1.anchor = GridBagConstraints.NORTHWEST;
    gbc_glyphField1.insets = new Insets(0, 0, 0, 5);
    gbc_glyphField1.gridx = 1;
    gbc_glyphField1.gridy = 1;
    trainingSavePanel.add(trainingGlyphField[1], gbc_glyphField1);
    trainingGlyphField[1].setColumns(2);
    
    trainingGlyphField[2] = new JTextField();
    GridBagConstraints gbc_glyphField2 = new GridBagConstraints();
    gbc_glyphField2.fill = GridBagConstraints.HORIZONTAL;
    gbc_glyphField2.anchor = GridBagConstraints.NORTHWEST;
    gbc_glyphField2.insets = new Insets(0, 0, 0, 5);
    gbc_glyphField2.gridx = 2;
    gbc_glyphField2.gridy = 1;
    trainingSavePanel.add(trainingGlyphField[2], gbc_glyphField2);
    trainingGlyphField[2].setColumns(2);
    
    trainingGlyphField[3] = new JTextField();
    GridBagConstraints gbc_glyphField3 = new GridBagConstraints();
    gbc_glyphField3.fill = GridBagConstraints.HORIZONTAL;
    gbc_glyphField3.anchor = GridBagConstraints.NORTHWEST;
    gbc_glyphField3.insets = new Insets(0, 0, 0, 5);
    gbc_glyphField3.gridx = 3;
    gbc_glyphField3.gridy = 1;
    trainingSavePanel.add(trainingGlyphField[3], gbc_glyphField3);
    trainingGlyphField[3].setColumns(2);
    
    trainingGlyphField[4] = new JTextField();
    GridBagConstraints gbc_glyphField4 = new GridBagConstraints();
    gbc_glyphField4.insets = new Insets(0, 0, 0, 5);
    gbc_glyphField4.fill = GridBagConstraints.HORIZONTAL;
    gbc_glyphField4.anchor = GridBagConstraints.NORTHWEST;
    gbc_glyphField4.gridx = 4;
    gbc_glyphField4.gridy = 1;
    trainingSavePanel.add(trainingGlyphField[4], gbc_glyphField4);
    trainingGlyphField[4].setColumns(2);
    
    JPanel testingPanel = new JPanel();
    tabbedPane.addTab("Testing", null, testingPanel, null);
    GridBagLayout gbl_testingPanel = new GridBagLayout();
    gbl_testingPanel.columnWidths = new int[]{437, 0};
    gbl_testingPanel.rowHeights = new int[]{0, 0, 0, 0};
    gbl_testingPanel.columnWeights = new double[]{1.0, Double.MIN_VALUE};
    gbl_testingPanel.rowWeights = new double[]{0.0, 0.0, 1.0, Double.MIN_VALUE};
    testingPanel.setLayout(gbl_testingPanel);
    
    testingLoadPanel = new JPanel();
    GridBagConstraints gbc_testingLoadPanel = new GridBagConstraints();
    gbc_testingLoadPanel.anchor = GridBagConstraints.NORTHWEST;
    gbc_testingLoadPanel.insets = new Insets(0, 0, 5, 0);
    gbc_testingLoadPanel.gridx = 0;
    gbc_testingLoadPanel.gridy = 0;
    testingPanel.add(testingLoadPanel, gbc_testingLoadPanel);
    testingLoadPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
    
    JButton testingLoadButton = new JButton("Download");
    testingLoadButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        testingCaptcha = downloadCAPTCHA();
        if (testingCaptcha != null) {
          testingCaptchaImage.setIcon(new ImageIcon(testingCaptcha));
          List<Image> glyphs = loadGlyphs(testingCaptcha);
          for (int g = 0; g < 5; ++g) {
            testingGlyphImage[g].setIcon(null);
            testingGlyphField[g].setText("");
          }
          for (int g = 0; g < glyphs.size() && g < 5; ++g) {
            testingGlyph[g] = glyphs.get(g);
            testingGlyphImage[g].setIcon(new ImageIcon(testingGlyph[g]));
          }
          testingLoadPanel.invalidate();
          testingSavePanel.invalidate();
        }
      }
    });
    testingLoadPanel.add(testingLoadButton);
    
    testingCaptchaImage = new JLabel("");
    testingLoadPanel.add(testingCaptchaImage);
    
    testingSavePanel = new JPanel();
    GridBagConstraints gbc_testingSavePanel = new GridBagConstraints();
    gbc_testingSavePanel.insets = new Insets(0, 5, 5, 0);
    gbc_testingSavePanel.anchor = GridBagConstraints.NORTHWEST;
    gbc_testingSavePanel.gridx = 0;
    gbc_testingSavePanel.gridy = 1;
    testingPanel.add(testingSavePanel, gbc_testingSavePanel);
    GridBagLayout gbl_testingSavePanel = new GridBagLayout();
    gbl_testingSavePanel.columnWidths = new int[]{25, 25, 25, 25, 25, 0, 0};
    gbl_testingSavePanel.rowHeights = new int[]{25, 0, 0};
    gbl_testingSavePanel.columnWeights = new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 0.0, Double.MIN_VALUE};
    gbl_testingSavePanel.rowWeights = new double[]{0.0, 0.0, Double.MIN_VALUE};
    testingSavePanel.setLayout(gbl_testingSavePanel);

    testingGlyph = new Image[5];
    testingGlyphImage = new JLabel[5];
    testingGlyphField = new JTextField[5];
    
    testingGlyphImage[0] = new JLabel("");
    GridBagConstraints gbc_testingGlyphImage0 = new GridBagConstraints();
    gbc_testingGlyphImage0.anchor = GridBagConstraints.NORTHWEST;
    gbc_testingGlyphImage0.insets = new Insets(0, 0, 5, 5);
    gbc_testingGlyphImage0.gridx = 0;
    gbc_testingGlyphImage0.gridy = 0;
    testingGlyphImage[0].setBorder(
        BorderFactory.createLineBorder(Color.GRAY, 2));
    testingSavePanel.add(testingGlyphImage[0], gbc_testingGlyphImage0);
    
    testingGlyphImage[1] = new JLabel("");
    GridBagConstraints gbc_testingGlyphImage1 = new GridBagConstraints();
    gbc_testingGlyphImage1.anchor = GridBagConstraints.NORTHWEST;
    gbc_testingGlyphImage1.insets = new Insets(0, 0, 5, 5);
    gbc_testingGlyphImage1.gridx = 1;
    gbc_testingGlyphImage1.gridy = 0;
    testingGlyphImage[1].setBorder(
        BorderFactory.createLineBorder(Color.GRAY, 2));
    testingSavePanel.add(testingGlyphImage[1], gbc_testingGlyphImage1);
    
    testingGlyphImage[2] = new JLabel("");
    GridBagConstraints gbc_testingGlyphImage2 = new GridBagConstraints();
    gbc_testingGlyphImage2.anchor = GridBagConstraints.NORTHWEST;
    gbc_testingGlyphImage2.insets = new Insets(0, 0, 5, 5);
    gbc_testingGlyphImage2.gridx = 2;
    gbc_testingGlyphImage2.gridy = 0;
    testingGlyphImage[2].setBorder(
        BorderFactory.createLineBorder(Color.GRAY, 2));
    testingSavePanel.add(testingGlyphImage[2], gbc_testingGlyphImage2);
    
    testingGlyphImage[3] = new JLabel("");
    GridBagConstraints gbc_testingGlyphImage3 = new GridBagConstraints();
    gbc_testingGlyphImage3.anchor = GridBagConstraints.NORTHWEST;
    gbc_testingGlyphImage3.insets = new Insets(0, 0, 5, 5);
    gbc_testingGlyphImage3.gridx = 3;
    gbc_testingGlyphImage3.gridy = 0;
    testingGlyphImage[3].setBorder(
        BorderFactory.createLineBorder(Color.GRAY, 2));
    testingSavePanel.add(testingGlyphImage[3], gbc_testingGlyphImage3);
    
    testingGlyphImage[4] = new JLabel("");
    GridBagConstraints gbc_testingGlyphImage4 = new GridBagConstraints();
    gbc_testingGlyphImage4.insets = new Insets(0, 0, 5, 5);
    gbc_testingGlyphImage4.anchor = GridBagConstraints.NORTHWEST;
    gbc_testingGlyphImage4.gridx = 4;
    gbc_testingGlyphImage4.gridy = 0;
    testingGlyphImage[4].setBorder(
        BorderFactory.createLineBorder(Color.GRAY, 2));
    testingSavePanel.add(testingGlyphImage[4], gbc_testingGlyphImage4);
    
    JButton testingSaveButton = new JButton("Save and test");
    testingSaveButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        String text = "";
        for (int g = 0; g < 5; ++g) {
          String s = testingGlyphField[g].getText();
          if (s.length() == 1) text += s.toUpperCase();
          trainingGlyphField[g].setText("");
        }
        
        if (text.length() != 5) return;
        
        try {
          File file = new File(tempFolder.getAbsolutePath() + "/captcha_"+text+".png");
          ImageIO.write(testingCaptcha, "png", file);
        } catch (IOException x) {
          x.printStackTrace();
        }
        
        testingViewArea.setText(loadStatistics());
      }
    });
    GridBagConstraints gbc_testingSaveButton = new GridBagConstraints();
    gbc_testingSaveButton.gridheight = 2;
    gbc_testingSaveButton.insets = new Insets(0, 0, 5, 0);
    gbc_testingSaveButton.gridx = 5;
    gbc_testingSaveButton.gridy = 0;
    testingSavePanel.add(testingSaveButton, gbc_testingSaveButton);
    
    testingGlyphField[0] = new JTextField();
    GridBagConstraints gbc_testingGlyphField0 = new GridBagConstraints();
    gbc_testingGlyphField0.fill = GridBagConstraints.HORIZONTAL;
    gbc_testingGlyphField0.anchor = GridBagConstraints.NORTHWEST;
    gbc_testingGlyphField0.insets = new Insets(0, 0, 0, 5);
    gbc_testingGlyphField0.gridx = 0;
    gbc_testingGlyphField0.gridy = 1;
    testingSavePanel.add(testingGlyphField[0], gbc_testingGlyphField0);
    testingGlyphField[0].setColumns(2);
    
    testingGlyphField[1] = new JTextField();
    GridBagConstraints gbc_testingGlyphField1 = new GridBagConstraints();
    gbc_testingGlyphField1.fill = GridBagConstraints.HORIZONTAL;
    gbc_testingGlyphField1.anchor = GridBagConstraints.NORTHWEST;
    gbc_testingGlyphField1.insets = new Insets(0, 0, 0, 5);
    gbc_testingGlyphField1.gridx = 1;
    gbc_testingGlyphField1.gridy = 1;
    testingSavePanel.add(testingGlyphField[1], gbc_testingGlyphField1);
    testingGlyphField[1].setColumns(2);
    
    testingGlyphField[2] = new JTextField();
    GridBagConstraints gbc_testingGlyphField2 = new GridBagConstraints();
    gbc_testingGlyphField2.fill = GridBagConstraints.HORIZONTAL;
    gbc_testingGlyphField2.anchor = GridBagConstraints.NORTHWEST;
    gbc_testingGlyphField2.insets = new Insets(0, 0, 0, 5);
    gbc_testingGlyphField2.gridx = 2;
    gbc_testingGlyphField2.gridy = 1;
    testingSavePanel.add(testingGlyphField[2], gbc_testingGlyphField2);
    testingGlyphField[2].setColumns(2);
    
    testingGlyphField[3] = new JTextField();
    GridBagConstraints gbc_testingGlyphField3 = new GridBagConstraints();
    gbc_testingGlyphField3.fill = GridBagConstraints.HORIZONTAL;
    gbc_testingGlyphField3.anchor = GridBagConstraints.NORTHWEST;
    gbc_testingGlyphField3.insets = new Insets(0, 0, 0, 5);
    gbc_testingGlyphField3.gridx = 3;
    gbc_testingGlyphField3.gridy = 1;
    testingSavePanel.add(testingGlyphField[3], gbc_testingGlyphField3);
    testingGlyphField[3].setColumns(2);
    
    testingGlyphField[4] = new JTextField();
    GridBagConstraints gbc_testingGlyphField4 = new GridBagConstraints();
    gbc_testingGlyphField4.insets = new Insets(0, 0, 0, 5);
    gbc_testingGlyphField4.fill = GridBagConstraints.HORIZONTAL;
    gbc_testingGlyphField4.anchor = GridBagConstraints.NORTHWEST;
    gbc_testingGlyphField4.gridx = 4;
    gbc_testingGlyphField4.gridy = 1;
    testingSavePanel.add(testingGlyphField[4], gbc_testingGlyphField4);
    
    JPanel testingViewPanel = new JPanel();
    GridBagConstraints gbc_testingViewPanel = new GridBagConstraints();
    gbc_testingViewPanel.fill = GridBagConstraints.HORIZONTAL;
    gbc_testingViewPanel.anchor = GridBagConstraints.NORTHWEST;
    gbc_testingViewPanel.gridx = 0;
    gbc_testingViewPanel.gridy = 2;
    testingPanel.add(testingViewPanel, gbc_testingViewPanel);
    GridBagLayout gbl_testingViewPanel = new GridBagLayout();
    gbl_testingViewPanel.columnWidths = new int[]{330, 0};
    gbl_testingViewPanel.rowHeights = new int[]{75, 0};
    gbl_testingViewPanel.columnWeights = new double[]{0.0, Double.MIN_VALUE};
    gbl_testingViewPanel.rowWeights = new double[]{0.0, Double.MIN_VALUE};
    testingViewPanel.setLayout(gbl_testingViewPanel);
    
    testingViewArea = new JTextArea();
    testingViewArea.setRows(5);
    testingViewArea.setColumns(30);
    GridBagConstraints gbc_testingViewArea = new GridBagConstraints();
    gbc_testingViewArea.fill = GridBagConstraints.BOTH;
    gbc_testingViewArea.anchor = GridBagConstraints.NORTHWEST;
    gbc_testingViewArea.gridx = 0;
    gbc_testingViewArea.gridy = 0;
    testingViewPanel.add(testingViewArea, gbc_testingViewArea);
    testingGlyphField[4].setColumns(2);
    
    JPanel outputPanel = new JPanel();
    FlowLayout flowLayout2 = (FlowLayout) outputPanel.getLayout();
    flowLayout2.setAlignment(FlowLayout.LEFT);
    tabbedPane.addTab("Output", null, outputPanel, null);
    
    JPanel outputFormPanel = new JPanel();
    outputPanel.add(outputFormPanel);
    GridBagLayout gbl_outputFormPanel = new GridBagLayout();
    gbl_outputFormPanel.columnWidths = new int[]{150, 250};
    gbl_outputFormPanel.rowHeights = new int[]{0};
    gbl_outputFormPanel.columnWeights = new double[]{0.0, 1.0};
    gbl_outputFormPanel.rowWeights = new double[]{0.0};
    outputFormPanel.setLayout(gbl_outputFormPanel);
    
    JLabel javaLabel = new JLabel("Output .java file");
    GridBagConstraints gbc_javaLabel = new GridBagConstraints();
    gbc_javaLabel.anchor = GridBagConstraints.WEST;
    gbc_javaLabel.fill = GridBagConstraints.VERTICAL;
    gbc_javaLabel.insets = new Insets(0, 0, 5, 5);
    gbc_javaLabel.gridx = 0;
    gbc_javaLabel.gridy = 0;
    outputFormPanel.add(javaLabel, gbc_javaLabel);
    
    javaField = new JTextField();
    javaField.addFocusListener(new FocusListener() {
      public void focusLost(FocusEvent e) {
        javaField.removeFocusListener(this);
      }
      public void focusGained(FocusEvent e) {
        JFileChooser chooser = new JFileChooser(outFolderName);
        chooser.setSelectedFile(new File(outFileName));
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showDialog(frame, "Save .java file") == JFileChooser.APPROVE_OPTION) {
          javaField.setText(chooser.getSelectedFile().getAbsolutePath());
          
          try {
            PrintWriter output = new PrintWriter(
                new FileWriter(chooser.getSelectedFile().getAbsolutePath()));
            output.println("package com.googlecode.libautocaptcha.decoder.data;");
            output.println("import net.sourceforge.javaocr.plugin.cluster.Cluster;");
            output.println("import net.sourceforge.javaocr.plugin.cluster.MahalanobisDistanceCluster;");
            output.println("public class VodafoneItalyData {");
            
            output.println("  public static final int[] "+BACKGROUND_FIELD+" = ");
            output.print("    new int[] { ");
            for (int y = 0; y < HEIGHT; ++y)
              for (int x = 0; x < WIDTH; ++x)
                output.print(background.get(x, y)+", ");
            output.println("};");
            
            output.println("  public static final char[] "+CHARACTERS_FIELD+" = ");
            output.print("    new char[] { ");
            for (Character c : clusters.keySet())
              output.print("'"+c+"', ");
            output.println("};");
                
            output.println("  public static final Cluster[] "+CLUSTERS_FIELD+" = ");
            output.print("    new MahalanobisDistanceCluster[] { ");
            for (Cluster c : clusters.values()) {
              output.print("new MahalanobisDistanceCluster(new double[] { ");
              double[] mx = ((MahalanobisDistanceCluster) c).getMx();
              for (double i : mx) output.print(i+", ");
              output.print("}, new double[][] { ");
              double[][] invcov = ((MahalanobisDistanceCluster) c).getInvcov();
              for (double[] row : invcov) { 
                output.print("new double[] { ");
                for (double i : row) output.print(i+", ");
                output.print("}, ");
              }
              output.print("}), ");
            }
            output.println("};");
                
            output.println("}");
            output.close();
          } catch (IOException x) {
            x.printStackTrace();
          }
        }
      }
    });
    GridBagConstraints gbc_javaField = new GridBagConstraints();
    gbc_javaField.fill = GridBagConstraints.BOTH;
    gbc_javaField.insets = new Insets(0, 0, 5, 0);
    gbc_javaField.gridx = 1;
    gbc_javaField.gridy = 0;
    outputFormPanel.add(javaField, gbc_javaField);
    javaField.setColumns(10);
    
    JPanel statusPanel = new JPanel();
    frame.getContentPane().add(statusPanel, BorderLayout.SOUTH);
    GridBagLayout gbl_statusPanel = new GridBagLayout();
    gbl_statusPanel.columnWidths = new int[]{150, 0, 0};
    gbl_statusPanel.rowHeights = new int[]{14, 0};
    gbl_statusPanel.columnWeights = new double[]{0.0, 1.0, Double.MIN_VALUE};
    gbl_statusPanel.rowWeights = new double[]{0.0, Double.MIN_VALUE};
    statusPanel.setLayout(gbl_statusPanel);
    
    statusBar = new JProgressBar();
    GridBagConstraints gbc_statusBar = new GridBagConstraints();
    gbc_statusBar.insets = new Insets(0, 0, 0, 5);
    gbc_statusBar.gridx = 0;
    gbc_statusBar.gridy = 0;
    statusPanel.add(statusBar, gbc_statusBar);
    
    statusLabel = new JLabel("");
    GridBagConstraints gbc_statusLabel = new GridBagConstraints();
    gbc_statusLabel.fill = GridBagConstraints.HORIZONTAL;
    gbc_statusLabel.gridx = 1;
    gbc_statusLabel.gridy = 0;
    statusPanel.add(statusLabel, gbc_statusLabel);
  }

  private BufferedImage downloadCAPTCHA() {
    try {
      
      if (!loggedIn) {
        HttpGet homeRequest = new HttpGet("http://www.vodafone.it/190/trilogy/jsp/home.do");
        System.out.println(homeRequest.getRequestLine());
        HttpResponse homeResponse = httpClient.execute(homeRequest);
        System.out.println(homeResponse.getStatusLine());
        EntityUtils.consume(homeResponse.getEntity());
        
        HttpPost loginRequest = new HttpPost("https://www.vodafone.it/190/trilogy/jsp/login.do");
        List<NameValuePair> requestData = new ArrayList<NameValuePair>();
        requestData.add(new BasicNameValuePair("username", usernameField.getText()));
        requestData.add(new BasicNameValuePair("password", String.valueOf(passwordField.getPassword())));
        loginRequest.setEntity(new UrlEncodedFormEntity(requestData, HTTP.UTF_8));
        System.out.println(loginRequest.getRequestLine());
        HttpResponse loginResponse = httpClient.execute(loginRequest);
        System.out.println(loginResponse.getStatusLine());
        EntityUtils.consume(loginResponse.getEntity());
        
        HttpGet dispatcherRequest1 = new HttpGet("http://www.vodafone.it/190/trilogy/jsp/dispatcher.do?ty_key=fdt_invia_sms&tk=9616,2");
        System.out.println(dispatcherRequest1.getRequestLine());
        HttpResponse dispatcherResponse1 = httpClient.execute(dispatcherRequest1);
        System.out.println(dispatcherResponse1.getStatusLine());
        EntityUtils.consume(dispatcherResponse1.getEntity());
        
        HttpGet dispatcherRequest2 = new HttpGet("http://www.vodafone.it/190/trilogy/jsp/dispatcher.do?ty_key=fsms_hp&ipage=next");
        System.out.println(dispatcherRequest2.getRequestLine());
        HttpResponse dispatcherResponse2 = httpClient.execute(dispatcherRequest2);
        System.out.println(dispatcherResponse2.getStatusLine());
        EntityUtils.consume(dispatcherResponse2.getEntity());
        
        loggedIn = true;
      }
      
      HttpPost prepareRequest = new HttpPost("http://www.vodafone.it/190/fsms/prepare.do");
      List<NameValuePair> prepareRequestData = new ArrayList<NameValuePair>();
      prepareRequestData.add(new BasicNameValuePair("pageTypeId", "9604"));
      prepareRequestData.add(new BasicNameValuePair("programId", "10384"));
      prepareRequestData.add(new BasicNameValuePair("chanelId", "-18126"));
      prepareRequestData.add(new BasicNameValuePair("receiverNumber", receiverField.getText()));
      prepareRequestData.add(new BasicNameValuePair("message", "message"));
      prepareRequest.setEntity(new UrlEncodedFormEntity(prepareRequestData, HTTP.UTF_8));
      System.out.println(prepareRequest.getRequestLine());
      HttpResponse prepareResponse = httpClient.execute(prepareRequest);
      System.out.println(prepareResponse.getStatusLine());
      EntityUtils.consume(prepareResponse.getEntity());
      
      HttpGet captchaRequest = new HttpGet("http://www.vodafone.it/190/fsms/generateimg.do");
      System.out.println(captchaRequest.getRequestLine());
      HttpResponse captchaResponse = httpClient.execute(captchaRequest);
      System.out.println(captchaResponse.getStatusLine());
      BufferedImage captcha = ImageIO.read(captchaResponse.getEntity().getContent());
      EntityUtils.consume(captchaResponse.getEntity());
  
      HttpPost sendRequest = new HttpPost("http://www.vodafone.it/190/fsms/send.do");
      List<NameValuePair> sendRequestData = new ArrayList<NameValuePair>();
      sendRequestData.add(new BasicNameValuePair("verifyCode", ""));
      sendRequestData.add(new BasicNameValuePair("pageTypeId", "9604"));
      sendRequestData.add(new BasicNameValuePair("programId", "10384"));
      sendRequestData.add(new BasicNameValuePair("chanelId", "-18126"));
      sendRequestData.add(new BasicNameValuePair("receiverNumber", receiverField.getText()));
      sendRequestData.add(new BasicNameValuePair("message", "message"));
      sendRequest.setEntity(new UrlEncodedFormEntity(sendRequestData, HTTP.UTF_8));
      System.out.println(sendRequest.getRequestLine());
      HttpResponse sendResponse = httpClient.execute(sendRequest);
      System.out.println(sendResponse.getStatusLine());
      EntityUtils.consume(sendResponse.getEntity());
      
      return captcha;
      
    } catch (Exception e) {
      
      e.printStackTrace();
      loggedIn = false;
      initHttpClient();
      return null;
      
    }
  }
  
  private Image loadBackground() {
    File[] files = tempFolder.listFiles();
    if (files == null) return null;
    List<net.sourceforge.javaocr.Image> images = 
        new ArrayList<net.sourceforge.javaocr.Image>();
    
    for (File file : files) {
      if (file.isFile() && file.getName().startsWith("background_")) {
        try {
          images.add(convertImage(ImageIO.read(file)));
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }

    return convertImage(extractBackground(images));
  }

  private String loadStatistics() {
    File[] files = tempFolder.listFiles();
    if (files == null) return null;

    int totalCAPTCHAs = 0;
    int totalGlyphs = 0;
    int goodCAPTCHAs = 0;
    int goodGlyphs = 0;
    
    for (File file : files) {
      String fn = file.getName();
      if (file.isFile() && fn.startsWith("captcha_")) {
        try {
          totalCAPTCHAs += 1;
          totalGlyphs += 5;
          String truth = fn.substring(fn.lastIndexOf('_')+1, fn.lastIndexOf('.'));
          System.out.println("CAPTCHA " + truth + " matches: ");
          String result = decode(convertImage(ImageIO.read(file)));
          if (result.equals(truth)) {
            goodCAPTCHAs += 1;
            goodGlyphs += 5;
          } else {
            for (int c = 0; c < result.length(); ++c) {
              if (result.charAt(c) == truth.charAt(c))
                goodGlyphs += 1;
            }
          }
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    
    int percCAPTCHAs = (int) (100.0 * goodCAPTCHAs / totalCAPTCHAs);
    int percGlyphs = (int) (100.0 * goodGlyphs / totalGlyphs);
    
    String statistics = 
        "Correctly identified CAPTCHAs: " + 
        goodCAPTCHAs + " / " + totalCAPTCHAs +
        " (" + percCAPTCHAs + " %)\n" +
        "Correctly identified glyphs: " + 
        goodGlyphs + " / " + totalGlyphs +
        " (" + percGlyphs + " %)\n";
    return statistics;
  }
  
  private List<Image> loadGlyphs(Image image) {
    net.sourceforge.javaocr.Image captcha = convertImage(image);
    grayFilter.process(captcha);
    net.sourceforge.javaocr.Image foreground = extractForeground(captcha);
    List<net.sourceforge.javaocr.Image> glyphs = slice(foreground);
    List<Image> images = new ArrayList<Image>();
    ThresholdFilter thresholdFilter = new ThresholdFilter(0, 255, 0);
    for (net.sourceforge.javaocr.Image glyph : glyphs) {
      thresholdFilter.process(glyph);
      rgbaFilter.process(glyph);
      images.add(convertImage(glyph));
    }
    return images;
  }
  
  private net.sourceforge.javaocr.Image convertImage(java.awt.Image image) {
    return new AwtImage(image);
  }

  private java.awt.Image convertImage(net.sourceforge.javaocr.Image image) {
    final int W = image.getWidth();
    final int H = image.getHeight();
    BufferedImage result = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
    AwtImage temp = new AwtImage(result);
    image.copy(temp);
    result.setRGB(0, 0, W, H, temp.pixels, 0, W);
    return result;
  }
  
}
