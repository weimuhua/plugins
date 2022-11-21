// Copyright 2013 The Flutter Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

package io.flutter.plugins.webviewflutter;

import android.os.Build;
import android.os.Message;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import io.flutter.plugins.webviewflutter.GeneratedAndroidWebView.WebChromeClientHostApi;
import android.net.Uri;
import android.util.Log;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.webkit.ValueCallback;

/**
 * Host api implementation for {@link WebChromeClient}.
 *
 * <p>Handles creating {@link WebChromeClient}s that intercommunicate with a paired Dart object.
 */
public class WebChromeClientHostApiImpl implements WebChromeClientHostApi {
  private static final String TAG = "WebChromeClientHostApiI";
  private final InstanceManager instanceManager;
  private final WebChromeClientCreator webChromeClientCreator;
  private final WebChromeClientFlutterApiImpl flutterApi;

  private static ValueCallback<Uri> valueCallback;
  private static ValueCallback<Uri[]> filePathCallback;
  private final static int FILE_CHOOSER_RESULT_CODE = 10000;

  /**
   * Implementation of {@link WebChromeClient} that passes arguments of callback methods to Dart.
   */
  public static class WebChromeClientImpl extends WebChromeClient implements Releasable {
    @Nullable private WebChromeClientFlutterApiImpl flutterApi;
    private WebViewClient webViewClient;

    /**
     * Creates a {@link WebChromeClient} that passes arguments of callbacks methods to Dart.
     *
     * @param flutterApi handles sending messages to Dart
     * @param webViewClient receives forwarded calls from {@link WebChromeClient#onCreateWindow}
     */
    public WebChromeClientImpl(
        @NonNull WebChromeClientFlutterApiImpl flutterApi, WebViewClient webViewClient) {
      this.flutterApi = flutterApi;
      this.webViewClient = webViewClient;
    }

    @Override
    public boolean onCreateWindow(
        final WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
      return onCreateWindow(view, resultMsg, new WebView(view.getContext()));
    }

    /**
     * Verifies that a url opened by `Window.open` has a secure url.
     *
     * @param view the WebView from which the request for a new window originated.
     * @param resultMsg the message to send when once a new WebView has been created. resultMsg.obj
     *     is a {@link WebView.WebViewTransport} object. This should be used to transport the new
     *     WebView, by calling WebView.WebViewTransport.setWebView(WebView)
     * @param onCreateWindowWebView the temporary WebView used to verify the url is secure
     * @return this method should return true if the host application will create a new window, in
     *     which case resultMsg should be sent to its target. Otherwise, this method should return
     *     false. Returning false from this method but also sending resultMsg will result in
     *     undefined behavior
     */
    @VisibleForTesting
    boolean onCreateWindow(
        final WebView view, Message resultMsg, @Nullable WebView onCreateWindowWebView) {
      final WebViewClient windowWebViewClient =
          new WebViewClient() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public boolean shouldOverrideUrlLoading(
                @NonNull WebView windowWebView, @NonNull WebResourceRequest request) {
              if (!webViewClient.shouldOverrideUrlLoading(view, request)) {
                view.loadUrl(request.getUrl().toString());
              }
              return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView windowWebView, String url) {
              if (!webViewClient.shouldOverrideUrlLoading(view, url)) {
                view.loadUrl(url);
              }
              return true;
            }
          };

      if (onCreateWindowWebView == null) {
        onCreateWindowWebView = new WebView(view.getContext());
      }
      onCreateWindowWebView.setWebViewClient(windowWebViewClient);

      final WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
      transport.setWebView(onCreateWindowWebView);
      resultMsg.sendToTarget();

      return true;
    }

    @Override
    public void onProgressChanged(WebView view, int progress) {
      if (flutterApi != null) {
        flutterApi.onProgressChanged(this, view, (long) progress, reply -> {});
      }
    }

    //For Android  >= 4.1
    public void openFileChooser(ValueCallback<Uri> valueCallback, String acceptType, String capture) {
      WebChromeClientHostApiImpl.valueCallback = valueCallback;
      openImageChooserActivity();
    }

