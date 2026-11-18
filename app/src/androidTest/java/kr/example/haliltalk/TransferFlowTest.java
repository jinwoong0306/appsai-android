package kr.example.haliltalk;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.net.Uri;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import kr.example.haliltalk.data.FriendsDatabase;
import kr.example.haliltalk.data.GoalBackup;
import kr.example.haliltalk.data.GoalStore;

/** The document picker is intercepted; the chosen URI, stream, and account DB are real. */
public final class TransferFlowTest extends GuestActivityTestCase {
    private TransferActivity activity;

    @Override protected void tearDown() throws Exception {
        try {
            if (activity != null) {
                getInstrumentation().runOnMainSync(activity::finish);
                getInstrumentation().waitForIdleSync();
            }
        } finally { super.tearDown(); }
    }

    private void launch() {
        Context context = getInstrumentation().getTargetContext();
        activity = (TransferActivity) getInstrumentation().startActivitySync(
                new Intent(context, TransferActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getInstrumentation().waitForIdleSync();
    }

    private IntentFilter picker() throws IntentFilter.MalformedMimeTypeException {
        IntentFilter filter = new IntentFilter(Intent.ACTION_OPEN_DOCUMENT);
        filter.addCategory(Intent.CATEGORY_OPENABLE);
        filter.addDataType("application/json");
        return filter;
    }

    private void onStatus(BooleanSupplier ready, Runnable action) throws Exception {
        CountDownLatch changed = new CountDownLatch(1);
        TextView status = activity.findViewById(R.id.transfer_status);
        TextWatcher watcher = new TextWatcher() {
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {}
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                if (ready.getAsBoolean()) changed.countDown();
            }
            public void afterTextChanged(Editable value) {}
        };
        getInstrumentation().runOnMainSync(() -> {
            status.addTextChangedListener(watcher);
            action.run();
        });
        try { assertTrue("Transfer result", changed.await(10, TimeUnit.SECONDS)); }
        finally { getInstrumentation().runOnMainSync(() -> status.removeTextChangedListener(watcher)); }
        getInstrumentation().waitForIdleSync();
    }

    private void capture(String name) throws Exception {
        CountDownLatch frame = new CountDownLatch(1);
        getInstrumentation().runOnMainSync(() -> {
            View decor = activity.getWindow().getDecorView();
            decor.getViewTreeObserver().registerFrameCommitCallback(() -> decor.post(
                    () -> android.view.Choreographer.getInstance().postFrameCallback(t -> frame.countDown())));
            decor.invalidate();
        });
        assertTrue("Committed transfer frame", frame.await(5, TimeUnit.SECONDS));
        Bitmap image = getInstrumentation().getUiAutomation().takeScreenshot();
        assertNotNull(image);
        File directory = new File(getInstrumentation().getTargetContext().getExternalFilesDir(null),
                "u5-transfer-qa");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, out));
        } finally { image.recycle(); }
    }

    private void captureEnd(String name) throws Exception {
        ScrollView scroll = (ScrollView) activity.findViewById(R.id.transfer_status).getParent().getParent();
        getInstrumentation().runOnMainSync(() -> {
            scroll.setSmoothScrollingEnabled(false);
            scroll.fullScroll(View.FOCUS_DOWN);
        });
        capture(name);
        getInstrumentation().runOnMainSync(() -> assertTrue(
                "Confirmation remains reachable at scroll end",
                activity.findViewById(R.id.transfer_confirm)
                        .getGlobalVisibleRect(new android.graphics.Rect())));
    }

    public void testCancelledImportKeepsCurrentAccountUnchanged() throws Exception {
        launch();
        Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(picker(),
                new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true);
        try {
            getInstrumentation().runOnMainSync(() ->
                    activity.findViewById(R.id.transfer_import).performClick());
            getInstrumentation().waitForIdleSync();
            assertEquals(1, monitor.getHits());
            assertTrue(activity.findViewById(R.id.transfer_export).isEnabled());
            assertTrue(((TextView) activity.findViewById(R.id.transfer_status))
                    .getText().toString().contains("취소"));
            capture("cancelled");
        } finally { getInstrumentation().removeMonitor(monitor); }
    }

    public void testPreviewRequiresConfirmationAndCorruptFilePreservesGoals() throws Exception {
        Context context = getInstrumentation().getTargetContext();
        String pkg = "qa.transfer.n" + UUID.randomUUID().toString().replace("-", "");
        File document = new File(context.getCacheDir(), "qa-u5-transfer-" + UUID.randomUUID() + ".json");
        try (FriendsDatabase helper = new FriendsDatabase(context)) {
            SQLiteDatabase db = helper.getWritableDatabase();
            ContentValues values = new ContentValues();
            values.put("package", pkg);
            values.put("name", "전송 검증 친구");
            values.put("personality", "차분한 친구");
            int friend = (int) db.insertOrThrow("friends", null, values);
            try {
                byte[] bytes;
                try (SQLiteDatabase fixture = SQLiteDatabase.create(null)) {
                    helper.onCreate(fixture);
                    int fixtureFriend = (int) fixture.insertOrThrow("friends", null, values);
                    new GoalStore(fixture).save(fixtureFriend, 0, "확인 후 추가할 목표", null);
                    bytes = GoalBackup.exportData(fixture);
                }
                try (FileOutputStream out = new FileOutputStream(document)) { out.write(bytes); }
                launch();
                Instrumentation.ActivityMonitor monitor = getInstrumentation().addMonitor(picker(),
                        new Instrumentation.ActivityResult(Activity.RESULT_OK,
                                new Intent().setData(Uri.fromFile(document))), true);
                try {
                    onStatus(() -> activity.findViewById(R.id.transfer_confirm).getVisibility() == View.VISIBLE,
                            () -> activity.findViewById(R.id.transfer_import).performClick());
                    assertEquals(1, monitor.getHits());
                    assertTrue(new GoalStore(db).list(friend).isEmpty());
                    assertTrue(((TextView) activity.findViewById(R.id.transfer_status))
                            .getText().toString().contains("추가할 목표 1개"));
                    capture("preview");
                    captureEnd("preview-end");
                    onStatus(() -> new GoalStore(db).list(friend).size() == 1,
                            () -> activity.findViewById(R.id.transfer_confirm).performClick());
                    capture("imported");
                } finally { getInstrumentation().removeMonitor(monitor); }
                assertEquals("확인 후 추가할 목표", new GoalStore(db).list(friend).get(0).title);
                try (FileOutputStream out = new FileOutputStream(document)) {
                    out.write("{invalid".getBytes(StandardCharsets.UTF_8));
                }
                monitor = getInstrumentation().addMonitor(picker(),
                        new Instrumentation.ActivityResult(Activity.RESULT_OK,
                                new Intent().setData(Uri.fromFile(document))), true);
                try {
                    onStatus(() -> activity.findViewById(R.id.transfer_export).isEnabled(),
                            () -> activity.findViewById(R.id.transfer_import).performClick());
                    assertEquals(1, monitor.getHits());
                    assertEquals(1, new GoalStore(db).list(friend).size());
                    capture("invalid");
                } finally { getInstrumentation().removeMonitor(monitor); }
            } finally { db.delete("friends", "id=?", new String[]{Integer.toString(friend)}); }
        } finally { if (document.exists()) assertTrue(document.delete()); }
    }
}
