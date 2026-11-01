package app.minilauncher;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.window.OnBackInvokedDispatcher;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Launcher minimalista pensado para gastar la menor RAM posible.
 *
 * - Una sola Activity, sin Fragments, sin AndroidX, sin XML de layouts: la UI se arma en código.
 * - Solo texto: no se carga ningún ícono (los bitmaps son lo que más memoria consume en un launcher).
 * - La lista de apps SOLO existe mientras el cajón está abierto; al cerrarlo se libera.
 * - Sin servicios, sin receivers, sin trabajo en segundo plano.
 */
public class Home extends Activity {

    private static final int MAX_FAVS = 7;
    private static final int GRAY = 0xFF9E9E9E;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private FrameLayout root;
    private LinearLayout home;
    private LinearLayout favsBox;

    // Todo lo de abajo es null mientras el cajón está cerrado.
    private LinearLayout drawer;
    private EditText search;
    private ListView list;
    private AppAdapter adapter;
    private App[] all;
    private App[] shown;
    private int shownCount;

    private float downX, downY;

    // ---------------------------------------------------------------- ciclo de vida

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        root = new FrameLayout(this);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            @SuppressWarnings("deprecation")
            public WindowInsets onApplyWindowInsets(View v, WindowInsets in) {
                if (Build.VERSION.SDK_INT >= 30) {
                    Insets i = in.getInsets(WindowInsets.Type.systemBars()
                            | WindowInsets.Type.displayCutout()
                            | WindowInsets.Type.ime());
                    v.setPadding(i.left, i.top, i.right, i.bottom);
                } else {
                    v.setPadding(in.getSystemWindowInsetLeft(), in.getSystemWindowInsetTop(),
                            in.getSystemWindowInsetRight(), in.getSystemWindowInsetBottom());
                }
                return in;
            }
        });
        root.addView(buildHome());
        setContentView(root);

        // Android 13+ con targetSdk 36: onBackPressed() ya no se llama, hay que registrar el callback.
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::closeDrawer);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (drawer == null) refreshFavs();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        closeDrawer(); // botón Home: volver siempre al inicio limpio
    }

    @Override
    protected void onStop() {
        super.onStop();
        closeDrawer(); // al irnos a segundo plano no dejamos la lista en memoria
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        closeDrawer(); // en el inicio "atrás" no hace nada: un launcher no se cierra
    }

    // Deslizar hacia arriba en el inicio abre el cajón.
    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = e.getX();
            downY = e.getY();
        }
        boolean handled = super.dispatchTouchEvent(e);
        if (action == MotionEvent.ACTION_UP && drawer == null) {
            float dy = downY - e.getY();
            if (dy > dp(96) && dy > Math.abs(downX - e.getX())) openDrawer();
        }
        return handled;
    }

    // ---------------------------------------------------------------- inicio

    private View buildHome() {
        home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setPadding(dp(32), dp(48), dp(32), dp(24));

        TextClock time = new TextClock(this);
        time.setFormat24Hour("HH:mm");
        time.setFormat12Hour("h:mm");
        time.setTextColor(Color.WHITE);
        time.setTextSize(64);
        time.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        time.setIncludeFontPadding(false);
        home.addView(time);

        TextClock date = new TextClock(this);
        date.setFormat24Hour("EEEE d MMMM");
        date.setFormat12Hour("EEEE d MMMM");
        date.setTextColor(GRAY);
        date.setTextSize(16);
        home.addView(date);

        // Separador elástico: empuja los favoritos hacia abajo.
        home.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));

        favsBox = new LinearLayout(this);
        favsBox.setOrientation(LinearLayout.VERTICAL);
        home.addView(favsBox);

        TextView more = text(getString(R.string.all_apps), 18, GRAY);
        more.setPadding(0, dp(20), 0, dp(8));
        more.setOnClickListener(v -> openDrawer());
        home.addView(more);

        return home;
    }

    @SuppressWarnings("deprecation")
    private void refreshFavs() {
        favsBox.removeAllViews();
        PackageManager pm = getPackageManager();
        ArrayList<String> favs = readFavs();
        boolean changed = false;

        for (int i = 0; i < favs.size(); i++) {
            ComponentName cn = ComponentName.unflattenFromString(favs.get(i));
            String label = null;
            if (cn != null) {
                try {
                    label = pm.getActivityInfo(cn, 0).loadLabel(pm).toString();
                } catch (PackageManager.NameNotFoundException ignored) {
                    // la app se desinstaló: se limpia sola de la lista
                }
            }
            if (label == null) {
                favs.remove(i--);
                changed = true;
                continue;
            }
            favsBox.addView(favRow(label, cn));
        }
        if (changed) writeFavs(favs);
    }

    private TextView favRow(String label, final ComponentName cn) {
        TextView t = text(label, 26, Color.WHITE);
        t.setPadding(0, dp(10), 0, dp(10));
        t.setOnClickListener(v -> launch(cn));
        t.setOnLongClickListener(v -> {
            toggleFav(cn);
            refreshFavs();
            return true;
        });
        return t;
    }

    // ---------------------------------------------------------------- cajón de apps

    private void openDrawer() {
        if (drawer != null) return;
        loadApps();

        drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setBackgroundColor(Color.BLACK);
        drawer.setPadding(dp(32), dp(16), dp(32), 0);

        search = new EditText(this);
        search.setHint(R.string.search_hint);
        search.setHintTextColor(0xFF707070);
        search.setTextColor(Color.WHITE);
        search.setTextSize(20);
        search.setBackground(null);
        search.setPadding(0, dp(12), 0, dp(12));
        // Sin sugerencias ni autocorrección: menos trabajo para el teclado.
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        search.setOnEditorActionListener((v, actionId, ev) -> {
            if (shownCount > 0) launch(shown[0].cn); // Enter lanza el primer resultado
            return true;
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable e) { filter(e.toString()); }
        });
        drawer.addView(search, new LinearLayout.LayoutParams(MATCH, WRAP));

        adapter = new AppAdapter();
        list = new ListView(this);
        list.setDivider(null);
        list.setVerticalScrollBarEnabled(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> launch(shown[pos].cn));
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            toggleFav(shown[pos].cn);
            return true;
        });
        drawer.addView(list, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        home.setVisibility(View.GONE);
        root.addView(drawer, new FrameLayout.LayoutParams(MATCH, MATCH));
        filter("");
    }

    /** @return true si había un cajón abierto que se cerró. */
    private boolean closeDrawer() {
        if (drawer == null) return false;
        InputMethodManager im = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(root.getWindowToken(), 0);

        root.removeView(drawer);
        drawer = null;
        search = null;
        list = null;
        adapter = null;
        all = null;
        shown = null;
        shownCount = 0;

        home.setVisibility(View.VISIBLE);
        refreshFavs();
        return true;
    }

    @SuppressWarnings("deprecation")
    private void loadApps() {
        PackageManager pm = getPackageManager();
        Intent q = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> found = pm.queryIntentActivities(q, 0);
        String self = getPackageName();

        ArrayList<App> out = new ArrayList<>(found.size());
        for (ResolveInfo ri : found) {
            ActivityInfo ai = ri.activityInfo;
            if (self.equals(ai.packageName)) continue;
            String label = ri.loadLabel(pm).toString();
            out.add(new App(label, fold(label), new ComponentName(ai.packageName, ai.name)));
        }
        all = out.toArray(new App[0]);
        Arrays.sort(all);
        shown = new App[all.length];
    }

    /** Primero los que empiezan con el texto, después los que lo contienen. */
    private void filter(String raw) {
        String q = fold(raw.trim());
        int n = 0;
        if (q.isEmpty()) {
            for (App a : all) shown[n++] = a;
        } else {
            for (App a : all) if (a.key.startsWith(q)) shown[n++] = a;
            for (App a : all) if (!a.key.startsWith(q) && a.key.contains(q)) shown[n++] = a;
        }
        shownCount = n;
        if (adapter != null) adapter.notifyDataSetChanged();
        if (list != null) list.setSelection(0);
    }

    private final class AppAdapter extends BaseAdapter {
        @Override public int getCount() { return shownCount; }
        @Override public Object getItem(int i) { return shown[i]; }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int i, View convert, ViewGroup parent) {
            TextView t = (TextView) convert;
            if (t == null) {
                t = text("", 20, Color.WHITE);
                t.setPadding(0, dp(12), 0, dp(12));
            }
            t.setText(shown[i].label);
            return t;
        }
    }

    // ---------------------------------------------------------------- acciones

    private void launch(ComponentName cn) {
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException | SecurityException e) {
            return;
        }
        closeDrawer();
    }

    private void toggleFav(ComponentName cn) {
        ArrayList<String> favs = readFavs();
        String id = cn.flattenToString();
        if (favs.remove(id)) {
            toast(R.string.unpinned);
        } else if (favs.size() >= MAX_FAVS) {
            toast(R.string.pin_full);
            return;
        } else {
            favs.add(id);
            toast(R.string.pinned);
        }
        writeFavs(favs);
    }

    // ---------------------------------------------------------------- favoritos (SharedPreferences)

    private ArrayList<String> readFavs() {
        ArrayList<String> out = new ArrayList<>();
        String s = getSharedPreferences("p", MODE_PRIVATE).getString("f", "");
        if (!s.isEmpty()) out.addAll(Arrays.asList(s.split("\n")));
        return out;
    }

    private void writeFavs(ArrayList<String> favs) {
        getSharedPreferences("p", MODE_PRIVATE).edit()
                .putString("f", TextUtils.join("\n", favs)).apply();
    }

    // ---------------------------------------------------------------- utilidades

    private TextView text(CharSequence s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setMaxLines(1);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** Minúsculas y sin tildes, para que "camara" encuentre "Cámara". */
    private static String fold(String s) {
        String d = Normalizer.normalize(s, Normalizer.Form.NFD);
        StringBuilder b = new StringBuilder(d.length());
        for (int i = 0; i < d.length(); i++) {
            char c = d.charAt(i);
            if (Character.getType(c) != Character.NON_SPACING_MARK) b.append(Character.toLowerCase(c));
        }
        return b.toString();
    }

    private static final class App implements Comparable<App> {
        final String label;
        final String key;
        final ComponentName cn;

        App(String label, String key, ComponentName cn) {
            this.label = label;
            this.key = key;
            this.cn = cn;
        }

        @Override
        public int compareTo(App o) {
            int c = key.compareTo(o.key);
            return c != 0 ? c : label.compareTo(o.label);
        }
    }
}
