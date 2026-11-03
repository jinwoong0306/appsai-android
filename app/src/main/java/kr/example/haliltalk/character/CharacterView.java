package kr.example.haliltalk.character;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

import java.io.IOException;
import java.io.InputStream;

/** Latest artwork and accessories share a padded canvas with character-specific attachment points. */
public final class CharacterView extends View {
    private static final int SOURCE_SIZE = 128;
    private static final int CANVAS_SIZE = 160;

    private static final class Fit {
        final float[] eyes, head, neck, hand, mouth;
        final float angle, eyeGap;
        Fit(float[] eyes, float[] head, float[] neck, float[] hand, float[] mouth) {
            this.eyes=eyes;this.head=head;this.neck=neck;this.hand=hand;this.mouth=mouth;
            angle=(float)Math.toDegrees(Math.atan2(eyes[3]-eyes[1],eyes[2]-eyes[0]));
            eyeGap=(float)Math.hypot(eyes[2]-eyes[0],eyes[3]-eyes[1]);
        }
    }

    // Measured on the current 128px art: left/right pupil centers; head/neck x,y,scale;
    // right hand center; upper lip center. Order matches CharacterCatalog.CHARACTER_IDS.
    private static final Fit[] FITS = {
        new Fit(new float[]{50.5f,58,78,51}, new float[]{62,36,2}, new float[]{64,79,1.8f}, new float[]{111,56}, new float[]{66.5f,59}), // mail
        new Fit(new float[]{49.5f,65,75.5f,66.5f}, new float[]{63,39,2}, new float[]{62,82,1.8f}, new float[]{111,88}, new float[]{61.5f,70}), // video
        new Fit(new float[]{46.5f,63,71,68}, new float[]{57,34,1.8f}, new float[]{60,83,1.6f}, new float[]{94,93}, new float[]{58,70}), // music
        new Fit(new float[]{46,62,77,61}, new float[]{63,42,2.1f}, new float[]{63,80,1.8f}, new float[]{111,78}, new float[]{62,66}), // photo
        new Fit(new float[]{50.5f,71,81,66.5f}, new float[]{62,38,1.9f}, new float[]{64,86,1.65f}, new float[]{111,69}, new float[]{67.5f,75}), // chat
        new Fit(new float[]{51.5f,67,77.5f,68}, new float[]{64,43,2}, new float[]{64,84,1.8f}, new float[]{111,87}, new float[]{64,73}), // map
        new Fit(new float[]{51,64.5f,82,65}, new float[]{66,39,2.1f}, new float[]{66,82,1.9f}, new float[]{111,91}, new float[]{66,69}), // calendar
        new Fit(new float[]{50,64.5f,79,61.5f}, new float[]{64,43,2}, new float[]{65,84,1.75f}, new float[]{109,91}, new float[]{66,68}), // reading
        new Fit(new float[]{50,62.5f,78,65}, new float[]{65,50,1.8f}, new float[]{65,82,1.6f}, new float[]{98,83}, new float[]{63.5f,69}) // fitness
    };

