package com.sift.app.presentation.common

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewbinding.ViewBinding
import com.sift.app.SiftApplication
import com.sift.app.di.AppContainer
import com.sift.app.presentation.navigation.Navigator
import com.sift.app.presentation.navigation.NavigatorHost

/**
 * Owns the ViewBinding for the view lifetime and gives access to the
 * [Navigator] and [AppContainer].
 */
abstract class BaseFragment<VB : ViewBinding>(
    private val inflate: (LayoutInflater, ViewGroup?, Boolean) -> VB
) : Fragment() {

    private var _binding: VB? = null

    /** Valid between onCreateView and onDestroyView. */
    protected val binding: VB get() = checkNotNull(_binding) { "Binding accessed outside the view lifecycle" }

    protected val navigator: Navigator get() = (requireActivity() as NavigatorHost).navigator

    protected val container: AppContainer get() = (requireActivity().application as SiftApplication).container

    final override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** The container for fragments that are not a [BaseFragment] (pages inside a pager). */
val Fragment.appContainer: AppContainer
    get() = (requireActivity().application as SiftApplication).container
