package com.myra.assistant.ui.usage

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.button.MaterialButton
import com.myra.assistant.R
import java.util.Calendar

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val icon: android.graphics.drawable.Drawable?,
    val timeInForegroundMs: Long
)

class UsageFragment : Fragment() {

    private lateinit var permissionLayout: View
    private lateinit var statsLayout: View
    private lateinit var barChart: BarChart
    private lateinit var recyclerUsage: RecyclerView
    private lateinit var spinnerTimeRange: Spinner
    private lateinit var textTotalUsage: TextView
    private lateinit var textRangeLabel: TextView
    private lateinit var adapter: UsageAdapter

    private val rangeLabels by lazy {
        arrayOf(
            getString(R.string.range_today),
            getString(R.string.range_yesterday),
            getString(R.string.range_week),
            getString(R.string.range_month)
        )
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_usage, container, false)
        permissionLayout = view.findViewById(R.id.permissionLayout)
        statsLayout = view.findViewById(R.id.statsLayout)
        barChart = view.findViewById(R.id.barChart)
        recyclerUsage = view.findViewById(R.id.recyclerUsage)
        spinnerTimeRange = view.findViewById(R.id.spinnerTimeRange)
        textTotalUsage = view.findViewById(R.id.textTotalUsage)
        textRangeLabel = view.findViewById(R.id.textRangeLabel)

        view.findViewById<MaterialButton>(R.id.btnGrantPermission).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        setupSpinner()
        setupRecyclerView()
        styleChart()

        return view
    }

    override fun onResume() {
        super.onResume()
        if (hasUsageStatsPermission()) {
            permissionLayout.visibility = View.GONE
            statsLayout.visibility = View.VISIBLE
            loadUsageStats(spinnerTimeRange.selectedItemPosition)
        } else {
            permissionLayout.visibility = View.VISIBLE
            statsLayout.visibility = View.GONE
        }
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = requireContext().getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            requireContext().packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun setupSpinner() {
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, rangeLabels)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTimeRange.adapter = spinnerAdapter
        spinnerTimeRange.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (hasUsageStatsPermission()) loadUsageStats(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupRecyclerView() {
        adapter = UsageAdapter()
        recyclerUsage.layoutManager = LinearLayoutManager(requireContext())
        recyclerUsage.adapter = adapter
    }

    private fun styleChart() {
        barChart.setDrawGridBackground(false)
        barChart.setFitBars(true)
    }

    private fun timeRangeMillis(rangeIndex: Int): Pair<Long, Long> {
        val endCal = Calendar.getInstance()
        val startCal = Calendar.getInstance()

        fun startOfDay(cal: Calendar) {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }

        when (rangeIndex) {
            0 -> {
                startOfDay(startCal)
                return startCal.timeInMillis to endCal.timeInMillis
            }
            1 -> {
                startCal.add(Calendar.DAY_OF_YEAR, -1)
                startOfDay(startCal)
                val start = startCal.timeInMillis
                startCal.add(Calendar.DAY_OF_YEAR, 1)
                val end = startCal.timeInMillis - 1
                return start to end
            }
            2 -> {
                startCal.add(Calendar.DAY_OF_YEAR, -6)
                startOfDay(startCal)
                return startCal.timeInMillis to endCal.timeInMillis
            }
            else -> {
                startCal.add(Calendar.MONTH, -1)
                startOfDay(startCal)
                return startCal.timeInMillis to endCal.timeInMillis
            }
        }
    }

    private fun loadUsageStats(rangeIndex: Int) {
        val (startTime, endTime) = timeRangeMillis(rangeIndex)
        textRangeLabel.text = rangeLabels[rangeIndex]

        val usm = requireContext().getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime)
        if (stats.isNullOrEmpty()) {
            adapter.submitList(emptyList())
            textTotalUsage.text = "0m"
            barChart.clear()
            barChart.invalidate()
            return
        }

        val pm = requireContext().packageManager
        val usageMap = mutableMapOf<String, Long>()

        for (stat in stats) {
            if (stat.totalTimeInForeground > 0) {
                usageMap[stat.packageName] =
                    usageMap.getOrDefault(stat.packageName, 0L) + stat.totalTimeInForeground
            }
        }

        val totalMs = usageMap.values.sum()
        textTotalUsage.text = UsageTimeFormatter.formatDuration(totalMs)

        val appList = usageMap.mapNotNull { (pkg, time) ->
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                val label = pm.getApplicationLabel(info).toString()
                val icon = pm.getApplicationIcon(info)
                AppUsageInfo(pkg, label, icon, time)
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }.sortedByDescending { it.timeInForegroundMs }.take(30)

        adapter.submitList(appList)
        setupChart(appList)
    }

    private fun setupChart(apps: List<AppUsageInfo>) {
        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        val topApps = apps.take(5)
        if (topApps.isEmpty()) {
            barChart.clear()
            barChart.invalidate()
            return
        }

        topApps.forEachIndexed { index, app ->
            val minutes = app.timeInForegroundMs / (1000 * 60).toFloat()
            entries.add(BarEntry(index.toFloat(), minutes))
            labels.add(if (app.appName.length > 10) app.appName.take(10) + "…" else app.appName)
        }

        val dataSet = BarDataSet(entries, getString(R.string.chart_minutes_label)).apply {
            color = requireContext().getColor(R.color.primary_red)
            valueTextColor = requireContext().getColor(R.color.text_primary)
            valueTextSize = 10f
        }

        barChart.data = BarData(dataSet)
        barChart.description.isEnabled = false
        barChart.legend.textColor = requireContext().getColor(R.color.text_primary)
        barChart.animateY(400)

        barChart.xAxis.apply {
            valueFormatter = IndexAxisValueFormatter(labels)
            position = XAxis.XAxisPosition.BOTTOM
            textColor = requireContext().getColor(R.color.text_hint)
            setDrawGridLines(false)
            granularity = 1f
        }

        barChart.axisLeft.apply {
            textColor = requireContext().getColor(R.color.text_hint)
            axisMinimum = 0f
            setDrawGridLines(false)
        }
        barChart.axisRight.isEnabled = false

        barChart.invalidate()
    }
}