    private final Paint pixelPaint = new Paint();
    private final Rect destination = new Rect();
    private String[] layerPaths = new String[8];
    private Bitmap[] layers = new Bitmap[8];
    private Matrix[] placements = new Matrix[8];

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
        String[] items={backId,null,bodyId,null,faceId,null,hatId,propId};
        for(int i=0;i<placements.length;i++) {
            placements[i]=paths[i]!=null&&i!=1?accessoryMatrix(characterId,i,items[i]):null;
        }
        setContentDescription(CharacterCatalog.nameFor(characterId) + " 캐릭터");
        invalidate();
    }

    static Matrix accessoryMatrix(String characterId, int layer, String itemId) {
        int character=0;
        for(int i=0;i<CharacterCatalog.CHARACTER_IDS.length;i++)
            if(CharacterCatalog.CHARACTER_IDS[i].equals(characterId)){character=i;break;}
        Fit fit=FITS[character];
        float sourceX=32.5f,sourceY=39.5f,targetX=64,targetY=fit.neck[1]-8;
        float scale=2.3f,angle=fit.angle;
        if(layer==6){
            sourceY=22;targetX=fit.head[0];targetY=fit.head[1];scale=fit.head[2];
            if("beret".equals(itemId)||"beanie".equals(itemId))scale*=1.1f;
        }else if(layer==4){
            sourceX=32;
            sourceY="star_glasses".equals(itemId)?28:29.5f;
            targetX=(fit.eyes[0]+fit.eyes[2])/2;targetY=(fit.eyes[1]+fit.eyes[3])/2;
            scale=fit.eyeGap/14;
            if("sunglasses".equals(itemId))sourceX=31.5f;
            else if("star_glasses".equals(itemId)){sourceX=31.5f;scale=fit.eyeGap/13;}
            else if("eyepatch".equals(itemId)){
                sourceX=25.5f;targetX=fit.eyes[0];targetY=fit.eyes[1];
            }
            if("moustache".equals(itemId)){
                sourceX=32.5f;sourceY=33.5f;targetX=fit.mouth[0];targetY=fit.mouth[1];scale=fit.eyeGap/24;
            }else if("cheek_bandage".equals(itemId)){
                sourceX=42.5f;sourceY=34;targetX=fit.eyes[2]+10;targetY=fit.eyes[3]+10;
                scale=fit.eyeGap/22;
            }
        }else if(layer==2){
            targetX=fit.neck[0];targetY=fit.neck[1];scale=fit.neck[2];
        }else if(layer==7){
            sourceX=52;sourceY=43;targetX=fit.hand[0];targetY=fit.hand[1];scale=1.4f;angle=0;
            if("umbrella".equals(itemId)){sourceX=55;sourceY=38;scale=2;}
            else if("coffee_cup".equals(itemId)){sourceX=49;sourceY=41;}
            else if("sunflower".equals(itemId))sourceX=54;
        }
        Matrix matrix=new Matrix();
        matrix.postTranslate(-sourceX,-sourceY);
        matrix.postScale(scale,scale);
        matrix.postRotate(angle);
        matrix.postTranslate(targetX+16,targetY+16);
        return matrix;
    }

    private Bitmap load(String path) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        try (InputStream stream = getContext().getAssets().open(path)) {
            Bitmap bitmap = BitmapFactory.decodeStream(stream, null, options);
            int expected = path.startsWith("characters/latest/") ? SOURCE_SIZE : 64;
            if (bitmap == null || bitmap.getWidth() != expected
                    || bitmap.getHeight() != expected) {
                throw new IllegalStateException("Unexpected PNG dimensions: " + path);
            }
            bitmap.setDensity(Bitmap.DENSITY_NONE);
            return bitmap;
        } catch (IOException error) {
            throw new IllegalStateException("Cannot open character asset: " + path, error);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int side = Math.round(SOURCE_SIZE * getResources().getDisplayMetrics().density);
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

        int side = Math.min(width, height);
        int left = getPaddingLeft() + (width - side) / 2;
        int top = getPaddingTop() + (height - side) / 2;
        int save = canvas.save();
        canvas.clipRect(getPaddingLeft(), getPaddingTop(),
                getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
        canvas.translate(left,top);
        canvas.scale(side/(float)CANVAS_SIZE,side/(float)CANVAS_SIZE);
        destination.set(16,16,16+SOURCE_SIZE,16+SOURCE_SIZE);
        // Back is behind the flattened character; the other items attach in front.
        for (int i=0;i<layers.length;i++) {
            if(layers[i]==null)continue;
            if(i==1)canvas.drawBitmap(layers[i],null,destination,pixelPaint);
            else canvas.drawBitmap(layers[i],placements[i],pixelPaint);
        }
        canvas.restoreToCount(save);
    }
}
