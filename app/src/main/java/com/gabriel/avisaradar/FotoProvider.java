package com.gabriel.avisaradar;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

/**
 * Comparte la foto del aparcamiento con la cámara (para guardarla)
 * y con WhatsApp (para enviarla), sin dar acceso a nada más de la app.
 */
public class FotoProvider extends ContentProvider {

    public static final String AUTORIDAD = "com.gabriel.avisaradar.foto";
    public static final String FICHERO = "aparcamiento.jpg";
    public static final Uri URI = Uri.parse("content://" + AUTORIDAD + "/" + FICHERO);

    private File fichero() {
        return new File(getContext().getFilesDir(), FICHERO);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String modo) throws FileNotFoundException {
        if (!FICHERO.equals(uri.getLastPathSegment())) throw new FileNotFoundException();
        int m = ParcelFileDescriptor.parseMode(modo);
        if ((m & ParcelFileDescriptor.MODE_WRITE_ONLY) != 0 || (m & ParcelFileDescriptor.MODE_READ_WRITE) != 0) {
            m |= ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_TRUNCATE;
        }
        return ParcelFileDescriptor.open(fichero(), m);
    }

    @Override
    public String getType(Uri uri) {
        return "image/jpeg";
    }

    @Override
    public Cursor query(Uri uri, String[] columnas, String sel, String[] args, String orden) {
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        File f = fichero();
        c.addRow(new Object[]{"aparcamiento.jpg", f.exists() ? f.length() : 0});
        return c;
    }

    @Override
    public Uri insert(Uri uri, ContentValues v) {
        return null;
    }

    @Override
    public int delete(Uri uri, String sel, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues v, String sel, String[] args) {
        return 0;
    }
}
