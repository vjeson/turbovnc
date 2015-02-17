/* Copyright (C) 2002-2005 RealVNC Ltd.  All Rights Reserved.
 * Copyright (C) 2011-2013 Brian P. Hinz
 * Copyright (C) 2012-2013, 2015 D. R. Commander.  All Rights Reserved.
 *
 * This is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this software; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301,
 * USA.
 */

package com.turbovnc.vncviewer;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.lang.reflect.*;
import java.util.Timer;
import java.util.TimerTask;

import com.turbovnc.rdr.*;
import com.turbovnc.rfb.*;
import com.turbovnc.rfb.Cursor;

public class Viewport extends JFrame {

  public Viewport(CConn cc_) {
    cc = cc_;
    updateTitle();
    setFocusable(false);
    setFocusTraversalKeysEnabled(false);
    setIconImage(VncViewer.frameImage);
    UIManager.getDefaults().put("ScrollPane.ancestorInputMap",
      new UIDefaults.LazyInputMap(new Object[]{}));
    sp = new JScrollPane();
    sp.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
    sp.getViewport().setBackground(Color.BLACK);
    InputMap im = sp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
    int ctrlAltShiftMask = Event.SHIFT_MASK | Event.CTRL_MASK | Event.ALT_MASK;
    if (im != null) {
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, ctrlAltShiftMask),
             "unitScrollUp");
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, ctrlAltShiftMask),
             "unitScrollDown");
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, ctrlAltShiftMask),
             "unitScrollLeft");
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, ctrlAltShiftMask),
             "unitScrollRight");
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, ctrlAltShiftMask),
             "scrollUp");
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, ctrlAltShiftMask),
             "scrollDown");
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_HOME, ctrlAltShiftMask),
             "scrollLeft");
      im.put(KeyStroke.getKeyStroke(KeyEvent.VK_END, ctrlAltShiftMask),
             "scrollRight");
    }
    tb = new Toolbar(cc);
    add(tb, BorderLayout.PAGE_START);
    getContentPane().add(sp);
    if (VncViewer.os.startsWith("mac os x")) {
      macMenu = new MacMenuBar(cc);
      setJMenuBar(macMenu);
      if (!VncViewer.noLionFS.getValue())
        enableLionFS();
    }
    // NOTE: If Lion FS mode is enabled, then the viewport is only created once
    // as a non-full-screen viewport, so we tell showToolbar() to ignore the
    // full-screen state.
    showToolbar(cc.showToolbar, canDoLionFS);
    addWindowFocusListener(new WindowAdapter() {
      public void windowGainedFocus(WindowEvent e) {
        sp.getViewport().getView().requestFocusInWindow();
      }
    });
    addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        cc.close();
      }
    });
    addComponentListener(new ComponentAdapter() {
      public void componentResized(ComponentEvent e) {
        if (cc.opts.scalingFactor == Options.SCALE_AUTO ||
            cc.opts.scalingFactor == Options.SCALE_FIXEDRATIO) {
          if ((sp.getSize().width != cc.desktop.scaledWidth) ||
              (sp.getSize().height != cc.desktop.scaledHeight)) {
            cc.desktop.setScaledSize();
            sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            sp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
            sp.validate();
            if (getExtendedState() != JFrame.MAXIMIZED_BOTH &&
                !cc.opts.fullScreen) {
              sp.setSize(new Dimension(cc.desktop.scaledWidth,
                                       cc.desktop.scaledHeight));
              int w = cc.desktop.scaledWidth + getInsets().left +
                      getInsets().right;
              int h = cc.desktop.scaledHeight + getInsets().top +
                      getInsets().bottom;
              if (tb.isVisible())
                h += tb.getHeight();
              if (cc.opts.scalingFactor == Options.SCALE_FIXEDRATIO)
                setSize(w, h);
            }
          }
        } else if (cc.opts.desktopSize == Options.SIZE_AUTO &&
                   !cc.pendingServerResize) {
          Dimension availableSize = cc.viewport.getAvailableSize();
          if (availableSize.width < 1 || availableSize.height < 1)
            // Viewport isn't fully baked yet
            return;
          if ((availableSize.width != cc.desktop.scaledWidth) ||
              (availableSize.height != cc.desktop.scaledHeight)) {
            cc.desktop.setScaledSize();
            sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            sp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
            sp.validate();
            if (timer != null)
              timer.cancel();
            timer = new Timer();
            timer.schedule(new TimerTask() {
              public void run() {
                Dimension availableSize = cc.viewport.getAvailableSize();
                cc.sendDesktopSize(availableSize.width, availableSize.height);
                cc.pendingClientResize = true;
              }
            }, 500);
          }
        } else {
          sp.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
          sp.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
          sp.validate();
        }
        if (cc.desktop.cursor != null) {
          Cursor cursor = cc.desktop.cursor;
          cc.setCursor(cursor.width(), cursor.height(), cursor.hotspot,
                       (int[])cursor.data, cursor.mask);
        }
        if ((sp.getSize().width > cc.desktop.scaledWidth) ||
            (sp.getSize().height > cc.desktop.scaledHeight)) {
          dx = (sp.getSize().width <= cc.desktop.scaledWidth) ? 0 :
            (int)Math.floor((sp.getSize().width - cc.desktop.scaledWidth) / 2);
          dy = (sp.getSize().height <= cc.desktop.scaledHeight) ? 0 :
            (int)Math.floor((sp.getSize().height - cc.desktop.scaledHeight) / 2);
        } else {
          dx = dy = 0;
        }
        repaint();
        cc.pendingServerResize = false;
      }
    });
  }

  public Dimension getAvailableSize() {
    Dimension vpSize = getSize();
    Insets vpInsets = getInsets();
    int availableWidth = vpSize.width - vpInsets.left - vpInsets.right;
    int availableHeight = vpSize.height - vpInsets.top - vpInsets.bottom;
    if (tb.isVisible())
      availableHeight -= tb.getHeight();
    if (availableWidth < 0)
      availableWidth = 0;
    if (availableHeight < 0)
      availableHeight = 0;
    return new Dimension(availableWidth, availableHeight);
  }

  public Dimension getBorderSize() {
    Dimension vpSize = getSize();
    boolean tempVisible = false;
    if (vpSize.width == 0 || vpSize.height == 0) {
      if (!isVisible()) {
        tempVisible = true;
        setVisible(true);
      }
    }
    Insets vpInsets = getInsets();
    if (tb.isVisible())
      vpInsets.top += tb.getHeight();
    Dimension borderSize = new Dimension(vpInsets.left + vpInsets.right,
                                         vpInsets.top + vpInsets.bottom);
    if (tempVisible)
      setVisible(false);
    return borderSize;
  }

  boolean lionFSSupported() { return canDoLionFS; }

  class MyInvocationHandler implements InvocationHandler {
    MyInvocationHandler(CConn cc_) { cc = cc_; }

    public Object invoke(Object proxy, Method method, Object[] args) {
      if (method.getName().equals("windowEnteringFullScreen")) {
        cc.opts.fullScreen = true;
        cc.menu.fullScreen.setSelected(cc.opts.fullScreen);
        updateMacMenuFS();
        showToolbar(cc.showToolbar);
      } else if (method.getName().equals("windowExitingFullScreen")) {
        cc.opts.fullScreen = false;
        cc.menu.fullScreen.setSelected(cc.opts.fullScreen);
        updateMacMenuFS();
        showToolbar(cc.showToolbar);
      } else if (method.getName().equals("windowEnteredFullScreen")) {
        Rectangle span = cc.getSpannedSize(true);
        setGeometry(span.x, span.y, span.width, span.height, false);
      }
      return null;
    }

    CConn cc;
  }

  void enableLionFS() {
    try {
      String version = System.getProperty("os.version");
      String[] tokens = version.split("\\.");
      int major = Integer.parseInt(tokens[0]), minor = 0;
      if (tokens.length > 1)
        minor = Integer.parseInt(tokens[1]);
      if (major < 10 || (major == 10 && minor < 7))
        throw new Exception("Operating system version is " + version);

      Class fsuClass = Class.forName("com.apple.eawt.FullScreenUtilities");
      Class argClasses[] = new Class[]{Window.class, Boolean.TYPE};
      Method setWindowCanFullScreen =
        fsuClass.getMethod("setWindowCanFullScreen", argClasses);
      setWindowCanFullScreen.invoke(fsuClass, this, true);

      Class fsListenerClass =
        Class.forName("com.apple.eawt.FullScreenListener");
      InvocationHandler fsHandler = new MyInvocationHandler(cc);
      Object proxy = Proxy.newProxyInstance(fsListenerClass.getClassLoader(),
                                            new Class[]{fsListenerClass},
                                            fsHandler);
      argClasses = new Class[]{Window.class, fsListenerClass};
      Method addFullScreenListenerTo =
        fsuClass.getMethod("addFullScreenListenerTo", argClasses);
      addFullScreenListenerTo.invoke(fsuClass, this, proxy);

      canDoLionFS = true;
    } catch (Exception e) {
      vlog.debug("Could not enable OS X 10.7+ full-screen mode:");
      vlog.debug("  " + e.toString());
    }
  }

  public void toggleLionFS() {
    try {
      Class appClass = Class.forName("com.apple.eawt.Application");
      Method getApplication = appClass.getMethod("getApplication",
                                                 (Class[])null);
      Object app = getApplication.invoke(appClass);
      Method requestToggleFullScreen =
        appClass.getMethod("requestToggleFullScreen", Window.class);
      requestToggleFullScreen.invoke(app, this);
    } catch (Exception e) {
      vlog.debug("Could not toggle OS X 10.7+ full-screen mode:");
      vlog.debug("  " + e.toString());
    }
  }

  public void updateMacMenuFS() {
    if (macMenu != null)
      macMenu.updateFullScreen();
  }

  public void updateMacMenuProfile() {
    if (macMenu != null)
      macMenu.updateProfile();
  }

  public void setChild(DesktopWindow child) {
    sp.getViewport().setView(child);
  }

  public void setGeometry(int x, int y, int w, int h, boolean pack) {
    if (pack) {
      pack();
      vlog.debug("Set geometry to " + x + ", " + y + " (pack)");
    } else {
      setSize(w, h);
      vlog.debug("Set geometry to " + x + ", " + y + " " + w + " x " + h);
    }
    setLocation(x, y);
  }

  public void showToolbar(boolean show) { showToolbar(show, false); }

  private void showToolbar(boolean show, boolean force) {
    tb.setVisible(show && (!cc.opts.fullScreen || force));
  }

  public void updateTitle() {
    int enc = cc.lastServerEncoding;
    if (enc < 0) enc = cc.currentEncoding;
    if (enc == Encodings.encodingTight) {
      if (cc.opts.allowJpeg) {
        String[] subsampStr = { "1X", "4X", "2X", "Gray" };
        setTitle(cc.cp.name() + " [Tight + JPEG " +
                 subsampStr[cc.opts.subsampling] + " Q" + cc.opts.quality +
                 (cc.opts.compressLevel > 1 ? " + CL " + cc.opts.compressLevel : "") +
                 "]");
      } else {
        setTitle(cc.cp.name() + " [Lossless Tight" +
                 (cc.opts.compressLevel == 1 ? " + Zlib" : "") +
                 (cc.opts.compressLevel > 1 ? " + CL " + cc.opts.compressLevel : "") +
                 "]");
      }
    } else {
      setTitle(cc.cp.name() + " [" + Encodings.encodingName(enc) + "]");
    }
  }


  CConn cc;
  JScrollPane sp;
  public Toolbar tb;
  public int dx, dy = 0;
  MacMenuBar macMenu;
  boolean canDoLionFS;
  Timer timer;
  static LogWriter vlog = new LogWriter("Viewport");
}

