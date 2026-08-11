package com.youtubeapp.ui.player

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Embeds a video using YouTube's official IFrame Player API.
 *
 * A raw <iframe src="..."> inside a data-URL document fails with error 152
 * because the player cannot validate the parent origin. The IFrame API with an
 * explicit `origin` playerVar, served from a page whose base URL is
 * youtube.com, is the approach YouTube supports.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayerWebView(
    videoId: String,
    onError: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    // Keep the latest callback without rebuilding the WebView on recomposition.
    val currentOnError by rememberUpdatedState(onError)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                setBackgroundColor(0xFF000000.toInt())

                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onPlayerError(code: Int) {
                            post { currentOnError(code) }
                        }
                    },
                    "AndroidPlayer"
                )
            }
        },
        update = { webView ->
            // Only (re)load when the video actually changes.
            if (webView.tag != videoId) {
                webView.tag = videoId
                webView.loadDataWithBaseURL(
                    "https://www.youtube.com",
                    buildPlayerHtml(videoId),
                    "text/html",
                    "utf-8",
                    null
                )
            }
        }
    )
}

private fun buildPlayerHtml(videoId: String): String = """
<!DOCTYPE html>
<html>
<head>
<meta name="viewport" content="width=device-width, initial-scale=1, user-scalable=no">
<style>
  html, body { margin:0; padding:0; height:100%; background:#000; overflow:hidden; }
  #player { width:100%; height:100%; }
</style>
</head>
<body>
<div id="player"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
  var player;
  function onYouTubeIframeAPIReady() {
    player = new YT.Player('player', {
      width: '100%',
      height: '100%',
      videoId: '$videoId',
      playerVars: {
        autoplay: 1,
        playsinline: 1,
        rel: 0,
        origin: 'https://www.youtube.com'
      },
      events: {
        onError: function (e) {
          if (window.AndroidPlayer) { AndroidPlayer.onPlayerError(e.data); }
        }
      }
    });
  }
</script>
</body>
</html>
""".trimIndent()
