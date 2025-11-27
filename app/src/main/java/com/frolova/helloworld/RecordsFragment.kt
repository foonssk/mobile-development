package com.frolova.helloworld

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

class RecordsFragment : Fragment() {

    private val viewModel: GameViewModel by viewModel()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val textView = TextView(requireContext()).apply {
            textSize = 16f
            setPadding(50, 50, 50, 50) // ← ИСПРАВЛЕНО: 4 параметра
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.getAllScores().collect { scores ->
                    if (scores.isEmpty()) {
                        textView.text = "Нет сохранённых рекордов."
                    } else {
                        val lines = scores.mapIndexed { i, item ->
                            "${i + 1}. ${item.player.fullName} — ${item.score.score} очков " +
                                    "(сложность: ${item.score.difficulty}, промахи: ${item.score.misses})"
                        }
                        textView.text = lines.joinToString("\n")
                    }
                }
            }
        }

        return textView
    }
}