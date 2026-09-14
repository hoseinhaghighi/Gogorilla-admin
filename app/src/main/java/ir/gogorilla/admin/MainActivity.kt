package ir.gogorilla.admin

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

class MainActivity : AppCompatActivity() {
    private val black = Color.rgb(11,11,11)
    private val dark = Color.rgb(22,22,22)
    private val yellow = Color.rgb(248,190,57)
    private val white = Color.WHITE
    private val client = OkHttpClient()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefs: android.content.SharedPreferences
    private var site = ""
    private var key = ""
    private var secret = ""
    private var content: LinearLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("gogo", Context.MODE_PRIVATE)
        site = prefs.getString("site", "") ?: ""
        key = prefs.getString("key", "") ?: ""
        secret = prefs.getString("secret", "") ?: ""
        if (site.isNotBlank() && key.isNotBlank() && secret.isNotBlank()) showDashboard() else showLogin()
    }

    private fun tv(text: String, size: Float = 16f): TextView = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(white); setPadding(14)
    }
    private fun button(text: String): Button = Button(this).apply {
        this.text = text; setTextColor(Color.BLACK); setBackgroundColor(yellow); isAllCaps = false
    }
    private fun field(hint: String, value: String = ""): EditText = EditText(this).apply {
        this.hint = hint; setText(value); setTextColor(white); setHintTextColor(Color.GRAY)
        setPadding(14); backgroundTintList = android.content.res.ColorStateList.valueOf(yellow)
    }
    private fun box(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setBackgroundColor(black); setPadding(16); layoutDirection = View.LAYOUT_DIRECTION_RTL
    }

    private fun showLogin() {
        val root = box(); root.gravity = Gravity.CENTER
        root.addView(tv("GOGORILLA ADMIN", 28f).apply { setTextColor(yellow); gravity = Gravity.CENTER })
        root.addView(tv("مدیریت فروشگاه", 16f).apply { gravity = Gravity.CENTER })
        val s = field("آدرس سایت، مثال: https://gogorilla.ir", site)
        val k = field("Consumer Key (ck_...)", key)
        val sec = field("Consumer Secret (cs_...)", secret); sec.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        root.addView(s); root.addView(k); root.addView(sec)
        val login = button("ورود به مدیریت")
        root.addView(login)
        login.setOnClickListener {
            val a=s.text.toString().trim().trimEnd('/'); val b=k.text.toString().trim(); val c=sec.text.toString().trim()
            if (a.isBlank() || b.isBlank() || c.isBlank()) { toast("همه فیلدها لازم است"); return@setOnClickListener }
            site=a; key=b; secret=c
            prefs.edit().putString("site",site).putString("key",key).putString("secret",secret).apply()
            showDashboard()
        }
        setContentView(root)
    }

    private fun showDashboard() {
        val root = box()
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(tv("Gogorilla Admin", 24f).apply { setTextColor(yellow) }, LinearLayout.LayoutParams(0,70,1f))
        val logout = button("خروج"); head.addView(logout, LinearLayout.LayoutParams(90,60)); root.addView(head)
        logout.setOnClickListener { prefs.edit().clear().apply(); showLogin() }

        val tabs = LinearLayout(this).apply { gravity = Gravity.CENTER }
        val bp=button("محصولات"); val bo=button("سفارش‌ها"); val bs=button("تنظیمات")
        tabs.addView(bp, LinearLayout.LayoutParams(0,60,1f)); tabs.addView(bo, LinearLayout.LayoutParams(0,60,1f)); tabs.addView(bs, LinearLayout.LayoutParams(0,60,1f)); root.addView(tabs)
        content = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(4); setBackgroundColor(black) }
        val scroll=ScrollView(this); scroll.addView(content); root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        bp.setOnClickListener { loadProducts() }; bo.setOnClickListener { loadOrders() }; bs.setOnClickListener { loadSettings() }
        loadProducts()
    }

    private fun auth(): String = Base64.getEncoder().encodeToString("$key:$secret".toByteArray())
    private fun request(path: String, method: String = "GET", json: String? = null): String {
        val builder=Request.Builder().url(site.trimEnd('/')+"/wp-json/wc/v3/"+path).header("Authorization","Basic ${auth()}")
        when(method){"POST"->builder.post((json ?: "{}").toRequestBody("application/json".toMediaType()));"PUT"->builder.put((json ?: "{}").toRequestBody("application/json".toMediaType()));"DELETE"->builder.delete()}
        client.newCall(builder.build()).execute().use { r -> val body=r.body?.string().orEmpty(); if(!r.isSuccessful) throw Exception("HTTP ${r.code}: ${body.take(300)}"); return body }
    }

    private fun loadProducts() {
        val c=content ?: return; c.removeAllViews(); c.addView(tv("محصولات",22f).apply{setTextColor(yellow)})
        val add=button("＋ افزودن محصول"); c.addView(add); add.setOnClickListener{ productDialog(null) }
        c.addView(tv("در حال دریافت..."))
        scope.launch { try { val body=withContext(Dispatchers.IO){request("products?per_page=100&orderby=date&order=desc")}; renderProducts(JSONArray(body)) } catch(e:Exception){showError(e)} }
    }
    private fun renderProducts(arr: JSONArray) {
        val c=content ?: return; if(c.childCount>2)c.removeViews(2,c.childCount-2)
        if(arr.length()==0){c.addView(tv("محصولی وجود ندارد"));return}
        for(i in 0 until arr.length()){
            val p=arr.getJSONObject(i); val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(dark);setPadding(12)}
            val name=tv(p.optString("name"),18f).apply{setTextColor(yellow)}
            val price=p.optString("price").ifBlank{"0"}; val stock=if(p.isNull("stock_quantity"))"نامشخص" else p.optString("stock_quantity")
            card.addView(name); card.addView(tv("قیمت: $price   |   موجودی: $stock   |   SKU: ${p.optString("sku")}"))
            val row=LinearLayout(this); val edit=button("ویرایش"); val del=button("حذف"); row.addView(edit,LinearLayout.LayoutParams(0,55,1f)); row.addView(del,LinearLayout.LayoutParams(0,55,1f)); card.addView(row)
            edit.setOnClickListener{productDialog(p)}; del.setOnClickListener{confirmDelete(p.optInt("id"),p.optString("name"))}
            c.addView(card,LinearLayout.LayoutParams(-1,LinearLayout.LayoutParams.WRAP_CONTENT).apply{setMargins(0,8,0,8)})
        }
    }

    private fun productDialog(p: JSONObject?) {
        val layout=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(20);layoutDirection=View.LAYOUT_DIRECTION_RTL}
        val name=field("نام محصول",p?.optString("name") ?: "")
        val regular=field("قیمت",p?.optString("regular_price") ?: "")
        val sale=field("قیمت تخفیف",p?.optString("sale_price") ?: "")
        val stock=field("موجودی",if(p==null)"" else if(p.isNull("stock_quantity"))"" else p.optString("stock_quantity"))
        val sku=field("SKU",p?.optString("sku") ?: "")
        val image=field("لینک تصویر محصول",if(p!=null && p.optJSONArray("images")?.length() ?: 0>0) p.optJSONArray("images")!!.optJSONObject(0)?.optString("src") ?: "" else "")
        listOf(name,regular,sale,stock,sku,image).forEach{layout.addView(it)}
        AlertDialog.Builder(this).setTitle(if(p==null)"افزودن محصول" else "ویرایش محصول").setView(layout)
            .setNegativeButton("انصراف",null).setPositiveButton("ذخیره",null).create().also{d->
                d.setOnShowListener{d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                    val data=JSONObject().put("name",name.text.toString()).put("regular_price",regular.text.toString()).put("sale_price",sale.text.toString()).put("sku",sku.text.toString())
                    if(stock.text.toString().isNotBlank()) data.put("manage_stock",true).put("stock_quantity",stock.text.toString().toIntOrNull() ?: 0)
                    if(image.text.toString().isNotBlank()) data.put("images",JSONArray().put(JSONObject().put("src",image.text.toString())))
                    scope.launch{try{withContext(Dispatchers.IO){if(p==null)request("products","POST",data.toString()) else request("products/${p.optInt("id")}","PUT",data.toString())};d.dismiss();loadProducts();toast("ذخیره شد ✓")}catch(e:Exception){toast(e.message ?: "خطا")}}
                }};d.show()
            }
    }

    private fun confirmDelete(id:Int,name:String){AlertDialog.Builder(this).setTitle("حذف محصول").setMessage("«$name» حذف شود؟").setNegativeButton("لغو",null).setPositiveButton("حذف"){_,_->scope.launch{try{withContext(Dispatchers.IO){request("products/$id?force=true","DELETE")};loadProducts();toast("حذف شد")}catch(e:Exception){toast(e.message ?: "خطا")}}}.show()}

    private fun loadOrders(){val c=content?:return;c.removeAllViews();c.addView(tv("سفارش‌ها",22f).apply{setTextColor(yellow)});c.addView(tv("در حال دریافت..."));scope.launch{try{val body=withContext(Dispatchers.IO){request("orders?per_page=100&orderby=date&order=desc")};renderOrders(JSONArray(body))}catch(e:Exception){showError(e)}}}
    private fun renderOrders(arr:JSONArray){val c=content?:return;if(c.childCount>1)c.removeViews(1,c.childCount-1);if(arr.length()==0){c.addView(tv("سفارشی وجود ندارد"));return};for(i in 0 until arr.length()){val o=arr.getJSONObject(i);val id=o.optInt("id");val card=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setBackgroundColor(dark);setPadding(12)};card.addView(tv("سفارش #$id",18f).apply{setTextColor(yellow)});card.addView(tv("مشتری: ${o.optJSONObject("billing")?.optString("first_name")} ${o.optJSONObject("billing")?.optString("last_name")}\nمبلغ: ${o.optString("total")} ${o.optString("currency")}\nوضعیت: ${o.optString("status")}"));val statuses=arrayOf("pending","processing","on-hold","completed","cancelled","refunded","failed");val sp=Spinner(this);sp.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,statuses);val current=statuses.indexOf(o.optString("status"));if(current>=0)sp.setSelection(current);card.addView(sp);val save=button("ذخیره وضعیت");card.addView(save);save.setOnClickListener{scope.launch{try{withContext(Dispatchers.IO){request("orders/$id","PUT",JSONObject().put("status",sp.selectedItem.toString()).toString())};toast("وضعیت سفارش #$id تغییر کرد")}catch(e:Exception){toast(e.message?:"خطا")}}};c.addView(card,LinearLayout.LayoutParams(-1,-2).apply{setMargins(0,8,0,8)})}}
    private fun loadSettings(){val c=content?:return;c.removeAllViews();c.addView(tv("تنظیمات",22f).apply{setTextColor(yellow)});c.addView(tv("سایت: $site"));c.addView(tv("اتصال از طریق WooCommerce REST API انجام می‌شود."));c.addView(tv("کلیدهای API فقط روی دستگاه ذخیره می‌شوند. برای نسخه انتشار عمومی، استفاده از سرور واسط امن توصیه می‌شود."));val logout=button("پاک کردن اطلاعات ورود");c.addView(logout);logout.setOnClickListener{prefs.edit().clear().apply();showLogin()}}
    private fun showError(e:Exception){val c=content?:return;c.removeAllViews();c.addView(tv("خطا در اتصال",22f).apply{setTextColor(yellow)});c.addView(tv(e.message?:"خطای ناشناخته"));val b=button("تلاش مجدد");c.addView(b);b.setOnClickListener{loadProducts()}}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
    override fun onDestroy(){scope.cancel();super.onDestroy()}
}
