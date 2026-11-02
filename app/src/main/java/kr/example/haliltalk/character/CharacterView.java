package kr.example.haliltalk.character;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;

/** Transparent 64 px source layers rendered at an integer physical-pixel scale. */
public final class CharacterView extends View {
    private static final int SOURCE_SIZE = 64;

    private final Paint pixelPaint = new Paint();
    private final Rect destination = new Rect();
    private String[] layerPaths = new String[8];
    private Bitmap[] layers = new Bitmap[8];

    public CharacterView(Context context) {
        this(context, null);
    }

    public CharacterView(Context context, AttributeSet attrs) {
        super(context, attrs);
        pixelPaint.setAntiAlias(false);
        pixelPaint.setFilterBitmap(false);
        pixelPaint.setDither(false);
        setAppearance("mail", "none", "none", "none");
    }

    /**
     * Set one item per slot; null/unknown IDs use mail or none for that slot.
     * Call on the UI thread. The caller owns selection persistence.
     */
    public void setAppearance(String characterId, String hatId, String faceId, String propId) {
        setAppearance(characterId, hatId, faceId, "none", "none", propId);
    }

    /**
     * Set hat, face, neck/body, back, and hand prop slots independently.
     * Null/unknown IDs use mail or none. Call on the UI thread.
     */
    public void setAppearance(String characterId, String hatId, String faceId,
            String bodyId, String backId, String propId) {
        String[] paths = CharacterCatalog.layerPaths(
                characterId, hatId, faceId, bodyId, backId, propId);
        Bitmap[] nextLayers = new Bitmap[paths.length];
        for (int i = 0; i < paths.length; i++) {
            if (paths[i] != null) {
                nextLayers[i] = paths[i].equals(layerPaths[i]) ? layers[i] : load(paths[i]);
            }
        }
        layerPaths = paths;
        layers = nextLayers;
        setContentDescription(CharacterCatalog.nameFor(characterId) + " 캐릭터");
        invalidate();
    }

    private Bitmap load(String path) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream stream = getContext().getAssets().open(path)) {
            Bitmap bitmap = BitmapFactory.decodeStream(stream, null, options);
            if (bitmap == null || bitmap.getWidth() != SOURCE_SIZE
                    || bitmap.getHeight() != SOURCE_SIZE) {
                throw new IllegalStateException("Expected a 64x64 PNG asset: " + path);
            }
            bitmap.setDensity(Bitmap.DENSITY_NONE);
            return bitmap;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot open character asset: " + path, error);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int side = SOURCE_SIZE * Math.max(1, Math.round(2 * getResources().getDisplayMetrics().density));
        int width = Math.max(getSuggestedMinimumWidth(), side + getPaddingLeft() + getPaddingRight());
        int height = Math.max(getSuggestedMinimumHeight(), side + getPaddingTop() + getPaddingBottom());
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth() - getPaddingLeft() - getPaddingRight();
        int height = getHeight() - getPaddingTop() - getPaddingBottom();
        if (width <= 0 || height <= 0) return;

        int scale = Math.max(1, Math.min(width, height) / SOURCE_SIZE);
        int side = SOURCE_SIZE * scale;
        int left = getPaddingLeft() + (width - side) / 2;
        int top = getPaddingTop() + (height - side) / 2;
        destination.set(left, top, left + side, top + side);
        int save = canvas.save();
        canvas.clipRect(getPaddingLeft(), getPaddingTop(),
                getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        // Back, body, neck/body accessory, expression, face accessory, hands, hat, prop.
        for (Bitmap layer : layers) {
            if (layer != null) canvas.drawBitmap(layer, null, destination, pixelPaint);
        }
        canvas.restoreToCount(save);
    }
}
