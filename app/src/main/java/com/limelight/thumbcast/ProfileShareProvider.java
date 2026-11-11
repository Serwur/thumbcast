package com.limelight.thumbcast;

// NOWY PLIK (moduł :app). Minimalny odpowiednik androidx FileProvider dla udostępniania profili:
// :app nie ma zależności AndroidX (upstream), a potrzebny jest tylko odczyt plików z jednego katalogu.
// Serwuje wyłącznie cacheDir/shared_profiles/<nazwa>.thumbcast.json, tylko do odczytu, bez eksportu
// (dostęp tylko przez FLAG_GRANT_READ_URI_PERMISSION w intencji ACTION_SEND).

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import app.thumbcast.android.library.ProfileRepository;

public class ProfileShareProvider extends ContentProvider {

    private static final String SHARED_DIR = "shared_profiles";

    public static String authority(Context context) {
        return context.getPackageName() + ".profiles";
    }

    /**
     * Zapisuje tekst profilu do katalogu udostępniania (stare pliki są usuwane) i zwraca content:// URI.
     */
    public static Uri writeShareFile(Context context, String fileName, String text) throws IOException {
        File dir = new File(context.getCacheDir(), SHARED_DIR);
        if (!dir.isDirectory() && !dir.mkdirs()) {
            throw new IOException("cannot create " + dir);
        }
        File[] old = dir.listFiles();
        if (old != null) {
            for (File f : old) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
        File out = new File(dir, fileName);
        try (FileOutputStream os = new FileOutputStream(out)) {
            os.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return new Uri.Builder().scheme("content").authority(authority(context)).appendPath(fileName).build();
    }

    private File fileFor(Uri uri) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (name == null || uri.getPathSegments().size() != 1 || !ProfileRepository.isSafeFileName(name)) {
            throw new FileNotFoundException("invalid uri");
        }
        //noinspection ConstantConditions
        File dir = new File(getContext().getCacheDir(), SHARED_DIR);
        File f = new File(dir, name);
        if (!f.isFile()) {
            throw new FileNotFoundException(name);
        }
        return f;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (mode != null && !"r".equals(mode)) {
            throw new SecurityException("read-only");
        }
        return ParcelFileDescriptor.open(fileFor(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        File f;
        try {
            f = fileFor(uri);
        } catch (FileNotFoundException e) {
            return null;
        }
        String[] cols = projection != null ? projection
                : new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(cols, 1);
        Object[] row = new Object[cols.length];
        for (int i = 0; i < cols.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(cols[i])) {
                row[i] = f.getName();
            } else if (OpenableColumns.SIZE.equals(cols[i])) {
                row[i] = f.length();
            }
        }
        cursor.addRow(row);
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return ProfileRepository.MIME_TYPE;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }
}
