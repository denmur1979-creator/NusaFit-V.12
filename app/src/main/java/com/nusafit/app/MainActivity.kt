package com.nusafit.app

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.location.LocationManager
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.max

class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private lateinit var status: TextView
    private lateinit var distance: TextView
    private lateinit var duration: TextView
    private lateinit var title: TextView
    private var map: GoogleMap? = null
    private var routeLine: Polyline? = null
    private var tracking = false
    private var paused = false
    private var activityNameField: TextInputEditText? = null
    private var activityTypeField: androidx.appcompat.widget.AppCompatAutoCompleteTextView? = null
    private var sessionName = "Aktivitas"
    private var selectedType = "Running"
    private var mapReady = false
    private var receiverRegistered = false
    private val prefs by lazy { getSharedPreferences("nusafit", MODE_PRIVATE) }

    private val profilePhotoPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) { prefs.edit().putString("profile_photo", uri.toString()).apply(); showProfile() }
    }

    private val mediaPicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            prefs.edit().putString("pending_media", uris.joinToString("|") { it.toString() }).apply()
            toast("${uris.size} media dipilih untuk sesi berikutnya")
        }
    }

    private val perm = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val locationOk = (result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        if (locationOk) startTracking() else toast("Izin lokasi diperlukan untuk tracking")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContext.ctx = applicationContext
        buildShell()
        registerSafeReceivers()
        showHome()
        restoreTrackingState()
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            runCatching { unregisterReceiver(trackReceiver) }
            runCatching { unregisterReceiver(stopReceiver) }
            receiverRegistered = false
        }
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun color(id: Int) = ContextCompat.getColor(this, id)

    private fun buildShell() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.nf_bg))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(14), dp(20), dp(14))
            setBackgroundColor(color(R.color.nf_primary_dark))
        }
        val brandRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.nusafit_logo)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = "Logo NusaFit"
        }
        brandRow.addView(logo, LinearLayout.LayoutParams(dp(52), dp(52)))
        val brandText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        title = TextView(this).apply {
            text = "NusaFit"
            textSize = 26f
            setTypeface(null, 1)
            setTextColor(Color.WHITE)
        }
        brandText.addView(title)
        brandText.addView(TextView(this).apply {
            text = "Sehat Hari Ini, Kuat Esok Nanti"
            textSize = 12f
            alpha = .88f
            setTextColor(Color.WHITE)
        })
        brandRow.addView(brandText)
        top.addView(brandRow)
        root.addView(top, LinearLayout.LayoutParams(-1, dp(82)))

        content = FrameLayout(this).apply { id = View.generateViewId() }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        nav = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(5), dp(8), dp(7))
            setBackgroundColor(Color.WHITE)
            elevation = dp(8).toFloat()
        }
        val items = listOf(
            "Beranda" to "⌂",
            "Riwayat" to "▣",
            "START" to "●",
            "Peta" to "⌖",
            "Statistik" to "▥",
            "Profil" to "●"
        )
        items.forEachIndexed { index, pair ->
            val b = navButton(pair.second, pair.first)
            b.setOnClickListener { selectNav(index) }
            nav.addView(b, LinearLayout.LayoutParams(0, dp(58), 1f))
        }
        root.addView(nav, LinearLayout.LayoutParams(-1, dp(68)))
        setContentView(root)
    }

    private fun navButton(icon: String, label: String) = TextView(this).apply {
        text = "$icon\n$label"
        gravity = Gravity.CENTER
        textSize = 11f
        setTextColor(color(R.color.nf_text_secondary))
        setPadding(0, dp(3), 0, 0)
    }

    private fun selectNav(index: Int) {
        when (index) {
            0 -> showHome()
            1 -> showHistory()
            2 -> { if (tracking && !paused) pauseTracking() else if (paused) showPauseChoices() else { sessionName = activityNameField?.text?.toString()?.ifBlank { "Aktivitas" } ?: "Aktivitas"; selectedType = activityTypeField?.text?.toString()?.ifBlank { "Jalan" } ?: "Jalan"; requestLocation() } }
            3 -> showMapScreen()
            4 -> showStats()
            5 -> showProfile()
        }
        for (i in 0 until nav.childCount) {
            (nav.getChildAt(i) as TextView).setTextColor(if (i == index) color(R.color.nf_primary) else color(R.color.nf_text_secondary))
        }
    }

    private fun clearContent() {
        content.removeAllViews()
    }

    private fun scrollColumn(): LinearLayout {
        val scroll = ScrollView(this).apply { clipToPadding = false }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(24))
        }
        scroll.addView(col)
        content.addView(scroll, FrameLayout.LayoutParams(-1, -1))
        return col
    }

    private fun section(text: String): TextView = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTypeface(null, 1)
        setTextColor(color(R.color.nf_text_secondary))
        setPadding(dp(3), dp(12), dp(3), dp(7))
    }

    private fun card(v: View, marginTop: Int = 0): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(18).toFloat()
        cardElevation = dp(2).toFloat()
        setCardBackgroundColor(Color.WHITE)
        addView(v)
        layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(marginTop) }
    }

    private fun button(text: String, primary: Boolean = false) = MaterialButton(this).apply {
        this.text = text
        isAllCaps = false
        textSize = 14f
        cornerRadius = dp(14)
        minimumHeight = dp(50)
        insetTop = dp(2); insetBottom = dp(2)
        if (primary) {
            setBackgroundColor(color(R.color.nf_green))
            setTextColor(Color.WHITE)
        } else {
            setBackgroundColor(Color.WHITE)
            setTextColor(color(R.color.nf_primary))
            strokeWidth = dp(1)
            setStrokeColorResource(R.color.nf_outline)
        }
    }

    private fun showHome() {
        clearContent()
        val col = scrollColumn()
        val welcome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
        }
        welcome.addView(TextView(this).apply { text = "Selamat datang di NusaFit"; textSize = 20f; setTypeface(null, 1); setTextColor(color(R.color.nf_text_primary)) })
        welcome.addView(TextView(this).apply { text = "Track aktivitasmu dengan aman dan tetap berjalan saat layar HP dikunci."; textSize = 12f; setTextColor(color(R.color.nf_text_secondary)); setPadding(0, dp(4), 0, 0) })
        col.addView(card(welcome))

        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(8), dp(14), dp(8), dp(14)) }
        status = metricValue("● Siap tracking", R.color.nf_green)
        distance = metricValue("0,00 km")
        duration = metricValue("00:00:00")
        metrics.addView(metric("STATUS", status), LinearLayout.LayoutParams(0, -2, 1f))
        metrics.addView(metric("JARAK", distance), LinearLayout.LayoutParams(0, -2, 1f))
        metrics.addView(metric("DURASI", duration), LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(card(metrics, 10))

        col.addView(section("MULAI AKTIVITAS"))
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(14)) }
        val nameLayout = TextInputLayout(this).apply { hint = "Nama aktivitas"; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE }
        val name = TextInputEditText(this).apply { hint = "Contoh: Lari Pagi"; setSingleLine(true); minHeight = dp(52); textSize = 14f }
        activityNameField = name
        nameLayout.addView(name)
        form.addView(nameLayout)
        val typeLayout = TextInputLayout(this).apply { hint = "Jenis olahraga"; boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE }
        val type = androidx.appcompat.widget.AppCompatAutoCompleteTextView(this).apply {
            keyListener = null
            setText("🏃  Lari", false)
            isFocusable = false
            setOnClickListener { showSportPicker { chosen, display -> selectedType = chosen; setText(display, false) } }
        }
        typeLayout.addView(type)
        activityTypeField = type
        form.addView(typeLayout, LinearLayout.LayoutParams(-1, dp(62)).apply { topMargin = dp(12) })
        val start = button(if (tracking && !paused) "Ⅱ  Jeda" else if (paused) "▶  Lanjutkan" else "▶  Mulai Olahraga", true)
        start.setOnClickListener {
            if (tracking && !paused) pauseTracking() else if (paused) resumeTracking() else {
                sessionName = name.text?.toString()?.ifBlank { "Aktivitas" } ?: "Aktivitas"
                selectedType = selectedType.ifBlank { "Lari" }
                requestLocation()
            }
        }
        form.addView(start, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(12) })
        val stop = button("■  Selesaikan", false)
        stop.setOnClickListener { stopTracking() }
        form.addView(stop, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(8) })
        col.addView(card(form))

        col.addView(section("MULAI / TRACKING GPS"))
        val startWrap = LinearLayout(this).apply { gravity = Gravity.CENTER; orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, dp(12)) }
        val roundStart = MaterialButton(this).apply {
            text = if (tracking && !paused) "Ⅱ" else if (paused) "▶" else "START"
            isAllCaps = false; textSize = if (tracking || paused) 28f else 18f; cornerRadius = dp(100)
            backgroundTintList = android.content.res.ColorStateList.valueOf(color(R.color.nf_green)); setTextColor(Color.WHITE)
            minWidth = dp(128); minimumHeight = dp(128); insetTop = 0; insetBottom = 0
            setOnClickListener { if (tracking && !paused) pauseTracking() else if (paused) resumeTracking() else { sessionName = name.text?.toString()?.ifBlank { "Aktivitas" } ?: "Aktivitas"; selectedType = selectedType.ifBlank { "Lari" }; requestLocation() } }
        }
        startWrap.addView(roundStart, LinearLayout.LayoutParams(dp(128), dp(128)))
        startWrap.addView(TextView(this).apply { text = if (paused) "Aktivitas dijeda" else if (tracking) "Aktivitas sedang berjalan" else "Tekan tombol untuk mulai"; textSize = 12f; setTextColor(color(R.color.nf_text_secondary)); setPadding(0, dp(8), 0, 0) })
        col.addView(card(startWrap, 4))

        col.addView(section("TOOLS"))
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        fun tool(label: String, click: () -> Unit) = button(label).apply { setOnClickListener { click() } }
        grid.addView(tool("📷  Foto / Video Aktivitas") { mediaPicker.launch("*/*") }, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(8) })
        grid.addView(tool("⏰  Pengingat Olahraga") { scheduleDialog() }, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(8) })
        grid.addView(tool("📅  Kalender & Riwayat") { selectNav(1) }, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(8) })
        grid.addView(tool("📊  Statistik & Capaian") { selectNav(3) }, LinearLayout.LayoutParams(-1, dp(50)))
        col.addView(card(grid))
    }

    private fun showSportPicker(onSelected: (String, String) -> Unit) {
        val sports = listOf(
            Triple("Jalan", "🚶", "Jalan"),
            Triple("Lari", "🏃", "Lari"),
            Triple("Bersepeda", "🚴", "Bersepeda"),
            Triple("Berenang", "🏊", "Berenang"),
            Triple("Aerobic/Fitness", "🏋️", "Aerobic/Fitness")
        )
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(8), dp(18), dp(8)) }
        val dialog = AlertDialog.Builder(this).setTitle("Pilih Jenis Olahraga").setView(list).create()
        sports.forEach { (value, icon, label) ->
            val item = TextView(this).apply {
                text = "$icon    $label"
                textSize = 17f
                setTextColor(color(R.color.nf_text_primary))
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(12), dp(14), dp(12), dp(14))
                isClickable = true
                isFocusable = true
                setOnClickListener { onSelected(value, "$icon  $label"); dialog.dismiss() }
            }
            list.addView(item, LinearLayout.LayoutParams(-1, dp(54)))
            val line = View(this).apply { setBackgroundColor(color(R.color.nf_outline)) }
            list.addView(line, LinearLayout.LayoutParams(-1, dp(1)))
        }
        dialog.show()
    }

    private fun createMap(container: FrameLayout) {
        mapReady = false
        val tag = "NusaFitMap"
        val existing = supportFragmentManager.findFragmentByTag(tag)
        if (supportFragmentManager.isStateSaved) return
        if (existing != null) {
            supportFragmentManager.beginTransaction().remove(existing).commitNowAllowingStateLoss()
        }
        val frag = SupportMapFragment.newInstance()
        supportFragmentManager.beginTransaction().replace(container.id, frag, tag).commitNowAllowingStateLoss()
        frag.getMapAsync(this)
    }

    private fun showMapScreen() {
        clearContent()
        val box = FrameLayout(this).apply { id = View.generateViewId() }
        content.addView(box, FrameLayout.LayoutParams(-1, -1))
        createMap(box)
        val overlay = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            setBackgroundColor(0xDFFFFFFF.toInt())
        }
        overlay.addView(TextView(this).apply { text = "Peta GPS"; textSize = 20f; setTypeface(null, 1); setTextColor(color(R.color.nf_text_primary)) })
        overlay.addView(TextView(this).apply { text = "Tracking tetap aktif saat HP kembali ke Home."; textSize = 12f; setTextColor(color(R.color.nf_text_secondary)) })
        val lp = FrameLayout.LayoutParams(-1, dp(80), Gravity.TOP)
        box.addView(overlay, lp)
    }

    private fun showHistory() {
        clearContent()
        val col = scrollColumn()
        col.addView(section("RIWAYAT AKTIVITAS"))
        val records = Store.activities(this)
        if (records.isEmpty()) {
            col.addView(card(TextView(this).apply { text = "Belum ada aktivitas.\nMulai olahraga untuk membuat riwayat."; textSize = 14f; setPadding(dp(18), dp(22), dp(18), dp(22)); setTextColor(color(R.color.nf_text_secondary)) }))
        } else records.forEach { a ->
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(13), dp(16), dp(13)) }
            box.addView(TextView(this).apply { text = "${a.name}  •  ${a.type}"; textSize = 16f; setTypeface(null, 1); setTextColor(color(R.color.nf_text_primary)) })
            box.addView(TextView(this).apply { text = "${date(a.start)}\n${String.format(Locale.US, "%.2f km", a.distanceKm)}  •  ${fmt(a.end - a.start)}  •  ${a.calories.toInt()} kcal"; textSize = 12f; setTextColor(color(R.color.nf_text_secondary)); setPadding(0, dp(6), 0, 0) })
            val share = button("Buat Foto Aktivitas")
            share.setOnClickListener { createActivityPhoto(a) }
            box.addView(share, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })
            col.addView(card(box, 8))
        }
    }

    private fun showStats() {
        clearContent()
        val col = scrollColumn()
        col.addView(section("STATISTIK & CAPAIAN"))
        val records = Store.activities(this)
        val totalKm = records.sumOf { it.distanceKm }
        val totalCal = records.sumOf { it.calories }.toInt()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        box.addView(TextView(this).apply { text = "${String.format(Locale.US, "%.2f", totalKm)} km"; textSize = 30f; setTypeface(null, 1); setTextColor(color(R.color.nf_primary)) })
        box.addView(TextView(this).apply { text = "Total jarak"; textSize = 12f; setTextColor(color(R.color.nf_text_secondary)) })
        box.addView(TextView(this).apply { text = "\n${records.size} aktivitas   •   $totalCal kcal"; textSize = 14f; setTextColor(color(R.color.nf_text_primary)) })
        col.addView(card(box))
        if (records.isNotEmpty()) {
            col.addView(card(StatsChartView(this, records), 10))
        }
    }

    private fun showProfile() {
        clearContent()
        val col = scrollColumn()
        col.addView(section("PROFIL & DATA TUBUH"))
        val p = Store.profile(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        val avatar = ImageView(this).apply { layoutParams = LinearLayout.LayoutParams(dp(96), dp(96)).apply { gravity = Gravity.CENTER; bottomMargin = dp(10) }; scaleType = ImageView.ScaleType.CENTER_CROP; setImageResource(android.R.drawable.sym_def_app_icon); prefs.getString("profile_photo", null)?.let { runCatching { setImageURI(Uri.parse(it)) } } }
        box.addView(avatar)
        val photoButton = button("Pilih Foto Profil")
        photoButton.setOnClickListener { profilePhotoPicker.launch("image/*") }
        box.addView(photoButton)
        box.addView(TextView(this).apply {
            text = "Profil NusaFit"; textSize = 20f; setTypeface(null, 1); setTextColor(color(R.color.nf_text_primary)); setPadding(0, dp(8), 0, 0)
        })
        val dobText = if (p.birthDateMs > 0L) dateOfBirth(p.birthDateMs) else "Tanggal lahir belum diisi"
        val currentAge = if (p.birthDateMs > 0L) calculateAge(p.birthDateMs) else p.age
        val ageText = if (currentAge > 0) "Usia otomatis: $currentAge tahun" else "Usia otomatis akan dihitung dari tanggal lahir"
        box.addView(TextView(this).apply {
            text = "${p.gender.ifBlank { "Jenis kelamin belum diisi" }}\n$dobText\n$ageText\n${if (p.heightCm > 0) "${fmtNumber(p.heightCm)} cm" else "Tinggi belum diisi"}  •  ${if (p.weightKg > 0) "${fmtNumber(p.weightKg)} kg" else "Berat belum diisi"}"
            textSize = 13f; setTextColor(color(R.color.nf_text_secondary)); setPadding(0, dp(8), 0, dp(14))
        })
        val edit = button("Edit Profil", true); edit.setOnClickListener { profileDialog() }
        box.addView(edit)
        val battery = button("⚡ Pengaturan agar tracking stabil di Home")
        battery.setOnClickListener { openBatterySettings() }
        box.addView(battery, LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(8) })
        col.addView(card(box))

        col.addView(section("KALKULATOR KESEHATAN BERAT BADAN"))
        col.addView(buildHealthCard(p))

        col.addView(section("STATUS TRACKING"))
        col.addView(card(TextView(this).apply {
            text = if (tracking) "● Tracking aktif — layanan berjalan di latar belakang dengan notifikasi permanen." else "● Tidak ada tracking aktif"
            textSize = 14f; setTextColor(if (tracking) color(R.color.nf_green) else color(R.color.nf_text_secondary)); setPadding(dp(16), dp(18), dp(16), dp(18))
        }))
    }

    private fun buildHealthCard(p: Profile): MaterialCardView {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(16)) }
        box.addView(TextView(this).apply {
            text = "Ringkasan kesehatan"; textSize = 18f; setTypeface(null, 1); setTextColor(color(R.color.nf_text_primary))
        })
        if (p.heightCm <= 0.0 || p.weightKg <= 0.0) {
            box.addView(TextView(this).apply {
                text = "Lengkapi tinggi dan berat badan di Edit Profil untuk melihat ringkasan BMI."
                textSize = 13f; setTextColor(color(R.color.nf_text_secondary)); setPadding(0, dp(8), 0, dp(4))
            })
        } else {
            val bmi = p.weightKg / ((p.heightCm / 100.0) * (p.heightCm / 100.0))
            box.addView(TextView(this).apply {
                text = String.format(Locale.getDefault(), "BMI saat ini  %.1f", bmi)
                textSize = 24f; setTypeface(null, 1); setTextColor(color(R.color.nf_primary)); setPadding(0, dp(10), 0, dp(2))
            })
            val currentAge = if (p.birthDateMs > 0L) calculateAge(p.birthDateMs) else p.age
            if (currentAge >= 18) {
                val low = 18.5 * (p.heightCm / 100.0).let { it * it }
                val high = 24.9 * (p.heightCm / 100.0).let { it * it }
                val gauge = HealthGaugeView(this).apply { this.bmi = bmi }
                box.addView(gauge, LinearLayout.LayoutParams(-1, dp(100)).apply { topMargin = dp(4) })
                box.addView(TextView(this).apply {
                    text = String.format(Locale.getDefault(), "Rentang berat referensi berbasis BMI dewasa: %.1f–%.1f kg", low, high)
                    textSize = 13f; setTypeface(null, 1); setTextColor(color(R.color.nf_text_primary)); setPadding(0, dp(8), 0, dp(2))
                })
                box.addView(TextView(this).apply {
                    text = "Ini adalah acuan kesehatan, bukan target penampilan. BMI tidak menggambarkan seluruh kondisi tubuh dan sebaiknya dibaca bersama konteks kesehatan lainnya."
                    textSize = 11f; setTextColor(color(R.color.nf_text_secondary)); setPadding(0, dp(4), 0, 0)
                })
            } else {
                box.addView(TextView(this).apply {
                    text = "Usia di bawah 18 tahun: NusaFit tidak menampilkan target berat dewasa. Penilaian berat dan pertumbuhan untuk remaja perlu menggunakan BMI menurut usia dan jenis kelamin serta kurva pertumbuhan yang sesuai, idealnya bersama tenaga kesehatan."
                    textSize = 12f; setTextColor(color(R.color.nf_text_secondary)); setPadding(0, dp(8), 0, dp(2))
                })
            }
        }
        return card(box)
    }

    private fun metricValue(text: String, colorId: Int = R.color.nf_text_primary) = TextView(this).apply { this.text = text; textSize = 14f; gravity = Gravity.CENTER; setTypeface(null, 1); setTextColor(color(colorId)) }
    private fun metric(label: String, value: TextView) = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; addView(value, LinearLayout.LayoutParams(-1, dp(34))); addView(TextView(this@MainActivity).apply { text = label; textSize = 10f; gravity = Gravity.CENTER; setTextColor(color(R.color.nf_text_secondary)) }) }

    private fun registerSafeReceivers() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(this, trackReceiver, IntentFilter("NUSAFIT_TRACK"), ContextCompat.RECEIVER_NOT_EXPORTED)
        ContextCompat.registerReceiver(this, stopReceiver, IntentFilter("NUSAFIT_STOPPED"), ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    private fun restoreTrackingState() {
        tracking = prefs.getBoolean("tracking_active", false)
        paused = prefs.getBoolean("tracking_paused", false)
        if (tracking) {
            status.text = "● TRACKING AKTIF"
            status.setTextColor(color(R.color.nf_green))
        }
    }

    private fun requestLocation() {
        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        val gpsEnabled = runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
        val networkEnabled = runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
        if (!gpsEnabled && !networkEnabled) {
            AlertDialog.Builder(this)
                .setTitle("Lokasi belum aktif")
                .setMessage("Aktifkan Lokasi/GPS di pengaturan HP agar NusaFit dapat merekam rute dan jarak.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Buka Pengaturan") { _, _ -> runCatching { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) } }
                .show()
            return
        }
        val need = mutableListOf<String>()
        val fineGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) {
            need.add(Manifest.permission.ACCESS_FINE_LOCATION)
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) need.add(Manifest.permission.POST_NOTIFICATIONS)
        if (need.isNotEmpty()) {
            prefs.edit().putString("pending_type", selectedType).apply()
            perm.launch(need.toTypedArray())
        } else startTracking()
    }

    private fun startTracking() {
        selectedType = prefs.getString("pending_type", selectedType) ?: selectedType
        tracking = true
        prefs.edit().putBoolean("tracking_active", true).apply()
        status.text = "● TRACKING AKTIF"
        status.setTextColor(color(R.color.nf_green))
        val intent = Intent(this, TrackingService::class.java).apply {
            action = "START"
            putExtra("name", sessionName)
            putExtra("type", selectedType)
        }
        try { ContextCompat.startForegroundService(this, intent) } catch (e: Exception) {
            tracking = false
            paused = false
            prefs.edit().putBoolean("tracking_active", false).putBoolean("tracking_paused", false).apply()
            toast("Tracking tidak dapat dimulai: ${e.javaClass.simpleName}")
        }
    }

    private fun pauseTracking() {
        if (!tracking || paused) return
        paused = true
        prefs.edit().putBoolean("tracking_paused", true).apply()
        runCatching { startService(Intent(this, TrackingService::class.java).setAction("PAUSE")) }
        toast("Aktivitas dijeda")
        showHome()
    }

    private fun resumeTracking() {
        if (!tracking || !paused) return
        paused = false
        prefs.edit().putBoolean("tracking_paused", false).apply()
        runCatching { startService(Intent(this, TrackingService::class.java).setAction("RESUME")) }
        toast("Aktivitas dilanjutkan")
        showHome()
    }

    private fun showPauseChoices() {
        AlertDialog.Builder(this).setTitle("Aktivitas dijeda")
            .setItems(arrayOf("Lanjutkan", "Selesaikan")) { _, which -> if (which == 0) resumeTracking() else stopTracking() }.show()
    }

    private fun createActivityPhoto(a: ActivityRecord) {
        runCatching {
            val w = 1080; val h = 1350
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp); c.drawColor(Color.rgb(235, 247, 247))
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.rgb(0, 112, 105); c.drawRoundRect(48f, 48f, 1032f, 1302f, 48f, 48f, p)
            p.color = Color.WHITE; c.drawRoundRect(70f, 70f, 1010f, 1280f, 36f, 36f, p)
            p.color = Color.rgb(0, 112, 105); p.typeface = Typeface.create("sans-serif", Typeface.BOLD); p.textSize = 64f
            c.drawText("NusaFit • ${a.type}", 120f, 170f, p)
            p.textSize = 42f; p.color = Color.DKGRAY; c.drawText(a.name.take(28), 120f, 235f, p)
            // Stylized route/map panel; GPS points are plotted in order when available.
            p.color = Color.rgb(225, 241, 239); c.drawRoundRect(110f, 290f, 970f, 800f, 28f, 28f, p)
            p.color = Color.rgb(0, 145, 210); p.strokeWidth = 14f; p.style = Paint.Style.STROKE
            val path = android.graphics.Path()
            if (a.points.isNotEmpty()) {
                val minLat = a.points.minOf { it.lat }; val maxLat = a.points.maxOf { it.lat }.let { if (it == minLat) it + .001 else it }
                val minLng = a.points.minOf { it.lng }; val maxLng = a.points.maxOf { it.lng }.let { if (it == minLng) it + .001 else it }
                a.points.forEachIndexed { idx, pt -> val x = 150f + ((pt.lng-minLng)/(maxLng-minLng)).toFloat()*780f; val y = 330f + (1f-((pt.lat-minLat)/(maxLat-minLat)).toFloat())*430f; if (idx == 0) path.moveTo(x,y) else path.lineTo(x,y) }
            } else { path.moveTo(180f,700f); path.cubicTo(300f,450f,650f,750f,890f,380f) }
            c.drawPath(path,p); p.style = Paint.Style.FILL
            p.color = Color.rgb(0, 112, 105); p.textSize = 48f; p.typeface = Typeface.DEFAULT_BOLD
            c.drawText(String.format(Locale.US,"%.2f km",a.distanceKm),120f,900f,p)
            p.color = Color.DKGRAY; p.textSize = 38f; p.typeface = Typeface.DEFAULT
            c.drawText("Durasi  ${fmt(a.end-a.start)}",120f,980f,p)
            c.drawText("Kalori  ${a.calories.toInt()} kcal",120f,1045f,p)
            c.drawText(date(a.start),120f,1130f,p)
            val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME,"NusaFit_${a.id.take(8)}.png"); put(MediaStore.Images.Media.MIME_TYPE,"image/png"); put(MediaStore.Images.Media.RELATIVE_PATH,Environment.DIRECTORY_PICTURES+"/NusaFit") }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values) ?: error("Gagal membuat file")
            contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG,100,it) }
            val send = Intent(Intent.ACTION_SEND).apply { type="image/png"; putExtra(Intent.EXTRA_STREAM,uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            startActivity(Intent.createChooser(send,"Bagikan foto aktivitas"))
        }.onFailure { toast("Foto aktivitas gagal dibuat: ${it.localizedMessage ?: "kesalahan"}") }
    }

    private fun stopTracking() {
        if (!tracking) { toast("Belum ada sesi aktif"); return }
        runCatching { startService(Intent(this, TrackingService::class.java).setAction("STOP")) }
        tracking = false
        paused = false
        prefs.edit().putBoolean("tracking_active", false).putBoolean("tracking_paused", false).apply()
        status.text = "● Menyimpan sesi..."
        status.setTextColor(color(R.color.nf_primary))
    }

    private val trackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val d = intent.getDoubleExtra("distance", 0.0)
            val t = intent.getLongExtra("duration", 0)
            distance.text = String.format(Locale.US, "%.2f km", d)
            duration.text = fmt(t)
            val la = intent.getDoubleExtra("lat", 0.0)
            val lo = intent.getDoubleExtra("lng", 0.0)
            val g = map
            if (g != null && la != 0.0 && lo != 0.0) {
                val pt = LatLng(la, lo)
                if (routeLine == null) routeLine = g.addPolyline(PolylineOptions().add(pt)) else routeLine!!.points = routeLine!!.points + pt
                g.animateCamera(CameraUpdateFactory.newLatLngZoom(pt, 16f))
            }
        }
    }

    private val stopReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            tracking = false
            prefs.edit().putBoolean("tracking_active", false).apply()
            status.text = "● Sesi tersimpan"
            val id = intent.getStringExtra("id")
            if (id != null) {
                val a = Store.activities(this@MainActivity).firstOrNull { it.id == id }
                if (a != null) {
                    val raw = prefs.getString("pending_media", "").orEmpty().split("|").filter { it.isNotBlank() }
                    Store.addMedia(this@MainActivity, a.id, raw)
                    prefs.edit().remove("pending_media").remove("pending_type").apply()
                    runCatching { if (raw.isNotEmpty()) FirebaseSync.syncActivity(this@MainActivity, a, raw) else FirebaseSync.syncActivity(this@MainActivity, a) }
                }
            }
            toast("Aktivitas tersimpan di HP")
            showHome()
        }
    }

    override fun onMapReady(g: GoogleMap) {
        map = g
        mapReady = true
        routeLine = null
        val fineGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (fineGranted || coarseGranted) {
            runCatching { g.isMyLocationEnabled = true }
        }
    }

    private fun profileDialog() {
        val existing = Store.profile(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        val gender = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Laki-laki", "Perempuan"))
            val pos = if (existing.gender == "Perempuan") 1 else 0
            setSelection(pos)
        }
        box.addView(TextView(this).apply { text = "Jenis kelamin"; textSize = 12f; setTextColor(color(R.color.nf_text_secondary)); setPadding(0, 0, 0, dp(4)) })
        box.addView(gender)

        val dobButton = MaterialButton(this).apply {
            isAllCaps = false; cornerRadius = dp(12); textSize = 14f
            text = if (existing.birthDateMs > 0L) "Tanggal lahir: ${dateOfBirth(existing.birthDateMs)}" else "Pilih tanggal lahir"
        }
        var selectedBirthDate = if (existing.birthDateMs > 0L) Calendar.getInstance().apply { timeInMillis = existing.birthDateMs } else null
        val agePreview = TextView(this).apply {
            textSize = 12f; setTextColor(color(R.color.nf_text_secondary)); setPadding(0, dp(5), 0, dp(8))
            text = if (existing.birthDateMs > 0L) "Usia otomatis: ${calculateAge(existing.birthDateMs)} tahun" else "Usia akan dihitung otomatis"
        }
        dobButton.setOnClickListener {
            val now = Calendar.getInstance()
            val initial = selectedBirthDate ?: Calendar.getInstance().apply { set(now.get(Calendar.YEAR)-18, now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH)) }
            DatePickerDialog(this, { _, y, m, d ->
                selectedBirthDate = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
                val ms = selectedBirthDate!!.timeInMillis
                dobButton.text = "Tanggal lahir: ${dateOfBirth(ms)}"
                agePreview.text = "Usia otomatis: ${calculateAge(ms)} tahun"
            }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).apply {
                datePicker.maxDate = System.currentTimeMillis()
            }.show()
        }
        box.addView(dobButton, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(10) })
        box.addView(agePreview)

        val height = EditText(this).apply { hint = "Tinggi badan (cm)"; inputType = 2 or 8192; setText(if (existing.heightCm > 0) fmtNumber(existing.heightCm) else "") }
        val weight = EditText(this).apply { hint = "Berat badan (kg)"; inputType = 2 or 8192; setText(if (existing.weightKg > 0) fmtNumber(existing.weightKg) else "") }
        box.addView(height); box.addView(weight)
        box.addView(TextView(this).apply {
            text = "Data ini digunakan untuk ringkasan kesehatan dan kalkulator BMI di halaman Profil."
            textSize = 11f; setTextColor(color(R.color.nf_text_secondary)); setPadding(0, dp(8), 0, 0)
        })

        AlertDialog.Builder(this).setTitle("Profil NusaFit").setView(box).setPositiveButton("Simpan") { _, _ ->
            val birthMs = selectedBirthDate?.timeInMillis ?: existing.birthDateMs
            val age = if (birthMs > 0L) calculateAge(birthMs) else existing.age
            val h = height.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
            val w = weight.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
            Store.saveProfile(this, Profile(gender.selectedItem.toString(), birthMs, age, h, w))
            showProfile()
        }.setNegativeButton("Batal", null).show()
    }

    private fun scheduleDialog() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        val name = EditText(this).apply { hint = "Nama aktivitas" }
        val time = TimePicker(this).apply { setIs24HourView(true) }
        box.addView(name); box.addView(time)
        AlertDialog.Builder(this).setTitle("Jadwal Olahraga").setView(box).setPositiveButton("Pasang") { _, _ ->
            val now = Calendar.getInstance(); now.set(Calendar.HOUR_OF_DAY, time.hour); now.set(Calendar.MINUTE, time.minute); now.set(Calendar.SECOND, 0); now.set(Calendar.MILLISECOND, 0)
            if (now.timeInMillis < System.currentTimeMillis()) now.add(Calendar.DAY_OF_YEAR, 1)
            val id = (System.currentTimeMillis() % 100000).toInt()
            val pi = PendingIntent.getBroadcast(this, id, Intent(this, ReminderReceiver::class.java).putExtra("id", id).putExtra("name", name.text.toString().ifBlank { "Olahraga" }), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, now.timeInMillis, pi)
            toast("Pengingat ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time)} dipasang")
        }.setNegativeButton("Batal", null).show()
    }

    private fun openBatterySettings() {
        runCatching { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = android.net.Uri.parse("package:$packageName") }) }.onFailure { toast("Buka Pengaturan Baterai lalu izinkan NusaFit berjalan tanpa pembatasan") }
    }

    private fun calculateAge(birthDateMs: Long): Int {
        if (birthDateMs <= 0L) return 0
        val dob = Calendar.getInstance().apply { timeInMillis = birthDateMs }
        val now = Calendar.getInstance()
        var age = now.get(Calendar.YEAR) - dob.get(Calendar.YEAR)
        if (now.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) age--
        return age.coerceAtLeast(0)
    }

    private fun dateOfBirth(ms: Long): String = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date(ms))
    private fun fmtNumber(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.getDefault(), "%.1f", v)

    private fun date(ms: Long) = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ms))
    private fun fmt(ms: Long): String { val s = max(0L, ms / 1000); return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60) }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
