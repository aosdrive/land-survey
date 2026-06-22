package pk.gop.pulse.katchiAbadi.presentation.util

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import pk.gop.pulse.katchiAbadi.R
import pk.gop.pulse.katchiAbadi.domain.model.JKGrowerEntity

class JKGrowerSelectionDialog(
    private val context: Context,
    private val growers: List<JKGrowerEntity>,
    private val onSelected: (JKGrowerEntity) -> Unit
) {

    fun show() {
        val dialog = Dialog(context)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_grower_select, null)
        dialog.setContentView(view)
        dialog.window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val tvCount = view.findViewById<TextView>(R.id.tvGrowerCount)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        val rv = view.findViewById<RecyclerView>(R.id.rvGrowers)
        val tvEmpty = view.findViewById<TextView>(R.id.tvEmpty)
        val btnCancel = view.findViewById<TextView>(R.id.btnCancelGrower)

        tvCount.text = "${growers.size} growers available"

        val adapter = GrowerAdapter(growers) { grower ->
            onSelected(grower)
            dialog.dismiss()
        }
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val matched = adapter.filter(s?.toString().orEmpty())
                tvEmpty.visibility = if (matched == 0) View.VISIBLE else View.GONE
                tvCount.text = "$matched of ${growers.size} growers"
            }
        })

        btnCancel.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // ---- Adapter ----
    private class GrowerAdapter(
        private val all: List<JKGrowerEntity>,
        private val onClick: (JKGrowerEntity) -> Unit
    ) : RecyclerView.Adapter<GrowerAdapter.VH>() {

        private var items: List<JKGrowerEntity> = all

        // Soft palette for avatar circles, cycled by position
        private val palette = listOf(
            Color.parseColor("#2E7D32"),
            Color.parseColor("#00796B"),
            Color.parseColor("#1565C0"),
            Color.parseColor("#6A1B9A"),
            Color.parseColor("#C62828"),
            Color.parseColor("#EF6C00")
        )

        fun filter(query: String): Int {
            val q = query.trim().lowercase()
            items = if (q.isEmpty()) all else all.filter { g ->
                g.growerName.lowercase().contains(q) ||
                        g.fatherName.lowercase().contains(q) ||
                        g.cnicNo.lowercase().contains(q) ||
                        g.passbookNo.lowercase().contains(q) ||
                        g.mobileNo.lowercase().contains(q)
            }
            notifyDataSetChanged()
            return items.size
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_grower_card, parent, false)
            return VH(v)
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val g = items[position]
//            holder.tvIndex.text = "${position + 1}"
            holder.tvName.text = g.growerName.ifBlank { "Unknown" }
            holder.tvFather.text =
                if (g.fatherName.isNotBlank()) "s/o ${g.fatherName}" else ""
            holder.tvFather.visibility =
                if (g.fatherName.isNotBlank()) View.VISIBLE else View.GONE
            // CNIC line
            holder.tvMeta.text = if (g.cnicNo.isNotBlank()) "CNIC: ${g.cnicNo}" else ""
            holder.tvMeta.visibility = if (g.cnicNo.isNotBlank()) View.VISIBLE else View.GONE

            // Passbook line
            holder.tvMeta2.text = if (g.passbookNo.isNotBlank()) "PB: ${g.passbookNo}" else ""
            holder.tvMeta2.visibility = if (g.passbookNo.isNotBlank()) View.VISIBLE else View.GONE
            val initial = g.growerName.trim().firstOrNull()?.uppercase() ?: "?"
            holder.tvInitial.text = initial
            (holder.tvInitial.background as? android.graphics.drawable.GradientDrawable)
                ?.setColor(palette[position % palette.size])
                ?: holder.tvInitial.setBackgroundColor(palette[position % palette.size])

            holder.itemView.setOnClickListener { onClick(g) }
        }

        class VH(v: View) : RecyclerView.ViewHolder(v) {
//            val tvIndex: TextView = v.findViewById(R.id.tvIndex)
            val tvInitial: TextView = v.findViewById(R.id.tvInitial)
            val tvName: TextView = v.findViewById(R.id.tvName)
            val tvFather: TextView = v.findViewById(R.id.tvFather)
            val tvMeta: TextView = v.findViewById(R.id.tvMeta1)
            val tvMeta2: TextView = v.findViewById(R.id.tvMeta2)
        }
    }
}