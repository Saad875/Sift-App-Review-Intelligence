package com.sift.app.presentation.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Collects [flow] only while the view is at least STARTED. */
fun <T> Fragment.collectWhileStarted(flow: Flow<T>, action: (T) -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            flow.collect { action(it) }
        }
    }
}

/**
 * Pads the view by the system bar (and display cutout) insets on the chosen
 * edges, keeping its original padding. Used for edge-to-edge layouts.
 */
fun View.applySystemBarPadding(top: Boolean = false, bottom: Boolean = false, horizontal: Boolean = true) {
    val initialLeft = paddingLeft
    val initialTop = paddingTop
    val initialRight = paddingRight
    val initialBottom = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
        )
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        view.updatePadding(
            left = if (horizontal) initialLeft + bars.left else initialLeft,
            top = if (top) initialTop + bars.top else initialTop,
            right = if (horizontal) initialRight + bars.right else initialRight,
            bottom = if (bottom) initialBottom + maxOf(bars.bottom, ime.bottom) else initialBottom
        )
        insets
    }
    requestApplyInsetsWhenAttached()
}

/** Sizes a status bar scrim view to the top system bar inset. */
fun View.matchStatusBarHeight() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val top = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()).top
        if (view.layoutParams.height != top) view.updateLayoutParams<ViewGroup.LayoutParams> { height = top }
        insets
    }
    requestApplyInsetsWhenAttached()
}

/** Adds the bottom system bar inset to the view's bottom margin (for floating buttons). */
fun View.applySystemBarBottomMargin() {
    val initial = (layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bottom = maxOf(
            insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom,
            insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        )
        view.updateLayoutParams<ViewGroup.MarginLayoutParams> { bottomMargin = initial + bottom }
        insets
    }
    requestApplyInsetsWhenAttached()
}

private fun View.requestApplyInsetsWhenAttached() {
    if (isAttachedToWindow) {
        ViewCompat.requestApplyInsets(this)
    } else {
        addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                v.removeOnAttachStateChangeListener(this)
                ViewCompat.requestApplyInsets(v)
            }

            override fun onViewDetachedFromWindow(v: View) = Unit
        })
    }
}

fun View.hideKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(windowToken, 0)
}

fun View.showKeyboard() {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
}

fun View.snackbar(@StringRes message: Int, duration: Int = Snackbar.LENGTH_SHORT): Snackbar =
    Snackbar.make(this, message, duration).also { it.show() }

fun View.snackbar(message: CharSequence, duration: Int = Snackbar.LENGTH_SHORT): Snackbar =
    Snackbar.make(this, message, duration).also { it.show() }

/** Copies text to the clipboard. Android 13+ shows its own confirmation; older versions get a toast. */
fun Context.copyToClipboard(label: String, text: String, @StringRes confirmation: Int) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(this, confirmation, Toast.LENGTH_SHORT).show()
    }
}

/** Text of a [UiError]: the server detail when present, else the generic message. */
fun Context.errorMessage(error: UiError): String = error.detail?.takeIf { it.isNotBlank() } ?: getString(error.messageRes)
