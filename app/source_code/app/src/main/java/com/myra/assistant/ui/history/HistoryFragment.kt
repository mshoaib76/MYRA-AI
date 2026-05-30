package com.myra.assistant.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.myra.assistant.R
import com.myra.assistant.db.HistoryDbHelper

class HistoryFragment : Fragment() {

    private lateinit var db: HistoryDbHelper
    private lateinit var adapter: HistoryAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var emptyView: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history, container, false)
        db = HistoryDbHelper(requireContext())
        recycler = view.findViewById(R.id.recyclerHistory)
        emptyView = view.findViewById(R.id.emptyHistory)

        adapter = HistoryAdapter()
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter

        view.findViewById<MaterialButton>(R.id.btnClearHistory).setOnClickListener {
            if (adapter.itemCount == 0) return@setOnClickListener
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.clear_history_title)
                .setMessage(R.string.clear_history_message)
                .setPositiveButton(R.string.clear) { _, _ ->
                    db.clearAll()
                    refreshList()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        refreshList()
    }

    private fun refreshList() {
        val commands = db.getAllCommands()
        adapter.submitList(commands)
        emptyView.visibility = if (commands.isEmpty()) View.VISIBLE else View.GONE
        recycler.visibility = if (commands.isEmpty()) View.GONE else View.VISIBLE
    }
}
