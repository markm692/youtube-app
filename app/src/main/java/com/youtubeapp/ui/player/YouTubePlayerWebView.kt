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
 * The embedding origin must be a third party. Serving this page with a base URL
 * of https://www.youtube.com — the usual advice for Android WebViews — makes the
 * player reach onReady and then immediately fail with error 152, because a
 * genuine embed never has youtube.com as its parent origin. Verified on device:
 * with a youtube.com base URL every video errors; with a third-party origin the
 * same videos play. The base URL and the `origin` playerVar must agree.
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
                // Present as ordinary Chrome rather than a WebView. Not what fixed
                // error 152 (the embedding origin was), but embedded playback is
                // less likely to be refused without the "; wv" token.
                settings.userAgentString = settings.userAgentString
                    .replace(Regex(";\\s*wv\\b"), "")
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(
                        msg: android.webkit.ConsoleMessage
                    ): Boolean {
                        android.util.Log.i(
                            "YTPlayer",
                            "console: ${msg.message()} @${msg.sourceId()}:${msg.lineNumber()}"
                        )
                        return true
                    }
                }
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
                    "https://example.com",
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
        // Deliberately not autoplaying: opening a video should not commit you
        // to watching it. Requiring a tap restores a decision point.
        autoplay: 0,
        playsinline: 1,
        // rel:0 no longer removes related videos (changed in 2018); it limits
        // them to the same channel, which is the most restriction the IFrame
        // API allows.
        rel: 0,
        origin: 'https://example.com'
      },
      events: {
        onReady: function () {
          console.log('player ready; origin=' + window.location.origin +
                      ' href=' + window.location.href);
        },
        onError: function (e) {
          console.log('player error ' + e.data +
                      ' origin=' + window.location.origin);
          if (window.AndroidPlayer) { AndroidPlayer.onPlayerError(e.data); }
        }
      }
    });
  }
</script>
</body>
</html>
""".trimIndent()
