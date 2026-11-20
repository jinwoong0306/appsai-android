package kr.example.haliltalk.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import kr.example.haliltalk.shop.CoinLedger;

/** Account/device-local daily reward, called only after a separate user confirmation. */
public final class QuestRewardStore {
    public static final int REWARD=20;
    public enum Status { AVAILABLE, ALREADY_CLAIMED, NO_ATTEMPT, BALANCE_LIMIT }
    private final SQLiteDatabase db;

    public QuestRewardStore(SQLiteDatabase db) { this.db=db; }

    public static void createTables(SQLiteDatabase db) {
        // No friend FK: deleting or replacing a friend must never erase a paid day.
        db.execSQL("CREATE TABLE IF NOT EXISTS quest_reward_receipts("
                +"day TEXT PRIMARY KEY NOT NULL CHECK(length(day)=10),"
                +"coins INTEGER NOT NULL CHECK(coins=20))");
    }

    /** The UI supplies today's local date. Reads never initialize or credit a wallet. */
    public Status status(int friend,String day) {
        JourneyStore.Entry entry=new JourneyStore(db).entry(friend,day);
        try(Cursor receipt=db.rawQuery("SELECT 1 FROM quest_reward_receipts WHERE day=?",new String[]{day})) {
            if(receipt.moveToFirst())return Status.ALREADY_CLAIMED;
        }
        if(entry==null||!("done".equals(entry.outcome)||"partial".equals(entry.outcome)))
            return Status.NO_ATTEMPT;
        return balance()>Integer.MAX_VALUE-REWARD?Status.BALANCE_LIMIT:Status.AVAILABLE;
    }

    /** One serialized transaction; false means an ineligible or already paid request. */
    public boolean claim(int friend,String day) {
        db.beginTransaction();
        try {
            if(status(friend,day)!=Status.AVAILABLE)return false;
            long current=balance();
            ContentValues receipt=new ContentValues();
            receipt.put("day",day);receipt.put("coins",REWARD);
            if(db.insertOrThrow("quest_reward_receipts",null,receipt)==-1)
                throw new SQLiteException("Reward receipt was not inserted");
            ContentValues wallet=new ContentValues();
            wallet.put("balance",current+REWARD);
            if(db.update("demo_wallet",wallet,"id=1 AND balance=?",new String[]{""+current})!=1)
                throw new SQLiteException("Reward wallet was not updated");
            CoinLedger.reward(db,day,REWARD,(int)(current+REWARD));
            db.setTransactionSuccessful();
            return true;
        } finally { db.endTransaction(); }
    }

    private long balance() {
        try(Cursor wallet=db.rawQuery("SELECT balance FROM demo_wallet WHERE id=1",null)) {
            if(!wallet.moveToFirst())throw new SQLiteException("Coin wallet is missing");
            return wallet.getLong(0);
        }
    }
}
