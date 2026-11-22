package com.example.communityhealthworkerassistantance;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.BitmapDrawable;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.MapTileIndex;

import java.io.File;

public class MapTiles {

    public static final String[] STYLE_NAMES = {"Street", "Street 2", "Satellite"};

    private static final String PREFS_NAME = "careconnect_prefs";
    private static final String KEY_STYLE = "map_style_v2";
    private static final int DEFAULT_STYLE = 1;

    public static void configure(Context context) {
        Context app = context.getApplicationContext();
        Configuration.getInstance().load(app, app.getSharedPreferences("osm_prefs", Context.MODE_PRIVATE));
        Configuration.getInstance().setUserAgentValue(
                "CareConnect-CHWAP/1.0 (B.Tech mini project; community health worker app)");

        File base = new File(app.getCacheDir(), "osmdroid_v4");
        File tiles = new File(base, "tiles");
        tiles.mkdirs();
        Configuration.getInstance().setOsmdroidBasePath(base);
        Configuration.getInstance().setOsmdroidTileCache(tiles);
    }

    public static int getSavedStyle(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int style = prefs.getInt(KEY_STYLE, DEFAULT_STYLE);
        if (style < 0 || style >= STYLE_NAMES.length) {
            return DEFAULT_STYLE;
        }
        return style;
    }

    public static void saveStyle(Context context, int style) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_STYLE, style).apply();
    }

    public static int nextStyle(int current) {
        return (current + 1) % STYLE_NAMES.length;
    }

    public static ITileSource source(int index) {
        if (index == 1) {
            return esri("EsriWorldStreet", "World_Street_Map");
        }
        if (index == 2) {
            return esri("EsriWorldImagery", "World_Imagery");
        }
        return TileSourceFactory.MAPNIK;
    }

    public static String attribution(int index) {
        if (index == 0) {
            return "\u00A9 OpenStreetMap contributors";
        }
        return "Tiles \u00A9 Esri";
    }

    private static OnlineTileSourceBase esri(String name, String service) {
        return new OnlineTileSourceBase(
                name,
                0,
                18,
                256,
                "",
                new String[]{"https://server.arcgisonline.com/ArcGIS/rest/services/" + service + "/MapServer/tile/"}) {
            @Override
            public String getTileURLString(long pMapTileIndex) {
                return getBaseUrl()
                        + MapTileIndex.getZoom(pMapTileIndex) + "/"
                        + MapTileIndex.getY(pMapTileIndex) + "/"
                        + MapTileIndex.getX(pMapTileIndex);
            }
        };
    }

    // Google-style teardrop pin. The tip is at the bottom centre.
    public static BitmapDrawable pin(Context context, int color) {
        float d = context.getResources().getDisplayMetrics().density;
        int width = Math.round(28 * d);
        int height = Math.round(38 * d);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color);

        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2 * d);
        stroke.setColor(Color.WHITE);

        Paint hole = new Paint(Paint.ANTI_ALIAS_FLAG);
        hole.setColor(Color.WHITE);

        float radius = width / 2f - 2 * d;
        float cx = width / 2f;
        float cy = radius + 2 * d;

        Path tip = new Path();
        tip.moveTo(cx, height - d);
        tip.lineTo(cx - radius * 0.75f, cy + radius * 0.65f);
        tip.lineTo(cx + radius * 0.75f, cy + radius * 0.65f);
        tip.close();

        canvas.drawPath(tip, fill);
        canvas.drawCircle(cx, cy, radius, fill);
        canvas.drawCircle(cx, cy, radius, stroke);
        canvas.drawCircle(cx, cy, radius * 0.38f, hole);

        return new BitmapDrawable(context.getResources(), bitmap);
    }
}