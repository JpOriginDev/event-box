package com.ogmediaapps.eventbox.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.ogmediaapps.eventbox.R
import com.ogmediaapps.eventbox.filter.OGEventBoxFormat
import com.ogmediaapps.eventbox.filter.OGEventBoxQuery
import com.ogmediaapps.eventbox.model.OGEventBoxDebugEvent
import com.ogmediaapps.eventbox.safeOr
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything one list row needs; a change in any field re-binds just that row. */
internal data class OGEventBoxRow(
    val event: OGEventBoxDebugEvent,
    /** Time since the previous entry in the full log, or null for the first one. */
    val deltaMs: Long?,
    /** False when the header switch hides the "+1.2s" delta. */
    val showDelta: Boolean,
    /** The newest entry in the whole log (green and bold). */
    val isLatest: Boolean,
    /** The last row shown, which has no divider below it. */
    val isLastRow: Boolean,
    val isExpanded: Boolean,
    /** Search words to highlight. */
    val highlight: List<String>,
)

/**
 * RecyclerView adapter for the event list. [DiffUtil] (keyed on the log index) means a new event
 * binds one row instead of every row, which matters once filtering re-runs on each keystroke.
 */
internal class OGEventBoxAdapter(
    private val onRowClick: (OGEventBoxDebugEvent) -> Unit,
    private val onRowLongClick: (OGEventBoxDebugEvent) -> Unit,
) : ListAdapter<OGEventBoxRow, OGEventBoxAdapter.RowHolder>(DIFF) {

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long = getItem(position).event.index.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RowHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.og_event_box_item, parent, false)
        return RowHolder(view, Palette(parent.context))
    }

    override fun onBindViewHolder(holder: RowHolder, position: Int) {
        holder.bind(getItem(position), onRowClick, onRowLongClick)
    }

    /** Colors and formats looked up once per holder rather than on every bind. */
    internal class Palette(context: Context) {
        val index = ContextCompat.getColor(context, R.color.og_event_box_index)
        val time = ContextCompat.getColor(context, R.color.og_event_box_time)
        val name = ContextCompat.getColor(context, R.color.og_event_box_name)
        val latest = ContextCompat.getColor(context, R.color.og_event_box_name_latest)
        val highlight = ContextCompat.getColor(context, R.color.og_event_box_search_highlight)
        val warning = ContextCompat.getColor(context, R.color.og_event_box_warning)
        val userProperty = ContextCompat.getColor(context, R.color.og_event_box_user_property)
        val source = ContextCompat.getColor(context, R.color.og_event_box_source)
        val params = ContextCompat.getColor(context, R.color.og_event_box_params)
        val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)
    }

    internal class RowHolder(view: View, private val palette: Palette) : RecyclerView.ViewHolder(view) {
        private val header: View = view.findViewById(R.id.og_event_box_item_header)
        private val stripe: View = view.findViewById(R.id.og_event_box_item_stripe)
        private val text: TextView = view.findViewById(R.id.og_event_box_item_text)
        private val repeat: TextView = view.findViewById(R.id.og_event_box_item_repeat)
        private val warningIcon: ImageView = view.findViewById(R.id.og_event_box_item_warning)
        private val details: View = view.findViewById(R.id.og_event_box_item_details)
        private val eventName: TextView = view.findViewById(R.id.og_event_box_item_warning_event_name)
        private val paramsText: TextView = view.findViewById(R.id.og_event_box_item_params_text)
        private val warningTitle: TextView = view.findViewById(R.id.og_event_box_item_warning_title)
        private val warningMessage: TextView = view.findViewById(R.id.og_event_box_item_warning_message)
        private val divider: View = view.findViewById(R.id.og_event_box_item_divider)

        fun bind(
            row: OGEventBoxRow,
            onClick: (OGEventBoxDebugEvent) -> Unit,
            onLongClick: (OGEventBoxDebugEvent) -> Unit,
        ) {
            val event = row.event
            val context = itemView.context
            val words = row.highlight

            text.text = SpannableStringBuilder().apply {
                append(String.format(Locale.US, "%02d  ", event.index), palette.index)
                append(palette.timeFormat.format(Date(event.timestampMillis)), palette.time)
                val delta = if (row.showDelta) OGEventBoxFormat.relativeTime(row.deltaMs) else ""
                if (delta.isNotEmpty()) append(" $delta", palette.index)
                append("  ")
                event.source?.let { source ->
                    val start = length
                    append("[$source] ", palette.source)
                    highlight(start, length, words)
                }
                val nameStart = length
                append(event.name, if (row.isLatest) palette.latest else palette.name)
                if (row.isLatest) setSpan(StyleSpan(Typeface.BOLD), nameStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                highlight(nameStart, length, words)
            }

            val stripeColor = when {
                event.hasWarning -> palette.warning
                OGEventBoxQuery.isUserProperty(event) -> palette.userProperty
                !event.params.isNullOrEmpty() -> palette.params
                else -> null
            }
            stripe.visibility = if (stripeColor != null) View.VISIBLE else View.INVISIBLE
            if (stripeColor != null) stripe.backgroundTintList = ColorStateList.valueOf(stripeColor)

            repeat.isVisible = event.isRepeated
            if (event.isRepeated) repeat.text = context.getString(R.string.og_event_box_repeat, event.repeatCount)
            warningIcon.isVisible = event.hasWarning
            divider.isVisible = !row.isLastRow

            header.contentDescription = describe(context, event)

            if (row.isExpanded) bindDetails(event, words)
            details.isVisible = row.isExpanded

            val click = View.OnClickListener { onClick(event) }
            header.setOnClickListener(click)
            warningIcon.setOnClickListener(if (event.hasWarning) click else null)
            header.setOnLongClickListener {
                onLongClick(event)
                true
            }
        }

        private fun bindDetails(event: OGEventBoxDebugEvent, words: List<String>) {
            val context = itemView.context
            eventName.text = context.getString(R.string.og_event_box_event_name, event.name)

            val lines = OGEventBoxFormat.paramLines(event.params)
            paramsText.text = if (lines.isEmpty()) {
                context.getString(R.string.og_event_box_no_params)
            } else {
                SpannableStringBuilder().apply {
                    lines.forEachIndexed { i, line ->
                        if (i > 0) append('\n')
                        append("  ")
                        val keyStart = length
                        append(line.paddedKey, palette.time)
                        highlight(keyStart, length, words)
                        append(" : ", palette.index)
                        val valueStart = length
                        append(line.value, palette.name)
                        highlight(valueStart, length, words)
                        append("  ").append(line.type, palette.index)
                    }
                }
            }

            warningTitle.isVisible = event.hasWarning
            warningMessage.isVisible = event.hasWarning
            if (event.hasWarning) warningMessage.text = event.warnings.joinToString("\n") { "• $it" }
        }

        private fun describe(context: Context, event: OGEventBoxDebugEvent): String {
            val parts = mutableListOf(context.getString(R.string.og_event_box_row_description, event.index, event.name))
            event.source?.let { parts += context.getString(R.string.og_event_box_row_source, it) }
            val count = event.params?.size ?: 0
            parts += context.resources.getQuantityString(R.plurals.og_event_box_row_params, count, count)
            if (event.isRepeated) parts += context.getString(R.string.og_event_box_row_repeated, event.repeatCount)
            if (event.hasWarning) parts += context.getString(R.string.og_event_box_row_has_warning)
            parts += context.getString(R.string.og_event_box_row_expand_hint)
            return parts.joinToString(", ")
        }

        private fun SpannableStringBuilder.append(value: CharSequence, color: Int): SpannableStringBuilder {
            val start = length
            append(value)
            setSpan(ForegroundColorSpan(color), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return this
        }

        /** Paints a search-highlight background behind every case-insensitive match of [words]. */
        private fun SpannableStringBuilder.highlight(from: Int, to: Int, words: List<String>) {
            if (words.isEmpty() || from >= to) return
            val haystack = substring(from, to).lowercase(Locale.ROOT)
            for (word in words) {
                var at = haystack.indexOf(word)
                while (at >= 0) {
                    setSpan(
                        BackgroundColorSpan(palette.highlight),
                        from + at, from + at + word.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    at = haystack.indexOf(word, at + word.length)
                }
            }
        }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<OGEventBoxRow>() {
            override fun areItemsTheSame(oldItem: OGEventBoxRow, newItem: OGEventBoxRow) =
                oldItem.event.index == newItem.event.index

            override fun areContentsTheSame(oldItem: OGEventBoxRow, newItem: OGEventBoxRow) =
                safeOr(false) { oldItem == newItem }
        }
    }
}
