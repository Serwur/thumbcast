package com.limelight.thumbcast;

// NOWY PLIK (moduł :app). Biblioteka profili sterowania: presety wbudowane i profile użytkownika,
// profil domyślny (preferencja list_thumbcast_profile = nazwa pliku; brak = ThumbcastProfiles.DEFAULT_PRESET,
// „★ Domyślny”, akcja „Ustaw jako domyślny”), przełącznik „Gesty myszy” na profil (ThumbcastSettings),
// edycja (kontrakt EditorContract),
// duplikat, zmiana nazwy, eksport (SAF), udostępnianie (ProfileShareProvider), usuwanie, import z pliku,
// ze schowka i z udostępnionego pliku/tekstu. Logika plików: ProfileRepository (:control-android).
// Opis formatu i kontraktu edytora: docs/profiles.md.

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import com.limelight.R;
import com.limelight.utils.UiHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import app.thumbcast.android.EditorContract;
import app.thumbcast.android.library.ImportPreview;
import app.thumbcast.android.library.ProfileEntry;
import app.thumbcast.android.library.ProfileLibraryException;
import app.thumbcast.android.library.ProfileRepository;
import app.thumbcast.model.ProfileMode;

public class ProfileLibraryActivity extends Activity {

    /** Ten sam klucz co dawny ListPreference; wartość = nazwa pliku profilu (ThumbcastProfiles). */
    public static final String PREF_PROFILE = "list_thumbcast_profile";

    private static final int REQ_EDIT = 1;
    private static final int REQ_EXPORT = 2;
    private static final int REQ_IMPORT_FILE = 3;

    private static final String STATE_EDIT_FILE = "thumbcast.editFile";
    private static final String STATE_EDIT_SELECT = "thumbcast.editSelect";
    private static final String STATE_EXPORT_FILE = "thumbcast.exportFile";

    private static final int MAX_ERROR_LINES = 8;

    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private ProfileRepository repo;
    private SharedPreferences prefs;
    private ThumbcastSettings settings;
    private LibraryAdapter adapter;

    private String pendingEditFile;
    private boolean pendingEditSelect;
    private String pendingExportFile;

    // ---------- API dla ustawień ----------

    /**
     * Nazwa pliku profilu domyślnego: wybór użytkownika (preferencja), a gdy go nie ma albo plik zniknął,
     * profil dostarczany z aplikacją ({@link ProfileRepository#DEFAULT_PRESET}). Preferencji nic nie zapisuje
     * przy pierwszym uruchomieniu (Preference bez defaultValue, persistent=false), więc kto nie wybrał, dostaje
     * aktualny domyślny preset także po jego zmianie w nowej wersji.
     */
    public static String defaultProfileFile(Context context) {
        String file = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(PREF_PROFILE, ProfileRepository.DEFAULT_PRESET);
        if (file == null || !ProfileRepository.create(context).exists(file)) {
            return ProfileRepository.DEFAULT_PRESET;
        }
        return file;
    }

    /** Podsumowanie preferencji w ustawieniach: nazwa wyświetlana profilu domyślnego. */
    public static String defaultProfileName(Context context) {
        String file = defaultProfileFile(context);
        ProfileEntry entry = ProfileRepository.create(context).entry(file);
        return entry != null ? entry.getName() : file;
    }

    // ---------- cykl życia ----------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UiHelper.setLocale(this);
        setContentView(R.layout.activity_thumbcast_profile_library);
        UiHelper.notifyNewRootView(this);

        repo = ProfileRepository.create(this);
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        settings = new ThumbcastSettings(this);

