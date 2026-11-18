package kr.example.haliltalk;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import kr.example.haliltalk.auth.AccountSession;
import kr.example.haliltalk.data.FriendsDatabase;
import kr.example.haliltalk.data.GoalBackup;
import kr.example.haliltalk.goals.GoalNotifications;

/** SAF grants access only to the document the user chooses; no storage permission is required. */
public final class TransferActivity extends FeatureActivity {
    private static final int EXPORT = 1501;
    private static final int IMPORT = 1502;
    private enum Mode { IDLE, EXPORT_PICKER, IMPORT_PICKER, BUSY, CONFIRM }
    private Job job;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.screen_transfer);
        job = (Job) getLastNonConfigurationInstance();
        if (job == null) {
            job = new Job(getApplicationContext());
            if (state != null) {
                String mode = state.getString("mode", "");
                if (Mode.EXPORT_PICKER.name().equals(mode) || Mode.IMPORT_PICKER.name().equals(mode)) {
                    job.mode = Mode.valueOf(mode);
                    job.account = state.getString("account");
                    job.generation = state.getLong("generation", -1L);
                    job.status = getString(R.string.transfer_selecting);
                } else {
                    // 프로세스 종료 후에는 확인되지 않은 파일을 자동으로 다시 가져오지 않는다.
                    job.status = getString(R.string.transfer_interrupted);
                }
            }
        }
        job.activity = this;
        findViewById(R.id.transfer_back).setOnClickListener(view -> finish());
        findViewById(R.id.transfer_export).setOnClickListener(view -> selectDocument(false));
        findViewById(R.id.transfer_import).setOnClickListener(view -> selectDocument(true));
        findViewById(R.id.transfer_confirm).setOnClickListener(view -> confirmImport());
        findViewById(R.id.transfer_cancel).setOnClickListener(view -> {
            job.abort(getString(R.string.transfer_cancelled));
            render();
        });
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (job.account != null && !job.current(job.sequence)) {
            job.abort(getString(R.string.transfer_account_changed));
        }
        render();
    }

    @Override public Object onRetainNonConfigurationInstance() { return job; }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("mode", job.mode.name());
        state.putString("account", job.account);
        state.putLong("generation", job.generation);
    }

    @Override protected void onDestroy() {
        job.activity = null;
        if (!isChangingConfigurations()) {
            job.disposed = true;
            job.sequence++;
            job.executor.shutdown();
        }
        super.onDestroy();
    }

    private void selectDocument(boolean importing) {
        if (job.mode != Mode.IDLE) return;
        job.account = AccountSession.databaseName(this);
        job.generation = AccountSession.generation();
        job.sequence++;
        job.mode = importing ? Mode.IMPORT_PICKER : Mode.EXPORT_PICKER;
        job.status = getString(R.string.transfer_selecting);
        Intent intent = new Intent(importing ? Intent.ACTION_OPEN_DOCUMENT : Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE).setType("application/json");
        if (!importing) intent.putExtra(Intent.EXTRA_TITLE, "appsai-goals.json");
        render();
        try {
            startActivityForResult(intent, importing ? IMPORT : EXPORT);
        } catch (ActivityNotFoundException unavailable) {
            job.abort(getString(R.string.transfer_no_picker));
            render();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != IMPORT && request != EXPORT) return;
        Mode expected = request == IMPORT ? Mode.IMPORT_PICKER : Mode.EXPORT_PICKER;
        if (job.mode != expected) return;
        if (!job.current(job.sequence)) {
            job.abort(getString(R.string.transfer_account_changed));
            render();
            return;
        }
        Uri uri = data == null ? null : data.getData();
        if (result != RESULT_OK || uri == null) {
            job.abort(getString(R.string.transfer_cancelled));
            render();
            return;
        }
        job.mode = Mode.BUSY;
        job.status = getString(R.string.transfer_busy);
        render();
        // 생성자는 UI 스레드에서 계정별 DB 이름을 고정한다. 이후 콜백에서 새 계정 DB를 열지 않는다.
        FriendsDatabase helper = new FriendsDatabase(this);
        Job active = job;
        long token = active.sequence;
        active.executor.execute(() -> {
            try (FriendsDatabase database = helper) {
                if (!active.current(token)) {
                    active.publish(token, active.context.getString(R.string.transfer_account_changed), null);
                    return;
                }
                if (request == IMPORT) {
                    GoalBackup.Document document;
                    try (InputStream input = active.context.getContentResolver().openInputStream(uri)) {
                        document = GoalBackup.read(input);
                    }
                    if (!active.current(token)) {
                        active.publish(token, active.context.getString(R.string.transfer_account_changed), null);
                        return;
                    }
                    GoalBackup.Preview preview = GoalBackup.preview(database.getReadableDatabase(), document);
                    active.publish(token, active.context.getString(R.string.transfer_preview,
                            preview.total, preview.additions, preview.unknownFriends, preview.alreadyPresent), preview);
                } else {
                    byte[] bytes = GoalBackup.exportData(database.getReadableDatabase());
                    if (!active.current(token)) {
                        active.publish(token, active.context.getString(R.string.transfer_account_changed), null);
                        return;
                    }
                    try (OutputStream output = active.context.getContentResolver().openOutputStream(uri, "wt")) {
                        if (output == null) throw new IOException("No document stream");
                        output.write(bytes);
                    }
                    active.publish(token, active.context.getString(R.string.transfer_exported), null);
                }
            } catch (GoalBackup.InvalidBackupException invalid) {
                active.publish(token, invalid.getMessage(), null);
            } catch (IOException | RuntimeException failure) {
                active.publish(token, active.context.getString(request == EXPORT
                        ? R.string.transfer_export_failed : R.string.transfer_import_failed), null);
            }
        });
    }

    private void confirmImport() {
        if (job.mode != Mode.CONFIRM || job.preview == null) return;
        if (!job.current(job.sequence)) {
            job.abort(getString(R.string.transfer_account_changed));
            render();
            return;
        }
        GoalBackup.Preview preview = job.preview;
        job.mode = Mode.BUSY;
        job.status = getString(R.string.transfer_busy);
        job.preview = null;
        render();
        FriendsDatabase helper = new FriendsDatabase(this);
        Job active = job;
        long token = active.sequence;
        active.executor.execute(() -> {
            try (FriendsDatabase database = helper) {
                int added = GoalBackup.importConfirmed(database.getWritableDatabase(), preview,
                        () -> active.current(token));
                if (active.current(token)) {
                    try {
                        GoalNotifications.reschedule(active.context);
                    } catch (RuntimeException unavailable) {
                        // 가져오기는 이미 커밋됐다. 알림 실패를 데이터 롤백으로 표시하지 않는다.
                        active.publish(token, active.context.getString(
                                R.string.transfer_imported_reminder_failed, added), null);
                        return;
                    }
                }
                active.publish(token, active.context.getString(R.string.transfer_imported, added), null);
            } catch (GoalBackup.InvalidBackupException invalid) {
                active.publish(token, invalid.getMessage(), null);
            } catch (IOException | RuntimeException failure) {
                active.publish(token, active.context.getString(R.string.transfer_import_failed), null);
            }
        });
    }

    private void render() {
        boolean idle = job.mode == Mode.IDLE;
        boolean confirm = job.mode == Mode.CONFIRM;
        findViewById(R.id.transfer_export).setEnabled(idle);
        findViewById(R.id.transfer_import).setEnabled(idle);
        findViewById(R.id.transfer_progress).setVisibility(job.mode == Mode.BUSY ? View.VISIBLE : View.GONE);
        findViewById(R.id.transfer_confirm).setVisibility(confirm ? View.VISIBLE : View.GONE);
        findViewById(R.id.transfer_confirm).setEnabled(confirm && job.preview != null && job.preview.additions > 0);
        findViewById(R.id.transfer_cancel).setVisibility(confirm ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.transfer_status)).setText(job.status);
    }

    /** Retained only across configuration changes; it never keeps the destroyed activity. */
    private static final class Job {
        final Context context;
        final Handler main = new Handler(Looper.getMainLooper());
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        volatile boolean disposed;
        volatile long sequence;
        volatile String account;
        volatile long generation;
        Mode mode = Mode.IDLE;
        String status;
        GoalBackup.Preview preview;
        TransferActivity activity;

        Job(Context context) {
            this.context = context;
            status = context.getString(R.string.transfer_ready);
        }

        boolean current(long token) {
            return !disposed && sequence == token && account != null
                    && account.equals(AccountSession.databaseName(context))
                    && generation == AccountSession.generation();
        }

        void abort(String message) {
            sequence++;
            mode = Mode.IDLE;
            preview = null;
            status = message;
            account = null;
        }

        void publish(long token, String message, GoalBackup.Preview result) {
            main.post(() -> {
                if (disposed || token != sequence) return;
                if (!current(token)) abort(context.getString(R.string.transfer_account_changed));
                else {
                    status = message;
                    preview = result;
                    mode = result == null ? Mode.IDLE : Mode.CONFIRM;
                }
                if (activity != null) activity.render();
            });
        }
    }
}