    // For Android >= 5.0
    @Override
    public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
      WebChromeClientHostApiImpl.filePathCallback = filePathCallback;
      openImageChooserActivity();
      return true;
    }

    /**
     * Set the {@link WebViewClient} that calls to {@link WebChromeClient#onCreateWindow} are passed
     * to.
     *
     * @param webViewClient the forwarding {@link WebViewClient}
     */
    public void setWebViewClient(WebViewClient webViewClient) {
      this.webViewClient = webViewClient;
    }

    @Override
    public void release() {
      if (flutterApi != null) {
        flutterApi.dispose(this, reply -> {});
      }
      flutterApi = null;
    }
  }

  /** Handles creating {@link WebChromeClient}s for a {@link WebChromeClientHostApiImpl}. */
  public static class WebChromeClientCreator {
    /**
     * Creates a {@link DownloadListenerHostApiImpl.DownloadListenerImpl}.
     *
     * @param flutterApi handles sending messages to Dart
     * @param webViewClient receives forwarded calls from {@link WebChromeClient#onCreateWindow}
     * @return the created {@link DownloadListenerHostApiImpl.DownloadListenerImpl}
     */
    public WebChromeClientImpl createWebChromeClient(
        WebChromeClientFlutterApiImpl flutterApi, WebViewClient webViewClient) {
      return new WebChromeClientImpl(flutterApi, webViewClient);
    }
  }

  /**
   * Creates a host API that handles creating {@link WebChromeClient}s.
   *
   * @param instanceManager maintains instances stored to communicate with Dart objects
   * @param webChromeClientCreator handles creating {@link WebChromeClient}s
   * @param flutterApi handles sending messages to Dart
   */
  public WebChromeClientHostApiImpl(
      InstanceManager instanceManager,
      WebChromeClientCreator webChromeClientCreator,
      WebChromeClientFlutterApiImpl flutterApi) {
    this.instanceManager = instanceManager;
    this.webChromeClientCreator = webChromeClientCreator;
    this.flutterApi = flutterApi;
  }

  @Override
  public void create(Long instanceId, Long webViewClientInstanceId) {
    final WebViewClient webViewClient =
        (WebViewClient) instanceManager.getInstance(webViewClientInstanceId);
    final WebChromeClient webChromeClient =
        webChromeClientCreator.createWebChromeClient(flutterApi, webViewClient);
    instanceManager.addDartCreatedInstance(webChromeClient, instanceId);
  }

  private static void openImageChooserActivity() {
    if (WebViewFlutterPlugin.activity == null) {
      Log.v(TAG, "activity is null");
      return;
    }
    Intent it = new Intent(Intent.ACTION_GET_CONTENT);
    it.addCategory(Intent.CATEGORY_OPENABLE);
    it.setType("image/*");

    Intent chooser = new Intent(Intent.ACTION_CHOOSER);
    chooser.putExtra(Intent.EXTRA_INTENT, it);
    WebViewFlutterPlugin.activity.startActivityForResult(chooser, FILE_CHOOSER_RESULT_CODE);
  }

  public boolean activityResult(int requestCode, int resultCode, Intent data) {
    if (null == valueCallback && null == filePathCallback) {
      return false;
    }
    Uri result = null;
    if (requestCode == FILE_CHOOSER_RESULT_CODE) {
      result = data == null || resultCode != Activity.RESULT_OK ? null : data.getData();
    }
    if (filePathCallback != null) {
      onActivityResultAboveL(requestCode, resultCode, data);
    } else if (valueCallback != null && result != null) {
      valueCallback.onReceiveValue(result);
      valueCallback = null;
    }
    return false;
  }

  @TargetApi(Build.VERSION_CODES.LOLLIPOP)
  private void onActivityResultAboveL(int requestCode, int resultCode, Intent intent) {
    Uri[] results = null;
    if (requestCode == FILE_CHOOSER_RESULT_CODE && resultCode == Activity.RESULT_OK) {
      if (intent != null) {
        String dataString = intent.getDataString();
        ClipData clipData = intent.getClipData();
        if (clipData != null) {
          results = new Uri[clipData.getItemCount()];
          for (int i = 0; i < clipData.getItemCount(); i++) {
            ClipData.Item item = clipData.getItemAt(i);
            results[i] = item.getUri();
          }
        }
        if (dataString != null) {
          results = new Uri[]{Uri.parse(dataString)};
        }
      }
    }
    filePathCallback.onReceiveValue(results);
    filePathCallback = null;
  }

}