        ListView list = findViewById(R.id.thumbcast_library_list);
        UiHelper.applyStatusBarPadding(list);
        adapter = new LibraryAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Object item = adapter.getItem(position);
            if (item instanceof ProfileEntry) {
                showActions((ProfileEntry) item);
            }
        });

        findViewById(R.id.thumbcast_library_import_file).setOnClickListener(v -> pickImportFile());
        findViewById(R.id.thumbcast_library_import_clipboard).setOnClickListener(v -> importFromClipboard());

        if (savedInstanceState != null) {
            pendingEditFile = savedInstanceState.getString(STATE_EDIT_FILE);
            pendingEditSelect = savedInstanceState.getBoolean(STATE_EDIT_SELECT);
            pendingExportFile = savedInstanceState.getString(STATE_EXPORT_FILE);
        } else {
            handleIncomingIntent(getIntent());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_EDIT_FILE, pendingEditFile);
        outState.putBoolean(STATE_EDIT_SELECT, pendingEditSelect);
        outState.putString(STATE_EXPORT_FILE, pendingExportFile);
    }

    @Override
    protected void onDestroy() {
        io.shutdownNow();
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQ_EDIT:
                onEditResult(resultCode, data);
                break;
            case REQ_EXPORT:
                if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingExportFile != null) {
                    writeExport(pendingExportFile, data.getData());
                }
                pendingExportFile = null;
                break;
            case REQ_IMPORT_FILE:
                if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                    importFromUri(data.getData());
                }
                break;
            default:
                break;
        }
    }

    private void refresh() {
        // Plik profilu domyślnego zniknął (np. usunięty poza aplikacją): domyślny jest preset z aplikacji.
        String defaultFile = defaultProfileFile(this);
        adapter.setData(repo.listPresets(), repo.listUser(), defaultFile);
    }

    /** Stan przełącznika „Gesty myszy” profilu: zapisany wybór albo zachowanie z pliku profilu. */
    private boolean mouseGesturesOn(ProfileEntry entry) {
        Boolean stored = settings.getMouseGestures(entry.getFileName());
        return stored != null ? stored : entry.getMouseGesturesDefault();
    }

    // ---------- akcje na profilu ----------

    private void showActions(final ProfileEntry entry) {
        final List<String> labels = new ArrayList<>();
        final List<Runnable> actions = new ArrayList<>();
        boolean isActive = entry.getFileName().equals(adapter.activeFile);

        if (entry.isValid()) {
            if (!isActive) {
                labels.add(getString(R.string.thumbcast_library_action_select));
                actions.add(() -> select(entry));
            }
            final boolean mouse = mouseGesturesOn(entry);
            labels.add(getString(mouse ? R.string.thumbcast_library_action_mouse_gestures_on
                    : R.string.thumbcast_library_action_mouse_gestures_off));
            actions.add(() -> toggleMouseGestures(entry, !mouse));
            labels.add(getString(R.string.thumbcast_library_action_edit));
            actions.add(() -> edit(entry, isActive));
            labels.add(getString(R.string.thumbcast_library_action_duplicate));
            actions.add(() -> duplicate(entry));
            if (!entry.getBuiltIn()) {
                labels.add(getString(R.string.thumbcast_library_action_rename));
                actions.add(() -> rename(entry));
            }
            labels.add(getString(R.string.thumbcast_library_action_export));
            actions.add(() -> startExport(entry));
            labels.add(getString(R.string.thumbcast_library_action_share));
            actions.add(() -> share(entry));
        }
        if (!entry.getBuiltIn()) {
            labels.add(getString(R.string.thumbcast_library_action_delete));
            actions.add(() -> confirmDelete(entry, isActive));
        }
        if (actions.isEmpty()) {
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle(entry.getName())
                .setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
                .show();
    }

    private void select(ProfileEntry entry) {
        selectFile(entry.getFileName());
        Toast.makeText(this, getString(R.string.thumbcast_library_default_set, entry.getName()), Toast.LENGTH_SHORT).show();
    }

    /** Zapis tylko, gdy wybór różni się od zachowania z pliku profilu (jak MouseGesturesTransform.toStore). */
    private void toggleMouseGestures(ProfileEntry entry, boolean enabled) {
        settings.setMouseGestures(entry.getFileName(), enabled == entry.getMouseGesturesDefault() ? null : enabled);
        refresh();
        Toast.makeText(this, ThumbcastSession.mouseGesturesHint(this, enabled, entry.getMouseGesturesTouchpad()),
                Toast.LENGTH_LONG).show();
    }

    private void selectFile(String fileName) {
        prefs.edit().putString(PREF_PROFILE, fileName).apply();
        refresh();
    }

    private Intent editorIntent() {
        return new Intent().setClassName(this, EditorContract.ACTIVITY_CLASS);
    }

    private void edit(ProfileEntry entry, boolean wasActive) {
        Intent intent = editorIntent();
        // Dopóki modułu edytora nie ma w APK, komunikat zamiast crasha (i bez tworzenia kopii presetu).
        if (intent.resolveActivity(getPackageManager()) == null) {
            Toast.makeText(this, R.string.thumbcast_library_editor_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            String target = entry.getFileName();
            boolean selectAfter = false;
            if (entry.getBuiltIn()) {
                // Preset jest tylko do odczytu: edytor dostaje kopię użytkownika.
                target = repo.duplicate(target, getString(R.string.thumbcast_library_copy_suffix));
                settings.copyMouseGestures(entry.getFileName(), target);
                selectAfter = wasActive;
                refresh();
            }
            intent.putExtra(EditorContract.EXTRA_PROFILE_JSON, repo.exportText(target));
            pendingEditFile = target;
            pendingEditSelect = selectAfter;
            startActivityForResult(intent, REQ_EDIT);
        } catch (ProfileLibraryException e) {
            showError(R.string.thumbcast_library_error, e);
        } catch (ActivityNotFoundException e) {
            pendingEditFile = null;
            Toast.makeText(this, R.string.thumbcast_library_editor_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void onEditResult(int resultCode, Intent data) {
        String target = pendingEditFile;
        boolean selectAfter = pendingEditSelect;
        pendingEditFile = null;
        pendingEditSelect = false;
        if (target == null || resultCode != RESULT_OK || data == null) {
            return;
        }
        String json = data.getStringExtra(EditorContract.EXTRA_PROFILE_JSON);
        if (json == null) {
            return;
        }
        try {
            repo.updateFromText(target, json);
            if (selectAfter) {
                selectFile(target);
            } else {
                refresh();
            }
            Toast.makeText(this, R.string.thumbcast_library_saved, Toast.LENGTH_SHORT).show();
        } catch (ProfileLibraryException e) {
            showError(R.string.thumbcast_library_edit_failed, e);
        }
    }

    private void duplicate(ProfileEntry entry) {
        try {
            String file = repo.duplicate(entry.getFileName(), getString(R.string.thumbcast_library_copy_suffix));
            settings.copyMouseGestures(entry.getFileName(), file);
            refresh();
            ProfileEntry copy = repo.entry(file);
            Toast.makeText(this, getString(R.string.thumbcast_library_duplicated,
                    copy != null ? copy.getName() : file), Toast.LENGTH_SHORT).show();
        } catch (ProfileLibraryException e) {
            showError(R.string.thumbcast_library_error, e);
        }
    }

    private void rename(final ProfileEntry entry) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setSingleLine(true);
        input.setText(entry.getName());
        input.setSelection(input.getText().length());
        FrameLayout container = new FrameLayout(this);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(R.string.thumbcast_library_rename_title)
                .setView(container)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    try {
                        repo.rename(entry.getFileName(), input.getText().toString());
                        refresh();
                    } catch (ProfileLibraryException e) {
                        showError(R.string.thumbcast_library_error, e);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmDelete(final ProfileEntry entry, final boolean wasActive) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.thumbcast_library_delete_title)
                .setMessage(getString(R.string.thumbcast_library_delete_message, entry.getName()))
                .setPositiveButton(R.string.thumbcast_library_action_delete, (dialog, which) -> {
                    try {
                        repo.delete(entry.getFileName());
                        settings.setMouseGestures(entry.getFileName(), null);
                        if (wasActive) {
                            // Usunięcie profilu domyślnego: wybór użytkownika znika, domyślny jest znowu preset
                            // dostarczany z aplikacją (jak przed pierwszym wyborem).
                            prefs.edit().remove(PREF_PROFILE).apply();
                            refresh();
                            ProfileEntry shipped = repo.entry(ProfileRepository.DEFAULT_PRESET);
                            Toast.makeText(this, getString(R.string.thumbcast_library_deleted_active,
                                    shipped != null ? shipped.getName() : ProfileRepository.DEFAULT_PRESET),
                                    Toast.LENGTH_LONG).show();
                        } else {
                            refresh();
                            Toast.makeText(this, R.string.thumbcast_library_deleted, Toast.LENGTH_SHORT).show();
                        }
                    } catch (ProfileLibraryException e) {
                        showError(R.string.thumbcast_library_error, e);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ---------- eksport i udostępnianie ----------

    private void startExport(ProfileEntry entry) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(ProfileRepository.MIME_TYPE)
                .putExtra(Intent.EXTRA_TITLE, ProfileRepository.suggestedFileName(entry.getName()));
        try {
            pendingExportFile = entry.getFileName();
            startActivityForResult(intent, REQ_EXPORT);
        } catch (ActivityNotFoundException e) {
            pendingExportFile = null;
            Toast.makeText(this, R.string.thumbcast_library_no_file_picker, Toast.LENGTH_LONG).show();
        }
    }

    private void writeExport(final String fileName, final Uri uri) {
        final String text;
        try {
            text = repo.exportText(fileName);
        } catch (ProfileLibraryException e) {
            showError(R.string.thumbcast_library_error, e);
            return;
        }
        io.execute(() -> {
            String error = null;
            try (OutputStream out = openForWrite(uri)) {
                if (out == null) {
                    throw new IOException("null stream");
                }
                out.write(text.getBytes(StandardCharsets.UTF_8));
            } catch (IOException | SecurityException e) {
                error = e.getMessage() != null ? e.getMessage() : e.toString();
            }
            final String finalError = error;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (finalError == null) {
                    Toast.makeText(this, R.string.thumbcast_library_exported, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, getString(R.string.thumbcast_library_export_failed, finalError),
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private OutputStream openForWrite(Uri uri) throws IOException {
        try {
            // "wt" obcina istniejący plik; nie każdy dostawca go obsługuje.
            return getContentResolver().openOutputStream(uri, "wt");
        } catch (IllegalArgumentException | IOException e) {
            return getContentResolver().openOutputStream(uri, "w");
        }
    }

    private void share(ProfileEntry entry) {
        try {
            String text = repo.exportText(entry.getFileName());
            String name = ProfileRepository.suggestedFileName(entry.getName());
            Uri uri = ProfileShareProvider.writeShareFile(this, name, text);
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType(ProfileRepository.MIME_TYPE)
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, entry.getName())
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(ClipData.newRawUri(name, uri));
            startActivity(Intent.createChooser(send, getString(R.string.thumbcast_library_share_title)));
        } catch (ProfileLibraryException e) {
            showError(R.string.thumbcast_library_error, e);
        } catch (IOException | ActivityNotFoundException e) {
            Toast.makeText(this, getString(R.string.thumbcast_library_export_failed, String.valueOf(e.getMessage())),
                    Toast.LENGTH_LONG).show();
        }
    }

    // ---------- import ----------

    private void pickImportFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES,
                        new String[]{ProfileRepository.MIME_TYPE, "application/octet-stream", "text/plain"});
        try {
            startActivityForResult(intent, REQ_IMPORT_FILE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.thumbcast_library_no_file_picker, Toast.LENGTH_LONG).show();
        }
    }

    private void importFromClipboard() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cm != null ? cm.getPrimaryClip() : null;
        CharSequence text = null;
        if (clip != null && clip.getItemCount() > 0) {
            text = clip.getItemAt(0).getText();
        }
        if (text == null || text.toString().trim().isEmpty()) {
            Toast.makeText(this, R.string.thumbcast_library_clipboard_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        importFromText(text.toString());
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            Uri stream = getStreamExtra(intent);
            if (stream != null) {
                importFromUri(stream);
                return;
            }
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (text != null) {
                importFromText(text.toString());
            }
        } else if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            importFromUri(intent.getData());
        }
    }

    @SuppressWarnings("deprecation")
    private static Uri getStreamExtra(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);
        }
        return intent.getParcelableExtra(Intent.EXTRA_STREAM);
    }

    private void importFromText(String text) {
        try {
            showImportConfirmation(repo.prepareImport(text));
        } catch (ProfileLibraryException e) {
            showError(R.string.thumbcast_library_import_failed, e);
        }
    }

    private void importFromUri(final Uri uri) {
        // Tylko content:// (SAF, udostępnianie). file:// mógłby wskazywać prywatne pliki aplikacji.
        if (!"content".equals(uri.getScheme())) {
            showErrorMessage(R.string.thumbcast_library_import_failed, getString(R.string.thumbcast_library_err_io));
            return;
        }
        io.execute(() -> {
            ImportPreview preview = null;
            ProfileLibraryException failure = null;
            String ioError = null;
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) {
                    throw new IOException("null stream");
                }
                preview = repo.prepareImport(ProfileRepository.readLimited(in));
            } catch (ProfileLibraryException e) {
                failure = e;
            } catch (IOException | SecurityException | IllegalArgumentException e) {
                ioError = e.getMessage() != null ? e.getMessage() : e.toString();
            }
            final ImportPreview p = preview;
            final ProfileLibraryException f = failure;
            final String ioMsg = ioError;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) {
                    return;
                }
                if (p != null) {
                    showImportConfirmation(p);
                } else if (f != null) {
                    showError(R.string.thumbcast_library_import_failed, f);
                } else {
                    showErrorMessage(R.string.thumbcast_library_import_failed,
                            getString(R.string.thumbcast_library_err_io) + "\n" + ioMsg);
                }
            });
        });
    }

    private void showImportConfirmation(final ImportPreview preview) {
        StringBuilder message = new StringBuilder(getString(R.string.thumbcast_library_import_details,
                preview.getName(), preview.getControlCount(), modeLabel(preview.getMode())));
        if (preview.getIdChanged()) {
            message.append("\n\n").append(getString(R.string.thumbcast_library_import_new_id));
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.thumbcast_library_import_title)
                .setMessage(message)
                .setPositiveButton(R.string.thumbcast_library_import_confirm, (d, w) -> commitImport(preview, false))
                .setNeutralButton(R.string.thumbcast_library_import_confirm_use, (d, w) -> commitImport(preview, true))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void commitImport(ImportPreview preview, boolean use) {
        try {
            String file = repo.commitImport(preview);
            if (use) {
                selectFile(file);
            } else {
                refresh();
            }
            Toast.makeText(this, getString(R.string.thumbcast_library_imported, preview.getName()),
                    Toast.LENGTH_SHORT).show();
        } catch (ProfileLibraryException e) {
            showError(R.string.thumbcast_library_import_failed, e);
        }
    }

    // ---------- komunikaty ----------

    private String modeLabel(ProfileMode mode) {
        if (mode == null) {
            return "?";
        }
        switch (mode) {
            case KEYBOARD_MOUSE:
                return getString(R.string.thumbcast_library_mode_keyboard_mouse);
            case PAD_PLUS:
            default:
                return getString(R.string.thumbcast_library_mode_pad_plus);
        }
    }

    private String reasonText(ProfileLibraryException e) {
        switch (e.getReason()) {
            case TOO_LARGE:
                return getString(R.string.thumbcast_library_err_too_large, ProfileRepository.MAX_IMPORT_BYTES / 1024);
            case NOT_UTF8:
                return getString(R.string.thumbcast_library_err_not_utf8);
            case INVALID_PROFILE: {
                StringBuilder sb = new StringBuilder(getString(R.string.thumbcast_library_err_invalid));
                List<String> details = e.getDetails();
                for (int i = 0; i < details.size() && i < MAX_ERROR_LINES; i++) {
                    sb.append("\n• ").append(details.get(i));
                }
                if (details.size() > MAX_ERROR_LINES) {
                    sb.append("\n…");
                }
                return sb.toString();
            }
            case NOT_FOUND:
                return getString(R.string.thumbcast_library_err_not_found);
            case BUILT_IN_READ_ONLY:
                return getString(R.string.thumbcast_library_err_read_only);
            case INVALID_NAME:
                return getString(R.string.thumbcast_library_err_name, ProfileRepository.MAX_NAME_LENGTH);
            case INVALID_ID:
                return getString(R.string.thumbcast_library_err_id);
            case IO:
            default:
                return getString(R.string.thumbcast_library_err_io);
        }
    }

    private void showError(int titleRes, ProfileLibraryException e) {
        showErrorMessage(titleRes, reasonText(e));
    }

    private void showErrorMessage(int titleRes, String message) {
        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    // ---------- lista ----------

    private final class LibraryAdapter extends BaseAdapter {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_PROFILE = 1;

        private final List<Object> items = new ArrayList<>();
        String activeFile;

        void setData(List<ProfileEntry> presets, List<ProfileEntry> user, String active) {
            items.clear();
            activeFile = active;
            items.add(getString(R.string.thumbcast_library_section_builtin));
            items.addAll(presets);
            items.add(getString(R.string.thumbcast_library_section_user));
            if (user.isEmpty()) {
                items.add(getString(R.string.thumbcast_library_empty_user));
            } else {
                items.addAll(user);
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public int getViewTypeCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return items.get(position) instanceof ProfileEntry ? TYPE_PROFILE : TYPE_HEADER;
        }

        @Override
        public boolean isEnabled(int position) {
            return getItemViewType(position) == TYPE_PROFILE;
        }

        @Override
        public boolean areAllItemsEnabled() {
            return false;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(ProfileLibraryActivity.this);
            Object item = items.get(position);
            if (!(item instanceof ProfileEntry)) {
                TextView header = convertView instanceof TextView ? (TextView) convertView
                        : (TextView) inflater.inflate(R.layout.thumbcast_profile_header, parent, false);
                header.setText((String) item);
                return header;
            }

            View row = convertView != null ? convertView
                    : inflater.inflate(R.layout.thumbcast_profile_item, parent, false);
            ProfileEntry entry = (ProfileEntry) item;
            RadioButton active = row.findViewById(R.id.thumbcast_profile_active);
            TextView name = row.findViewById(R.id.thumbcast_profile_name);
            TextView details = row.findViewById(R.id.thumbcast_profile_details);

            boolean isActive = entry.getFileName().equals(activeFile);
            active.setChecked(isActive);
            name.setText(isActive ? getString(R.string.thumbcast_library_default_marker, entry.getName())
                    : entry.getName());
            if (entry.isValid()) {
                String kind = getString(entry.getBuiltIn() ? R.string.thumbcast_library_builtin
                        : R.string.thumbcast_library_custom);
                String controls = getResources().getQuantityString(R.plurals.thumbcast_library_controls,
                        entry.getControlCount(), entry.getControlCount());
                String text = kind + " · " + modeLabel(entry.getMode()) + " · " + controls;
                if (mouseGesturesOn(entry)) {
                    text += " · " + getString(R.string.thumbcast_library_mouse_gestures_tag);
                }
                details.setText(text);
            } else {
                details.setText(getString(R.string.thumbcast_library_invalid, entry.getError()));
            }
            row.setContentDescription(isActive
                    ? getString(R.string.thumbcast_library_active_description, entry.getName())
                    : entry.getName());
            return row;
        }
    }
}
