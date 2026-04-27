package com.ty.wifikeyxposed;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class ConfigProvider extends ContentProvider {
    public static final String AUTHORITY = "com.ty.wifikeyxposed.config";
    public static final Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY);

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        String key = uri.getLastPathSegment();
        if (key == null) return null;
        
        Context context = getContext();
        if (context == null) return null;

        SharedPreferences sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        MatrixCursor cursor = new MatrixCursor(new String[]{"value"});
        
        // 默认值逻辑
        boolean def = true;
        if ("deep_clean_vip".equals(key) || "block_news".equals(key)) def = false;
        
        boolean val = sp.getBoolean(key, def);
        cursor.addRow(new Object[]{val ? 1 : 0});
        return cursor;
    }

    @Nullable @Override public String getType(@NonNull Uri uri) { return null; }
    @Nullable @Override public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) { return null; }
    @Override public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }
    @Override public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) { return 0; }
}
