package com.subnook.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY active DESC, nextBillingDay ASC")
    fun observeAll(): Flow<List<Subscription>>

    @Query("SELECT * FROM subscriptions")
    suspend fun getAll(): List<Subscription>

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    suspend fun get(id: Long): Subscription?

    @Query("SELECT * FROM subscriptions WHERE id = :id")
    fun observe(id: Long): Flow<Subscription?>

    @Upsert
    suspend fun upsert(sub: Subscription): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subs: List<Subscription>)

    @Delete
    suspend fun delete(sub: Subscription)

    @Query("DELETE FROM subscriptions")
    suspend fun deleteAll()

    @Insert
    suspend fun insertPayment(p: PaymentRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(ps: List<PaymentRecord>)

    @Delete
    suspend fun deletePayment(p: PaymentRecord)

    @Query("SELECT * FROM payments WHERE subscriptionId = :subId ORDER BY billingDay DESC")
    fun observePayments(subId: Long): Flow<List<PaymentRecord>>

    @Query("SELECT * FROM payments WHERE billingDay BETWEEN :from AND :to")
    fun observePaymentsBetween(from: Long, to: Long): Flow<List<PaymentRecord>>

    @Query("SELECT * FROM payments")
    suspend fun getAllPayments(): List<PaymentRecord>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getPayment(id: Long): PaymentRecord?

    /** 某条记录之后（按记账时间）同一订阅 / 分期又记了几笔 */
    @Query("SELECT COUNT(*) FROM payments WHERE subscriptionId = :subId AND (recordedAt > :at OR (recordedAt = :at AND id > :id))")
    suspend fun countPaymentsAfter(subId: Long, at: Long, id: Long): Int

    /** 撤销提前还款：删掉这条记录，同时把分期恢复成还款前的样子 */
    @Transaction
    suspend fun undoPrepay(p: PaymentRecord, restored: Subscription) {
        deletePayment(p)
        upsert(restored)
    }

    @Query("SELECT * FROM payments ORDER BY billingDay DESC")
    fun observeAllPayments(): Flow<List<PaymentRecord>>

    @Query("UPDATE subscriptions SET sortIndex = :index WHERE id = :id")
    suspend fun setSortIndex(id: Long, index: Int)

    /** 按 [ids] 的顺序写入手动排序位置 */
    @Transaction
    suspend fun setManualOrder(ids: List<Long>) {
        ids.forEachIndexed { i, id -> setSortIndex(id, i) }
    }

    @Query("SELECT COALESCE(MAX(sortIndex), -1) FROM subscriptions")
    suspend fun maxSortIndex(): Int

    @Transaction
    suspend fun replaceAll(subs: List<Subscription>, payments: List<PaymentRecord>, topUps: List<TopUp> = emptyList()) {
        deleteAll() // payments / topups cascade
        insertAll(subs)
        insertPayments(payments)
        insertTopUps(topUps)
    }

    // ---- 余额付款（移植自 PR #3）----

    @Query("UPDATE subscriptions SET balance = :balance WHERE id = :id")
    suspend fun setBalance(id: Long, balance: Double)

    /** 删除一条付款记录；如果当时从余额扣过款，把这部分加回余额 */
    @Transaction
    suspend fun deletePaymentWithRefund(p: PaymentRecord) {
        deletePayment(p)
        if (p.balanceUsed <= 0.0) return
        val sub = get(p.subscriptionId) ?: return
        setBalance(sub.id, roundMoney(sub.balance + p.balanceUsed))
    }

    @Insert
    suspend fun insertTopUp(t: TopUp): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopUps(ts: List<TopUp>)

    @Delete
    suspend fun deleteTopUp(t: TopUp)

    @Query("SELECT * FROM topups WHERE subscriptionId = :subId ORDER BY day DESC, id DESC")
    fun observeTopUps(subId: Long): Flow<List<TopUp>>

    @Query("SELECT * FROM topups")
    suspend fun getAllTopUps(): List<TopUp>

    /** 充值：记一笔充值记录，余额加上去 */
    @Transaction
    suspend fun topUp(t: TopUp) {
        val sub = get(t.subscriptionId) ?: return
        insertTopUp(t)
        setBalance(sub.id, roundMoney(sub.balance + t.amount))
    }

    /** 删除充值记录：余额减回去（最多减到 0——中间扣掉的钱已经花出去了） */
    @Transaction
    suspend fun deleteTopUpWithRevert(t: TopUp) {
        deleteTopUp(t)
        val sub = get(t.subscriptionId) ?: return
        setBalance(sub.id, roundMoney((sub.balance - t.amount).coerceAtLeast(0.0)))
    }
}

/** 余额按分保存，避免 0.1 + 0.2 这类浮点尾巴 */
fun roundMoney(v: Double): Double = java.math.BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP).toDouble()

@Database(entities = [Subscription::class, PaymentRecord::class, TopUp::class], version = 8, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): SubscriptionDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "subnook.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                .build()

        /** v8：实际扣款渠道（如 信用卡7727、借记卡2990、微信零钱，跨支付平台复用） */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN payChannel TEXT NOT NULL DEFAULT ''")
            }
        }

        /** v7：「其他」可以填名称；去掉「分期还款」分类（改成「其他」，否则 Room 读到不认识的枚举会崩） */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN payMethodNote TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN categoryNote TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE subscriptions SET category = 'OTHER' WHERE category = 'INSTALLMENT'")
            }
        }

        /** v6：余额付款（移植自 PR #3）+ 充值记录。建表语句要和 Room 生成的一致 */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN balance REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE payments ADD COLUMN balanceUsed REAL NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `topups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `subscriptionId` INTEGER NOT NULL, " +
                        "`day` INTEGER NOT NULL, `amount` REAL NOT NULL, `currency` TEXT NOT NULL, `recordedAt` INTEGER NOT NULL, " +
                        "FOREIGN KEY(`subscriptionId`) REFERENCES `subscriptions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_topups_subscriptionId` ON `topups` (`subscriptionId`)")
            }
        }

        /** v5：提前还款记录保存还款前的分期快照，删除记录时可以恢复 */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE payments ADD COLUMN undo TEXT")
            }
        }

        /** v4：提前还款 */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN instTermOffset INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN instPaidBefore REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN instPrincipalBefore REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN instPaidOffDay INTEGER")
                db.execSQL("ALTER TABLE payments ADD COLUMN term INTEGER")
                db.execSQL("ALTER TABLE payments ADD COLUMN prepay INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v3：分期付款。默认值必须和 Subscription 上的 @ColumnInfo(defaultValue) 一致，否则 Room 校验表结构会失败 */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN kind TEXT NOT NULL DEFAULT 'SUBSCRIPTION'")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN instPrincipal REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN instTerms INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN instRate REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN instMethod TEXT NOT NULL DEFAULT 'FEE'")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN instFirstAmount REAL")
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN instStartDay INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** v2：新增手动排序位置 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE subscriptions ADD COLUMN sortIndex INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
